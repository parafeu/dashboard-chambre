package fr.alex.chambre.boot

import org.junit.Assert.assertEquals
import org.junit.Test

class BootStartTest {
    @Test
    fun option_off_does_nothing() {
        assertEquals(BootStart.OFF, bootStart(enabled = false, sdk = 30, canDrawOverlays = true))
    }

    @Test
    fun android_9_starts_without_any_permission() {
        assertEquals(BootStart.START, bootStart(enabled = true, sdk = 28, canDrawOverlays = false))
    }

    @Test
    fun android_10_and_later_need_the_overlay_permission() {
        assertEquals(BootStart.NEEDS_OVERLAY, bootStart(enabled = true, sdk = 29, canDrawOverlays = false))
        assertEquals(BootStart.NEEDS_OVERLAY, bootStart(enabled = true, sdk = 30, canDrawOverlays = false))
        assertEquals(BootStart.START, bootStart(enabled = true, sdk = 30, canDrawOverlays = true))
    }
}
