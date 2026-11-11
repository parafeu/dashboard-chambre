package fr.alex.chambre.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenPolicyTest {
    @Test
    fun awake_keeps_the_screen_on_at_normal_brightness() {
        assertEquals(ScreenFlags(keepOn = true, dark = false), screenFlags(Presence.AWAKE, screenOff = false))
        assertEquals(ScreenFlags(keepOn = true, dark = false), screenFlags(Presence.AWAKE, screenOff = true))
    }

    @Test
    fun asleep_without_real_off_stays_on_but_dark() { // AMOLED tablets: black is already off
        assertEquals(ScreenFlags(keepOn = true, dark = true), screenFlags(Presence.ASLEEP, screenOff = false))
    }

    @Test
    fun asleep_with_real_off_lets_android_turn_the_screen_off() { // LCD tablets
        assertEquals(ScreenFlags(keepOn = false, dark = true), screenFlags(Presence.ASLEEP, screenOff = true))
    }
}
