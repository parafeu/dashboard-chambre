package fr.alex.chambre.presence

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceControllerTest {

    private fun TestScope.controller(delayMs: () -> Long = { 60_000 }) =
        PresenceController(backgroundScope, delayMs, now = { testScheduler.currentTime }).also {
            it.start()
            runCurrent()
        }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun starts_awake() = runTest {
        assertEquals(Presence.AWAKE, controller().state.value)
    }

    @Test
    fun sleeps_after_delay_without_activity() = runTest {
        val c = controller()
        advance(59_000)
        assertEquals(Presence.AWAKE, c.state.value)
        advance(1_000)
        assertEquals(Presence.ASLEEP, c.state.value)
    }

    @Test
    fun motion_resets_timer() = runTest {
        val c = controller()
        advance(50_000)
        c.onMotion()
        advance(50_000)
        assertEquals(Presence.AWAKE, c.state.value)
        advance(10_000)
        assertEquals(Presence.ASLEEP, c.state.value)
    }

    @Test
    fun motion_wakes_when_asleep() = runTest {
        val c = controller()
        advance(60_000)
        assertEquals(Presence.ASLEEP, c.state.value)
        c.onMotion()
        assertEquals(Presence.AWAKE, c.state.value)
    }

    @Test
    fun touch_wakes_when_asleep() = runTest {
        val c = controller()
        advance(60_000)
        c.onTouch()
        assertEquals(Presence.AWAKE, c.state.value)
    }

    @Test
    fun delay_change_applies_live() = runTest {
        var delay = 60_000L
        val c = controller { delay }
        delay = 10_000
        advance(10_000)
        assertEquals(Presence.ASLEEP, c.state.value)
    }
}
