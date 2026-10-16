package fr.alex.chambre.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepStepsTest {
    @Test
    fun labels() {
        assertEquals("45 s", formatDelay(45))
        assertEquals("1 min", formatDelay(60))
        assertEquals("1 min 30", formatDelay(90))
        assertEquals("10 min", formatDelay(600))
    }

    @Test
    fun nearest_step_index() {
        assertEquals(4, nearestStep(60))
        assertEquals(0, nearestStep(5))
        assertEquals(9, nearestStep(5000))
        assertEquals(5, nearestStep(80))
    }
}
