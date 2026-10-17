package fr.alex.chambre.ui.salon

import fr.alex.chambre.ha.EntityState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TimerFormatTest {
    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private fun timer(state: String, vararg a: Pair<String, String>) =
        EntityState("timer.minuteur_1", state, JsonObject(a.associate { it.first to JsonPrimitive(it.second) }))

    @Test
    fun active_counts_down_to_finishes_at() {
        assertEquals("4:05", timerRemaining(timer("active", "finishes_at" to "2026-10-17T10:04:05+00:00"), now))
        assertEquals("0:00", timerRemaining(timer("active", "finishes_at" to "2026-10-17T09:59:00+00:00"), now))
    }

    @Test
    fun paused_shows_remaining() {
        assertEquals("12:30 · pause", timerRemaining(timer("paused", "remaining" to "0:12:30"), now))
    }

    @Test
    fun idle_or_unknown_is_null() {
        assertNull(timerRemaining(timer("idle"), now))
        assertNull(timerRemaining(timer("active"), now))
    }
}
