package fr.alex.chambre.ui.timers

import org.junit.Assert.assertEquals
import org.junit.Test

class NewTimerStateTest {
    @Test
    fun stepper_starts_at_5_and_clamps() {
        assertEquals(5, NewTimerState().minutes)
        var s = NewTimerState()
        repeat(10) { s = s.minus() }
        assertEquals(1, s.minutes)
        assertEquals(180, NewTimerState(180).plus().minutes)
        assertEquals(6, NewTimerState().plus().minutes)
        assertEquals(30, NewTimerState().preset(30).minutes)
        assertEquals(listOf(1, 3, 5, 10, 15, 30), NEW_TIMER_PRESETS)
    }
}
