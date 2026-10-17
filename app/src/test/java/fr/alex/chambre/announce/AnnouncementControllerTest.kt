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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private class CalApi : HaApi {
    override val conn = MutableStateFlow(ConnState.CONNECTED)
    override val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    override val forecast = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val forecastDaily = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val errors = MutableSharedFlow<String>()
    override val events = MutableSharedFlow<Incoming.HaEvent>(extraBufferCapacity = 4)
    val responses = HashMap<String, JsonObject?>()
    val asked = mutableListOf<String>()
    override suspend fun call(domain: String, service: String, entityId: String, data: JsonObject) = true
    override suspend fun callForResponse(domain: String, service: String, entityId: String, data: JsonObject): JsonObject? {
        asked += entityId
        return responses[entityId]
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
    private val api = CalApi()
    private val store = MemStore()
    private val speaker = FakeSpeaker()
    private var wakes = 0
    private val settings = MutableStateFlow(Settings(token = "t", room = Room.SALON))

    private fun events(cal: String, body: String) =
        Json.parseToJsonElement("""{"$cal":{"events":[$body]}}""").jsonObject

    private fun TestScope.start(): AnnouncementController =
        AnnouncementController(
            backgroundScope, api, settings, store, speaker,
            now = { base.plusMillis(testScheduler.currentTime) },
            zone = ZoneId.of("Europe/Paris"),
            wake = { wakes++ },
        ).also { runCurrent() }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun meeting_is_spoken_woken_and_remembered() = runTest {
        api.responses["calendar.travail"] = events("calendar.travail", """{"start":"2026-10-17T14:00:00+02:00","summary":"Point hebdo"}""")
        api.responses["calendar.alertes"] = events("calendar.alertes", "")
        start()
        advance(65_000)
        assertEquals(listOf("Réunion dans 5 minutes : Point hebdo"), speaker.said)
        assertEquals(1, wakes)
        assertTrue(store.keys.single().startsWith("calendar.travail|Point hebdo|"))
    }

    @Test
    fun acknowledged_alert_is_persisted_and_silenced() = runTest {
        api.responses["calendar.travail"] = events("calendar.travail", "")
        api.responses["calendar.alertes"] = events("calendar.alertes", """{"start":"2026-10-17T13:54:00+02:00","summary":"Litière"}""")
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
        api.responses["calendar.alertes"] = events("calendar.alertes", "")
        val c = start()
        advance(5_000)
        assertNotNull(c.error.value)
        assertTrue(c.error.value!!.contains("calendar.travail"))
    }

    @Test
    fun timer_finished_uses_friendly_name() = runTest {
        api.responses["calendar.travail"] = events("calendar.travail", "")
        api.responses["calendar.alertes"] = events("calendar.alertes", "")
        api.entities.value = mapOf("timer.minuteur_2" to EntityState("timer.minuteur_2", "idle", JsonObject(mapOf("friendly_name" to JsonPrimitive("Minuteur 2")))))
        start()
        api.events.emit(Incoming.HaEvent(9, "timer.finished", JsonObject(mapOf("entity_id" to JsonPrimitive("timer.minuteur_2")))))
        advance(5_000)
        assertEquals(listOf("Minuteur 2 terminé"), speaker.said)
    }

    @Test
    fun bedroom_mode_does_nothing() = runTest { // RF5
        settings.value = Settings(token = "t", room = Room.CHAMBRE)
        val c = start()
        advance(10 * 60_000)
        assertTrue(api.asked.isEmpty())
        assertTrue(speaker.said.isEmpty())
        assertNull(c.error.value)
    }

    @Test
    fun upcoming_lists_remaining_events_of_both_calendars_in_order() = runTest {
        api.responses["calendar.travail"] = events("calendar.travail",
            """{"start":"2026-10-17T13:00:00+02:00","summary":"Passée"},{"start":"2026-10-17T16:30:00+02:00","summary":"Revue"},{"start":"2026-10-17T14:00:00+02:00","summary":"Point"}""")
        api.responses["calendar.alertes"] = events("calendar.alertes", """{"start":"2026-10-17T19:00:00+02:00","summary":"Litière"}""")
        val c = start()
        assertEquals(listOf("Point", "Revue", "Litière"), c.upcoming.value.map { it.title })
    }
}
