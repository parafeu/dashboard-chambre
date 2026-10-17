package fr.alex.chambre.announce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AnnouncementEngineTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val work = "calendar.travail"
    private val alerts = "calendar.alertes"
    private val engine = AnnouncementEngine(work, alerts, paris)

    private fun t(s: String) = Instant.parse(s)
    private fun meeting(title: String, start: String) = CalendarEvent(work, title, t(start), allDay = false)
    private fun alert(title: String, start: String, allDay: Boolean = false) = CalendarEvent(alerts, title, t(start), allDay)
    private fun spoken(tick: Tick) = tick.speak.map { speechFor(it) }

    @Test
    fun meeting_is_announced_five_minutes_before_once() {
        engine.setEvents(listOf(meeting("Point hebdo", "2026-10-17T12:00:00Z")))
        assertEquals(emptyList<String>(), spoken(engine.tick(t("2026-10-17T11:54:59Z"), emptySet())))
        val first = engine.tick(t("2026-10-17T11:55:00Z"), emptySet())
        assertEquals(listOf("Réunion dans 5 minutes : Point hebdo"), spoken(first))
        assertTrue(first.banner is Announcement.Meeting)
        assertEquals(emptyList<String>(), spoken(engine.tick(t("2026-10-17T11:55:05Z"), emptySet())))
    }

    @Test
    fun meeting_banner_disappears_after_a_minute() {
        engine.setEvents(listOf(meeting("Point", "2026-10-17T12:00:00Z")))
        engine.tick(t("2026-10-17T11:55:00Z"), emptySet())
        assertTrue(engine.tick(t("2026-10-17T11:55:59Z"), emptySet()).banner is Announcement.Meeting)
        assertNull(engine.tick(t("2026-10-17T11:56:00Z"), emptySet()).banner)
    }

    @Test
    fun started_or_all_day_or_done_meetings_are_not_announced() {
        val m = meeting("Déjà commencée", "2026-10-17T12:00:00Z")
        engine.setEvents(listOf(m, CalendarEvent(work, "Congé", t("2026-10-16T22:00:00Z"), allDay = true), meeting("Faite", "2026-10-17T12:02:00Z")))
        val done = setOf("$work|Faite|2026-10-17T12:00:00Z".replace("12:00:00", "12:02:00"))
        assertEquals(emptyList<String>(), spoken(engine.tick(t("2026-10-17T12:00:00Z"), done)))
    }

    @Test
    fun alert_repeats_every_five_minutes_for_an_hour_then_stays_on_screen() {
        engine.setEvents(listOf(alert("Litière", "2026-10-17T17:00:00Z")))
        val times = mutableListOf<String>()
        var i = 0L
        while (i <= 70 * 60) {
            val now = t("2026-10-17T17:00:00Z").plusSeconds(i)
            if (engine.tick(now, emptySet()).speak.isNotEmpty()) times += "${i / 60}"
            i += 5
        }
        assertEquals((0..55 step 5).map { "$it" }, times)
        val late = engine.tick(t("2026-10-17T18:10:00Z"), emptySet())
        assertEquals("Litière", (late.alerts.single() as Announcement.Alert).title)
        assertEquals("Alerte : Litière", speechFor(late.alerts.single()))
    }

    @Test
    fun all_day_alert_fires_at_nine() {
        engine.setEvents(listOf(alert("Poubelles", "2026-10-16T22:00:00Z", allDay = true)))
        assertTrue(engine.tick(t("2026-10-17T06:59:55Z"), emptySet()).alerts.isEmpty())
        assertEquals(listOf("Alerte : Poubelles"), spoken(engine.tick(t("2026-10-17T07:00:00Z"), emptySet())))
    }

    @Test
    fun acknowledged_or_done_alert_is_gone() {
        val a = alert("Litière", "2026-10-17T17:00:00Z")
        engine.setEvents(listOf(a))
        assertTrue(engine.tick(t("2026-10-17T17:00:00Z"), setOf(a.key)).alerts.isEmpty())
        engine.tick(t("2026-10-17T17:00:00Z"), emptySet())
        engine.acknowledge(a.key)
        val after = engine.tick(t("2026-10-17T17:05:00Z"), emptySet())
        assertTrue(after.alerts.isEmpty())
        assertTrue(after.speak.isEmpty())
    }

    @Test
    fun late_alert_shows_within_the_hour_only() { // RF1
        engine.setEvents(listOf(alert("Litière", "2026-10-17T17:00:00Z")))
        assertEquals(listOf("Alerte : Litière"), spoken(engine.tick(t("2026-10-17T17:40:00Z"), emptySet())))
        val other = AnnouncementEngine(work, alerts, paris)
        other.setEvents(listOf(alert("Litière", "2026-10-17T17:00:00Z")))
        assertTrue(other.tick(t("2026-10-17T18:00:00Z"), emptySet()).alerts.isEmpty())
    }

    @Test
    fun seen_alert_survives_a_refetch_that_no_longer_contains_it() {
        engine.setEvents(listOf(alert("Litière", "2026-10-17T17:00:00Z")))
        engine.tick(t("2026-10-17T17:00:00Z"), emptySet())
        engine.setEvents(emptyList())
        assertEquals(1, engine.tick(t("2026-10-17T18:30:00Z"), emptySet()).alerts.size)
    }

    @Test
    fun several_alerts_stack_newest_first() { // RF4
        val a = alert("Litière", "2026-10-17T17:00:00Z")
        val b = alert("Plantes", "2026-10-17T17:10:00Z")
        engine.setEvents(listOf(a, b))
        engine.tick(t("2026-10-17T17:00:00Z"), emptySet())
        val tick = engine.tick(t("2026-10-17T17:10:00Z"), emptySet())
        assertEquals(listOf("Plantes", "Litière"), tick.alerts.map { (it as Announcement.Alert).title })
        engine.acknowledge(b.key)
        assertEquals(listOf("Litière"), engine.tick(t("2026-10-17T17:11:00Z"), emptySet()).alerts.map { (it as Announcement.Alert).title })
    }

    @Test
    fun timer_repeats_every_thirty_seconds_five_times_until_ok() {
        engine.timerFinished("Minuteur 1", t("2026-10-17T10:00:00Z"))
        val said = (0..200 step 5).count { engine.tick(t("2026-10-17T10:00:00Z").plusSeconds(it.toLong()), emptySet()).speak.isNotEmpty() }
        assertEquals(5, said)
        val tick = engine.tick(t("2026-10-17T10:05:00Z"), emptySet())
        val banner = tick.banner as Announcement.TimerDone
        assertEquals("Minuteur 1 terminé", speechFor(banner))
        engine.acknowledge(banner.key)
        assertNull(engine.tick(t("2026-10-17T10:05:05Z"), emptySet()).banner)
    }

    @Test
    fun alert_speaks_first_and_meeting_follows_on_next_tick() {
        engine.setEvents(listOf(alert("Litière", "2026-10-17T11:55:00Z"), meeting("Point", "2026-10-17T12:00:00Z")))
        assertEquals(listOf("Alerte : Litière"), spoken(engine.tick(t("2026-10-17T11:55:00Z"), emptySet())))
        assertEquals(listOf("Réunion dans 5 minutes : Point"), spoken(engine.tick(t("2026-10-17T11:55:05Z"), emptySet())))
    }
}
