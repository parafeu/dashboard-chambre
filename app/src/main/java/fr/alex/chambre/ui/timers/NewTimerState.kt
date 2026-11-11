package fr.alex.chambre.ui.timers

/** Quick durations of « Nouvelle minuterie », in minutes. */
val NEW_TIMER_PRESETS = listOf(1, 3, 5, 10, 15, 30)

/** The « Nouvelle minuterie » stepper: whole minutes, 1–180, starting at 5. */
data class NewTimerState(val minutes: Int = 5) {
    fun plus() = copy(minutes = (minutes + 1).coerceIn(MIN, MAX))
    fun minus() = copy(minutes = (minutes - 1).coerceIn(MIN, MAX))
    fun preset(m: Int) = copy(minutes = m.coerceIn(MIN, MAX))

    private companion object {
        const val MIN = 1
        const val MAX = 180
    }
}
