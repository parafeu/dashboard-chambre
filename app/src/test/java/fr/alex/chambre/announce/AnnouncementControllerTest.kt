package fr.alex.chambre.announce

import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.ForecastEntry
import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.ha.Incoming
import fr.alex.chambre.settings.Room
import fr.alex.chambre.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private const val W = "11"
private const val A = "12"

/** Home Assistant is only used for timer events now. */
private class TimerApi : HaApi {
    override val conn = MutableStateFlow(ConnState.CONNECTED)
    override val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    override val forecast = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val forecastDaily = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val errors = MutableSharedFlow<String>()
    override val events = MutableSharedFlow<Incoming.HaEvent>(extraBufferCapacity = 4)
    override suspend fun call(domain: String, service: String, entityId: String, data: JsonObject) = true
}

private class FakeCalendars : CalendarSource {
    var permission = true
    /** calendar id → events; an id absent from the map is a calendar that is not on the tablet. */
    val events = HashMap<String, List<CalendarEvent>>()
    val asked = mutableListOf<String>()
    var lastWindow: Pair<Instant, Instant>? = null
    override fun calendars() = if (permission) events.keys.map { DeviceCalendar(it, "Agenda $it", "moi@gmail.com") } else null
    override fun read(calendarId: String, from: Instant, to: Instant): CalendarRead {
        asked += calendarId
        lastWindow = from to to
        if (!permission) return CalendarRead.NoPermission
        return events[calendarId]?.let { CalendarRead.Events(it) } ?: CalendarRead.NotFound
    }
}

private class MemStore : DoneStore {
    val keys = linkedSetOf<String>()
    override suspend fun done() = keys.toSet()
    override suspend fun markDone(key: String, at: Instant) { keys += key }
}

