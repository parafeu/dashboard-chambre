package fr.alex.chambre.timers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RingStateTest {
    private val ringing = listOf(SharedTimer("a", "Pâtes", Instant.parse("2026-11-11T12:00:00Z"), 600, ""))

    @Test
    fun ringing_disabled_shows_nothing() {
        assertTrue(ringingShown(ringing, enabled = false, tablet = true).isEmpty())
    }

    @Test
    fun phone_shows_nothing() {
        assertTrue(ringingShown(ringing, enabled = true, tablet = false).isEmpty())
    }

    @Test
    fun unknown_setting_shows_nothing() { // settings not loaded yet: no burst on a tablet set to stay silent
        assertTrue(ringingShown(ringing, enabled = null, tablet = true).isEmpty())
    }

    @Test
    fun enabled_tablet_shows_ringing() {
        assertEquals(ringing, ringingShown(ringing, enabled = true, tablet = true))
    }
}
