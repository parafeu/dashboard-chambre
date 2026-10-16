package fr.alex.chambre.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalSliderTest {
    @Test
    fun bottom_filled_slider_maps_top_to_one() {
        assertEquals(1f, sliderFraction(0f, 400f, fillFromTop = false), 0f)
        assertEquals(0f, sliderFraction(400f, 400f, fillFromTop = false), 0f)
        assertEquals(0.75f, sliderFraction(100f, 400f, fillFromTop = false), 1e-6f)
    }

    @Test
    fun top_filled_slider_maps_top_to_zero() {
        assertEquals(0f, sliderFraction(0f, 400f, fillFromTop = true), 0f)
        assertEquals(0.25f, sliderFraction(100f, 400f, fillFromTop = true), 1e-6f)
    }

    @Test
    fun clamps_outside_track() {
        assertEquals(1f, sliderFraction(-50f, 400f, fillFromTop = false), 0f)
        assertEquals(0f, sliderFraction(900f, 400f, fillFromTop = false), 0f)
    }
}

class HueTest {
    private val c = androidx.compose.ui.geometry.Offset(100f, 100f)

    @Test
    fun hue_follows_angle_clockwise_from_right() {
        assertEquals(0f, fr.alex.chambre.ui.hueAt(androidx.compose.ui.geometry.Offset(200f, 100f), c), 1e-3f)
        assertEquals(90f, fr.alex.chambre.ui.hueAt(androidx.compose.ui.geometry.Offset(100f, 200f), c), 1e-3f)
        assertEquals(180f, fr.alex.chambre.ui.hueAt(androidx.compose.ui.geometry.Offset(0f, 100f), c), 1e-3f)
        assertEquals(270f, fr.alex.chambre.ui.hueAt(androidx.compose.ui.geometry.Offset(100f, 0f), c), 1e-3f)
    }
}
