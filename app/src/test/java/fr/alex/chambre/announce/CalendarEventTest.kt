package fr.alex.chambre.announce

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CalendarEventTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun parses_timed_events() {
        val r = json("""{"calendar.travail":{"events":[{"start":"2026-10-17T14:00:00+02:00","end":"2026-10-17T14:30:00+02:00","summary":"Point hebdo"}]}}""")
        val e = parseCalendarEvents(r, "calendar.travail", paris).single()
        assertEquals(CalendarEvent("calendar.travail", "Point hebdo", Instant.parse("2026-10-17T12:00:00Z"), allDay = false), e)
        assertEquals("calendar.travail|Point hebdo|2026-10-17T12:00:00Z", e.key)
    }

    @Test
    fun all_day_event_starts_at_local_midnight() {
        val r = json("""{"calendar.alertes":{"events":[{"start":"2026-10-17","end":"2026-10-18","summary":"Litière"}]}}""")
        val e = parseCalendarEvents(r, "calendar.alertes", paris).single()
        assertTrue(e.allDay)
        assertEquals(Instant.parse("2026-10-16T22:00:00Z"), e.start)
    }

    @Test
    fun missing_calendar_or_garbage_gives_empty_list() {
        assertEquals(emptyList<CalendarEvent>(), parseCalendarEvents(json("""{"calendar.autre":{"events":[]}}"""), "calendar.travail", paris))
        assertEquals(emptyList<CalendarEvent>(), parseCalendarEvents(json("""{"calendar.travail":{"events":[{"summary":"sans date"}]}}"""), "calendar.travail", paris))
    }
}
