package fr.alex.chambre.announce

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One occurrence read from the tablet's calendars. [calendar] is the Android calendar id;
 * calendar + title + start identify an occurrence (the same for every refresh).
 */
data class CalendarEvent(
    val calendar: String,
    val title: String,
    val start: Instant,
    val allDay: Boolean,
) {
    val key: String get() = "$calendar|$title|$start"

    /** All-day events only count on their own date (the read window starts one hour back). */
    fun isToday(now: Instant, zone: ZoneId): Boolean =
        !allDay || start.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
}

/** Builds an event from a CalendarContract.Instances row; Android stores all-day events at 00:00 UTC of their date. */
fun instanceEvent(calendar: String, title: String?, beginMillis: Long, allDay: Boolean, zone: ZoneId): CalendarEvent {
    val begin = Instant.ofEpochMilli(beginMillis)
    val start = if (allDay) begin.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant() else begin
    return CalendarEvent(calendar, title.orEmpty(), start, allDay)
}
