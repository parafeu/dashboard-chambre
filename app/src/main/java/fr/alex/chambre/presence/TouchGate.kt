package fr.alex.chambre.presence

/**
 * Decides which touch events reach the UI. Every touch counts as activity; a gesture that starts
 * while the screen is asleep only wakes it and is swallowed until the finger lifts.
 */
class TouchGate(
    private val isAsleep: () -> Boolean,
    private val onTouch: () -> Unit,
) {
    enum class Action { DOWN, MOVE, UP, CANCEL }

    private var swallowing = false

    /** Returns true when the event must not reach the UI. */
    fun shouldConsume(action: Action): Boolean {
        if (action == Action.DOWN) {
            swallowing = isAsleep()
            onTouch()
            return swallowing
        }
        val consume = swallowing
        if (action == Action.UP || action == Action.CANCEL) swallowing = false
        return consume
    }
}
