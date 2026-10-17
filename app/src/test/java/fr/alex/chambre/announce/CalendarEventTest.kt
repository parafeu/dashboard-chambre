package fr.alex.chambre.announce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CalendarEventTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun timed_instance_keeps_its_begin() {
        val e = instanceEvent("7", "Point hebdo", Instant.parse("2026-10-17T12:00:00Z").toEpochMilli(), allDay = false, zone = paris)
        assertEquals(CalendarEvent("7", "Point hebdo", Instant.parse("2026-10-17T12:00:00Z"), allDay = false), e)
        assertEquals("7|Point hebdo|2026-10-17T12:00:00Z", e.key)
    }

    @Test
    fun all_day_instance_stored_at_utc_midnight_starts_at_local_midnight() {
        // Android stores all-day events at 00:00 UTC of their date.
        val e = instanceEvent("3", "Litière", Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), allDay = true, zone = paris)
        assertTrue(e.allDay)
        assertEquals(Instant.parse("2026-10-16T22:00:00Z"), e.start)
    }

    @Test
    fun untitled_instance_gets_an_empty_title() {
        assertEquals("", instanceEvent("3", null, 0, allDay = false, zone = paris).title)
    }

    @Test
    fun all_day_event_belongs_to_today_only_on_its_own_date() {
        val now = Instant.parse("2026-10-17T22:30:00Z") // 00:30 on the 18th in Paris
        val yesterday = instanceEvent("3", "Hier", Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), allDay = true, zone = paris)
        val today = instanceEvent("3", "Aujourd'hui", Instant.parse("2026-10-18T00:00:00Z").toEpochMilli(), allDay = true, zone = paris)
        assertFalse(yesterday.isToday(now, paris))
        assertTrue(today.isToday(now, paris))
    }
}
