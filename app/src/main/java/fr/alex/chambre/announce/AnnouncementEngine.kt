package fr.alex.chambre.announce

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

sealed interface Announcement {
    val key: String

    data class Meeting(override val key: String, val title: String, val start: Instant) : Announcement
    data class Alert(override val key: String, val title: String, val at: Instant) : Announcement
    data class TimerDone(override val key: String, val name: String, val firedAt: Instant) : Announcement
}

/** What to say now, the banner to show (meeting or timer) and the pending alerts, newest first. */
data class Tick(
    val speak: List<Announcement>,
    val banner: Announcement?,
    val alerts: List<Announcement.Alert>,
)

fun speechFor(a: Announcement): String = when (a) {
    is Announcement.Meeting -> "Réunion dans 5 minutes : ${a.title}"
    is Announcement.Alert -> "Alerte : ${a.title}"
    is Announcement.TimerDone -> "${a.name} terminé"
}

/**
 * Decides announcements from the work and alerts calendars and from finished timers. Pure: the
 * caller feeds events and the clock, and persists keys it reports as done.
 */
class AnnouncementEngine(
    private val workCalendar: String,
    private val alertsCalendar: String,
    private val zone: ZoneId,
) {
    private var events: List<CalendarEvent> = emptyList()
    private val seenAlerts = LinkedHashMap<String, Announcement.Alert>()
    private val alertSpokenAt = HashMap<String, Instant>()
    private val spokenMeetings = HashSet<String>()
    private var meetingBanner: Announcement.Meeting? = null
    private var meetingBannerAt: Instant = Instant.EPOCH
    private val timers = LinkedHashMap<String, Announcement.TimerDone>()
    private val timerSpeeches = HashMap<String, Pair<Int, Instant>>()
    private val acknowledged = HashSet<String>()

    fun setEvents(list: List<CalendarEvent>) {
        events = list
    }

    fun timerFinished(name: String, now: Instant) {
        val key = "timer|$name|$now"
        timers[key] = Announcement.TimerDone(key, name, now)
    }

    fun acknowledge(key: String) {
        acknowledged += key
        seenAlerts.remove(key)
        timers.remove(key)
        if (meetingBanner?.key == key) meetingBanner = null
    }

    fun tick(now: Instant, done: Set<String>): Tick {
        val ignored = { key: String -> key in done || key in acknowledged }

        // Alerts: start in their first hour (late start included), then stay until acknowledged.
        for (e in events) {
            if (e.calendar != alertsCalendar || ignored(e.key) || e.key in seenAlerts) continue
            val at = alertTime(e)
            if (!now.isBefore(at) && now.isBefore(at.plus(ALERT_VOICE))) {
                seenAlerts[e.key] = Announcement.Alert(e.key, e.title, at)
            }
        }
        seenAlerts.keys.removeAll { ignored(it) }
        val alertVoice = seenAlerts.values.filter { a ->
            now.isBefore(a.at.plus(ALERT_VOICE)) &&
                alertSpokenAt[a.key]?.let { Duration.between(it, now) >= ALERT_REPEAT } ?: true
        }

        val timerVoice = timers.values.filter { t ->
            val (count, last) = timerSpeeches[t.key] ?: (0 to Instant.EPOCH)
            count < TIMER_MAX && (count == 0 || Duration.between(last, now) >= TIMER_REPEAT)
        }

        val meetingVoice = events.filter { e ->
            e.calendar == workCalendar && !e.allDay && !ignored(e.key) && e.key !in spokenMeetings &&
                !now.isBefore(e.start.minus(MEETING_LEAD)) && now.isBefore(e.start)
        }.map { Announcement.Meeting(it.key, it.title, it.start) }

        // One category speaks per tick: alert > timer > meeting; the others wait for the next tick.
        val speak: List<Announcement> = when {
            alertVoice.isNotEmpty() -> alertVoice.onEach { alertSpokenAt[it.key] = now }
            timerVoice.isNotEmpty() -> timerVoice.onEach {
                val count = timerSpeeches[it.key]?.first ?: 0
                timerSpeeches[it.key] = (count + 1) to now
            }
            meetingVoice.isNotEmpty() -> meetingVoice.onEach {
                spokenMeetings += it.key
                meetingBanner = it
                meetingBannerAt = now
            }
            else -> emptyList()
        }

        val meetingShown = meetingBanner?.takeIf { Duration.between(meetingBannerAt, now) < MEETING_BANNER }
        val banner = timers.values.lastOrNull() ?: meetingShown
        return Tick(speak, banner, seenAlerts.values.sortedByDescending { it.at })
    }

    private fun alertTime(e: CalendarEvent): Instant =
        if (e.allDay) e.start.atZone(zone).toLocalDate().atTime(ALL_DAY_ALERT_HOUR, 0).atZone(zone).toInstant() else e.start

    private companion object {
        val MEETING_LEAD: Duration = Duration.ofMinutes(5)
        val MEETING_BANNER: Duration = Duration.ofSeconds(60)
        val ALERT_VOICE: Duration = Duration.ofHours(1)
        val ALERT_REPEAT: Duration = Duration.ofMinutes(5)
        val TIMER_REPEAT: Duration = Duration.ofSeconds(30)
        const val TIMER_MAX = 5
        const val ALL_DAY_ALERT_HOUR = 9
    }
}
