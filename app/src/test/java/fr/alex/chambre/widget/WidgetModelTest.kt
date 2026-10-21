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
