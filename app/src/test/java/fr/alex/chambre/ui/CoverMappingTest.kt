package fr.alex.chambre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverMappingTest {
    @Test
    fun open_shutter_has_empty_slider() {
        assertEquals(0f, closedFraction(100)!!, 0f)
        assertEquals(0.57f, closedFraction(43)!!, 1e-6f)
    }

    @Test
    fun unknown_position_has_no_value() { // RF4
        assertNull(closedFraction(null))
    }

    @Test
    fun fraction_maps_back_to_position() {
        assertEquals(43, positionFromClosedFraction(0.57f))
        assertEquals(100, positionFromClosedFraction(0f))
        assertEquals(0, positionFromClosedFraction(1f))
    }
}
