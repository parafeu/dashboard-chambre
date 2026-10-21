package fr.alex.chambre.widget

import fr.alex.chambre.ha.EntityState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun light(state: String, brightness: Int? = null) = EntityState(
    "light.x", state, buildJsonObject { brightness?.let { put("brightness", JsonPrimitive(it)) } },
)

private fun cover(state: String, position: Int? = null) = EntityState(
    "cover.x", state, buildJsonObject { position?.let { put("current_position", JsonPrimitive(it)) } },
)

private fun sensor(value: String) = EntityState("sensor.x", value, buildJsonObject { })

class WidgetModelTest {
    @Test
    fun `light text`() {
        assertEquals("40 %", lightText(light("on", 102)))
        assertEquals("Éteinte", lightText(light("off")))
        assertEquals("Allumée", lightText(light("on")))
        assertEquals("–", lightText(null))
        assertEquals("–", lightText(light("unavailable")))
        assertTrue(lightIsOn(light("on", 10)))
        assertFalse(lightIsOn(light("off")))
    }

    @Test
    fun `minus on an off light turns it on at the minimum`() {
        assertEquals(10, stepBrightness(light("off"), -10))
        assertEquals(10, stepBrightness(light("off"), +10))
        assertEquals(50, stepBrightness(light("on", 102), +10))
        assertEquals(30, stepBrightness(light("on", 102), -10))
        assertEquals(10, stepBrightness(light("on", 38), -10))
        assertEquals(100, stepBrightness(light("on", 242), +10))
    }

    @Test
    fun `cover text`() {
        assertEquals("Ouvert", coverText(cover("open", 100)))
        assertEquals("Fermé", coverText(cover("closed", 0)))
        assertEquals("Ouvert à 35 %", coverText(cover("open", 35)))
        assertEquals("Ouverture…", coverText(cover("opening", 35)))
        assertEquals("Fermeture…", coverText(cover("closing", 35)))
        assertEquals("–", coverText(null))
    }

    @Test
    fun `cover without position uses its state`() {
        assertEquals("Ouvert", coverText(cover("open")))
        assertEquals(CoverTap.CLOSE, coverTap(cover("open")))
        assertTrue(coverIsOpen(cover("open")))
        assertEquals("Fermé", coverText(cover("closed")))
        assertEquals(CoverTap.OPEN, coverTap(cover("closed")))
        assertFalse(coverIsOpen(cover("closed")))
    }

    @Test
    fun `cover tap`() {
        assertEquals(CoverTap.STOP, coverTap(cover("opening", 20)))
        assertEquals(CoverTap.STOP, coverTap(cover("closing", 20)))
        assertEquals(CoverTap.CLOSE, coverTap(cover("open", 50)))
        assertEquals(CoverTap.OPEN, coverTap(cover("closed", 0)))
        assertEquals(CoverTap.OPEN, coverTap(null))
    }

    @Test
    fun `climate text`() {
        assertEquals("21,4 °C · 52 %", climateText(sensor("21.43"), sensor("52.0")))
        assertEquals("21,4 °C", climateText(sensor("21.4"), null))
        assertEquals("", climateText(null, sensor("unavailable")))
    }
}

class CoverButtonsTest {
    @Test
    fun `every shutter widget has open, stop and close`() {
        WidgetRoom.entries.forEach { room ->
            assertEquals(listOf(CoverButton.OPEN, CoverButton.STOP, CoverButton.CLOSE), coverButtons(room))
        }
    }
}

class FollowUpTest {
    @Test
    fun `every action reads the state again once the device has reported`() {
        listOf(WidgetAction.ToggleLight, WidgetAction.StepLight(-10), WidgetAction.CoverTapAction, WidgetAction.CoverOpen, WidgetAction.CoverStop, WidgetAction.CoverClose).forEach { a ->
            assert(followUpDelaysSec(a).isNotEmpty()) { "no follow-up for $a" }
        }
        assertEquals(listOf(2L, 6L), followUpDelaysSec(WidgetAction.ToggleLight))
        assertEquals(listOf(2L, 6L), followUpDelaysSec(WidgetAction.StepLight(10)))
        assertEquals(listOf(3L, 20L), followUpDelaysSec(WidgetAction.CoverClose))
    }
}

class PredictTest {
    private val room = WidgetRoom.CHAMBRE
    private fun states(vararg s: EntityState) = s.associateBy { it.entityId }
    private fun l(state: String, b: Int? = null) = EntityState(room.light!!, state, buildJsonObject { b?.let { put("brightness", JsonPrimitive(it)) } })
    private fun c(state: String, p: Int?) = EntityState(room.cover, state, buildJsonObject { p?.let { put("current_position", JsonPrimitive(it)) } })

    @Test
    fun `toggling shows the light's new state at once`() {
        assertEquals("Éteinte", lightText(predict(states(l("on", 102)), room, WidgetAction.ToggleLight)[room.light]))
        assertTrue(lightIsOn(predict(states(l("off")), room, WidgetAction.ToggleLight)[room.light]))
    }

    @Test
    fun `a brightness step shows the new level`() {
        assertEquals("50 %", lightText(predict(states(l("on", 102)), room, WidgetAction.StepLight(10))[room.light]))
        assertEquals("10 %", lightText(predict(states(l("off")), room, WidgetAction.StepLight(-10))[room.light]))
    }

    @Test
    fun `a shutter shows it is moving`() {
        assertEquals("Fermeture…", coverText(predict(states(c("open", 100)), room, WidgetAction.CoverClose)[room.cover]))
        assertEquals("Ouverture…", coverText(predict(states(c("closed", 0)), room, WidgetAction.CoverTapAction)[room.cover]))
        assertEquals("Ouvert à 40 %", coverText(predict(states(c("closing", 40)), room, WidgetAction.CoverStop)[room.cover]))
    }

    @Test
    fun `nothing known, nothing predicted`() {
        assertEquals(emptyMap<String, EntityState>(), predict(emptyMap(), room, WidgetAction.ToggleLight))
    }
}

class SettleTest {
    private fun l(state: String) = EntityState("light.x", state, buildJsonObject { })
    private val before = mapOf("light.x" to l("on"))
    private val predicted = mapOf("light.x" to l("off"))

    @Test
    fun `an early check that still sees the old state keeps the prediction`() {
        assertEquals(predicted, settle(before, predicted, read = mapOf("light.x" to l("on")), last = false))
    }

    @Test
    fun `once HA has moved, or at the last check, HA wins`() {
        assertEquals(mapOf("light.x" to l("off")), settle(before, predicted, read = mapOf("light.x" to l("off")), last = false))
        assertEquals(mapOf("light.x" to l("on")), settle(before, predicted, read = mapOf("light.x" to l("on")), last = true))
    }
}
