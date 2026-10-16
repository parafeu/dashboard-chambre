package fr.alex.chambre.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchGateTest {
    private var state = Presence.AWAKE
    private var touches = 0
    private val gate = TouchGate(isAsleep = { state == Presence.ASLEEP }, onTouch = { touches++; state = Presence.AWAKE })

    @Test
    fun awake_touches_pass_through_and_reset_timer() {
        assertFalse(gate.shouldConsume(TouchGate.Action.DOWN))
        assertFalse(gate.shouldConsume(TouchGate.Action.MOVE))
        assertFalse(gate.shouldConsume(TouchGate.Action.UP))
        assertEquals(1, touches)
    }

    @Test
    fun first_gesture_on_sleeping_screen_only_wakes() {
        state = Presence.ASLEEP
        assertTrue(gate.shouldConsume(TouchGate.Action.DOWN))
        assertEquals(Presence.AWAKE, state)
        assertTrue(gate.shouldConsume(TouchGate.Action.MOVE))
        assertTrue(gate.shouldConsume(TouchGate.Action.UP))
        // next gesture goes through
        assertFalse(gate.shouldConsume(TouchGate.Action.DOWN))
    }
}
