package fr.alex.chambre.ui.salon

import fr.alex.chambre.ha.EntityState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSummaryTest {
    private fun e(id: String, state: String, vararg attrs: Pair<String, Int>) =
        EntityState(id, state, JsonObject(attrs.associate { it.first to JsonPrimitive(it.second) }))

    @Test
    fun light_and_cover() {
        assertEquals("Lumières à 65 % · volet ouvert", roomSummary(e("l", "on", "brightness" to 166), e("c", "open", "current_position" to 100), plural = true))
        assertEquals("Lumière éteinte · volet fermé", roomSummary(e("l", "off"), e("c", "closed", "current_position" to 0), plural = false))
        assertEquals("Lumières éteintes · volet à 40 %", roomSummary(e("l", "off"), e("c", "open", "current_position" to 40), plural = true))
    }

    @Test
    fun unknown_entities() {
        assertEquals("–", roomSummary(null, null, plural = true))
    }

    @Test
    fun kitchen() {
        val idle = e("t", "idle")
        assertEquals("Volet ouvert · minuteurs prêts", kitchenSummary(e("c", "open", "current_position" to 100), listOf(idle, idle, idle)))
        assertEquals("Volet ouvert · 2 minuteurs en cours", kitchenSummary(e("c", "open", "current_position" to 100), listOf(e("t", "active"), e("t", "paused"), idle)))
        assertEquals("Volet fermé · 1 minuteur en cours", kitchenSummary(e("c", "closed", "current_position" to 0), listOf(e("t", "active"))))
    }
}
