package fr.alex.chambre.mealie

import kotlin.math.roundToInt

/** A duration found in a step's text, [start]..[end) in that text: shown as a timer chip in place. */
data class TimerSpan(val start: Int, val end: Int, val seconds: Int, val label: String)

private val DURATION = Regex(
    """(\d+(?:[.,]\d+)?)(?:\s*(?:à|-|–)\s*(\d+(?:[.,]\d+)?))?\s*(heures?|h|minutes?|mins?|mn|secondes?|sec|s)(?!\p{L})""" +
        """(?:\s*(\d{1,2})(?![\d\p{L}°])(?:\s*(?:min|minutes?)(?!\p{L}))?)?""",
    RegexOption.IGNORE_CASE,
)

/**
 * Durations in a recipe step (« 10 minutes », « 30 à 45 secondes », « 8-10 min », « 1 h 30 », « 1h15 »).
 * A range uses its upper bound. Other numbers (« 1 cm », « 5 sachets », « 180 °C ») are left alone.
 */
fun findTimers(text: String): List<TimerSpan> = DURATION.findAll(text).map { m ->
    val unit = m.groupValues[3].lowercase()
    val unitSeconds = when {
        unit.startsWith("h") -> 3600
        unit.startsWith("m") -> 60
        else -> 1
    }
    val amount = (m.groupValues[2].ifEmpty { m.groupValues[1] }).replace(',', '.').toDouble()
    val extraMinutes = if (unitSeconds == 3600) m.groups[4]?.value?.toIntOrNull() else null
    // « 10 minutes 3 » is not « 10 min 3 »: trailing minutes only count after hours.
    val end = if (unitSeconds == 3600 || m.groups[4] == null) m.range.last + 1 else m.groups[3]!!.range.last + 1
    val seconds = (amount * unitSeconds).roundToInt() + (extraMinutes ?: 0) * 60
    TimerSpan(m.range.first, end, seconds, timerLabel(seconds))
}.toList()

fun timerLabel(seconds: Int): String {
    if (seconds < 60) return "$seconds s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 && m > 0 -> "$h h ${m.toString().padStart(2, '0')}"
        h > 0 -> "$h h"
        s > 0 -> "$m min $s"
        else -> "$m min"
    }
}
