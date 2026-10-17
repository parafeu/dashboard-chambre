package fr.alex.chambre.announce

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** One event read from calendar.get_events. HA gives no uid: calendar + title + start identify it. */
data class CalendarEvent(
    val calendar: String,
    val title: String,
    val start: Instant,
    val allDay: Boolean,
) {
    val key: String get() = "$calendar|$title|$start"
}

/** Parses a calendar.get_events response (`{"calendar.x": {"events": [...]}}`) for [calendar]. */
fun parseCalendarEvents(response: JsonObject, calendar: String, zone: ZoneId): List<CalendarEvent> {
    val events = ((response[calendar] as? JsonObject)?.get("events") as? JsonArray) ?: return emptyList()
    return events.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val raw = (o["start"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val title = (o["summary"] as? JsonPrimitive)?.content ?: ""
        if (raw.length == 10) {
            val day = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return@mapNotNull null
            CalendarEvent(calendar, title, day.atStartOfDay(zone).toInstant(), allDay = true)
        } else {
            val t = runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() ?: return@mapNotNull null
            CalendarEvent(calendar, title, t, allDay = false)
        }
    }
}
