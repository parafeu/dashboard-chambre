package fr.alex.chambre.presence

import fr.alex.chambre.presence.CameraWatchdog.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraWatchdogTest {
    private var now = 0L
    private val w = CameraWatchdog { now }

    /** Advances time second by second until the watchdog acts; returns the action and how long it waited. */
    private fun waitForAction(): Pair<Action, Long> {
        val from = now
        while (true) {
            val a = w.check()
            if (a != Action.NONE) return a to (now - from)
            now += 1_000
        }
    }

    @Test
    fun nothing_happens_while_frames_arrive() {
        w.onBound()
        repeat(20) {
            now += 5_000
            w.onFrame()
            assertEquals(Action.NONE, w.check())
        }
    }

    @Test
    fun a_silent_camerax_falls_back_to_camera1_after_20_s() {
        // e.g. a slow legacy HAL: CameraX gives up after its 5 s open timeout and stays PENDING_OPEN.
        w.onBound()
        assertEquals(Action.FALLBACK_TO_CAMERA1 to 20_000L, waitForAction())
        assertEquals(CameraWatchdog.Backend.CAMERA1, w.backend)
    }

    @Test
    fun camerax_whose_frames_stopped_also_falls_back() {
        w.onBound()
        now = 3_000
        w.onFrame()
        assertEquals(Action.FALLBACK_TO_CAMERA1 to 20_000L, waitForAction())
    }

    @Test
    fun a_silent_camera1_is_reopened_with_back_off_up_to_five_minutes() {
        w.onBound()
        waitForAction() // fallback
        val waits = (1..6).map {
            w.onBound()
            val (a, waited) = waitForAction()
            assertEquals(Action.RESTART, a)
            waited
        }
        assertEquals(listOf(20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L), waits)
        assertEquals(CameraWatchdog.Backend.CAMERA1, w.backend)
    }

    @Test
    fun a_frame_resets_the_back_off() {
        w.onBound()
        waitForAction() // fallback
        repeat(3) {
            w.onBound()
            waitForAction()
        }
        w.onBound()
        w.onFrame()
        assertEquals(Action.RESTART to 20_000L, waitForAction())
    }
}