private class FakeSpeaker : Speaker {
    val said = mutableListOf<String>()
    override fun say(text: String) { said += text }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementControllerTest {
    private val base = Instant.parse("2026-10-17T11:54:00Z")
    private val api = TimerApi()
    private val cals = FakeCalendars()
    private val store = MemStore()
    private val speaker = FakeSpeaker()
    private var wakes = 0
    private val settings = MutableStateFlow(Settings(token = "t", room = Room.SALON, workCalendar = W, alertsCalendar = A))

    private fun ev(cal: String, title: String, at: String, allDay: Boolean = false) =
        CalendarEvent(cal, title, Instant.parse(at), allDay)

    private fun TestScope.start(): AnnouncementController =
        AnnouncementController(
            backgroundScope, api, cals, settings, store, speaker,
            now = { base.plusMillis(testScheduler.currentTime) },
            zone = ZoneId.of("Europe/Paris"),
            wake = { wakes++ },
            io = StandardTestDispatcher(testScheduler),
        ).also { runCurrent() }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun meeting_is_spoken_woken_and_remembered() = runTest {
        cals.events[W] = listOf(ev(W, "Point hebdo", "2026-10-17T12:00:00Z"))
        cals.events[A] = emptyList()
        start()
        advance(65_000)
        assertEquals(listOf("Réunion dans 5 minutes : Point hebdo"), speaker.said)
        assertEquals(1, wakes)
        assertTrue(store.keys.single().startsWith("$W|Point hebdo|"))
    }

    @Test
    fun acknowledged_alert_is_persisted_and_silenced() = runTest {
        cals.events[W] = emptyList()
        cals.events[A] = listOf(ev(A, "Litière", "2026-10-17T11:54:00Z"))
        val c = start()
        advance(5_000)
        assertEquals(listOf("Alerte : Litière"), speaker.said)
        val key = c.state.value.alerts.single().key
        c.acknowledge(key)
        runCurrent()
        assertTrue(key in store.keys)
        advance(10 * 60_000)
        assertEquals(1, speaker.said.size)
        assertTrue(c.state.value.alerts.isEmpty())
    }

    @Test
    fun missing_calendar_reports_an_error_without_crashing() = runTest { // RF3
        cals.events[A] = listOf(ev(A, "Litière", "2026-10-17T17:00:00Z"))
        val c = start()
        advance(5_000)
        assertEquals("Agenda Travail introuvable sur la tablette", c.error.value)
        assertEquals(listOf("Litière"), c.upcoming.value.map { it.title })
    }

    @Test
    fun calendar_permission_refused_is_reported() = runTest {
        cals.permission = false
        val c = start()
        advance(5_000)
        assertEquals("Accès aux agendas non autorisé", c.error.value)
    }

    @Test
    fun granting_the_permission_is_picked_up_at_once() = runTest {
        cals.permission = false
        cals.events[W] = listOf(ev(W, "Revue", "2026-10-17T14:30:00Z"))
        cals.events[A] = emptyList()
        val c = start()
        cals.permission = true
        c.refreshNow()
        advance(1_000)
        assertNull(c.error.value)
        assertEquals(listOf("Revue"), c.upcoming.value.map { it.title })
    }

    @Test
    fun unchosen_calendars_are_reported_and_not_read() = runTest {
        settings.value = Settings(token = "t", room = Room.SALON, workCalendar = "", alertsCalendar = "")
        val c = start()
        advance(5_000)
        assertEquals("Choisir les agendas Travail et Alertes", c.error.value)
        assertTrue(cals.asked.isEmpty())
    }

    @Test
    fun calendars_are_read_again_every_minute() = runTest {
        cals.events[W] = emptyList()
        cals.events[A] = emptyList()
        val c = start()
        cals.events[W] = listOf(ev(W, "Ajoutée", "2026-10-17T16:00:00Z"))
        advance(60_000)
        assertEquals(listOf("Ajoutée"), c.upcoming.value.map { it.title })
    }

    @Test
    fun timer_finished_uses_friendly_name() = runTest {
        cals.events[W] = emptyList()
        cals.events[A] = emptyList()
        api.entities.value = mapOf("timer.minuteur_2" to EntityState("timer.minuteur_2", "idle", JsonObject(mapOf("friendly_name" to JsonPrimitive("Minuteur 2")))))
        start()
        api.events.emit(Incoming.HaEvent(9, "timer.finished", JsonObject(mapOf("entity_id" to JsonPrimitive("timer.minuteur_2")))))
        advance(5_000)
        assertEquals(listOf("Minuteur 2 terminé"), speaker.said)
    }

    @Test
    fun only_kitchen_timers_are_announced() = runTest {
        cals.events[W] = emptyList()
        cals.events[A] = emptyList()
        val c = start()
        api.events.emit(Incoming.HaEvent(9, "timer.finished", JsonObject(mapOf("entity_id" to JsonPrimitive("timer.lave_linge")))))
        advance(5_000)
        assertTrue(speaker.said.isEmpty())
        assertTrue(c.state.value.banner == null)
    }

    @Test
    fun bedroom_mode_does_nothing() = runTest { // RF5
        settings.value = Settings(token = "t", room = Room.CHAMBRE, workCalendar = W, alertsCalendar = A)
        val c = start()
        advance(10 * 60_000)
        assertTrue(cals.asked.isEmpty())
        assertTrue(speaker.said.isEmpty())
        assertNull(c.error.value)
    }

    @Test
    fun upcoming_lists_remaining_events_of_both_calendars_in_order() = runTest {
        cals.events[W] = listOf(
            ev(W, "Passée", "2026-10-17T11:00:00Z"), ev(W, "Revue", "2026-10-17T14:30:00Z"), ev(W, "Point", "2026-10-17T12:00:00Z"),
        )
        cals.events[A] = listOf(ev(A, "Litière", "2026-10-17T17:00:00Z"), ev(A, "Hier", "2026-10-15T22:00:00Z", allDay = true))
        val c = start()
        assertEquals(listOf("Point", "Revue", "Litière"), c.upcoming.value.map { it.title })
        // window: one hour back (late alerts) → next local midnight
        assertEquals(Instant.parse("2026-10-17T10:54:00Z") to Instant.parse("2026-10-17T22:00:00Z"), cals.lastWindow)
    }

    @Test
    fun all_day_work_events_are_not_meetings() = runTest {
        // e.g. « Télétravail » or a daily task: no time, so nothing to announce and nothing to list.
        cals.events[W] = listOf(ev(W, "Télétravail", "2026-10-16T22:00:00Z", allDay = true), ev(W, "Revue", "2026-10-17T14:30:00Z"))
        cals.events[A] = listOf(ev(A, "Poubelles", "2026-10-16T22:00:00Z", allDay = true))
        val c = start()
        advance(10 * 60_000)
        assertEquals(listOf("Poubelles", "Revue"), c.upcoming.value.map { it.title })
        assertTrue(speaker.said.none { "Télétravail" in it })
    }

    @Test
    fun calendars_are_read_without_home_assistant() = runTest {
        api.conn.value = ConnState.CONNECTING
        cals.events[W] = listOf(ev(W, "Revue", "2026-10-17T14:30:00Z"))
        cals.events[A] = emptyList()
        val c = start()
        assertEquals(listOf("Revue"), c.upcoming.value.map { it.title })
        assertNull(c.error.value)
    }
}
