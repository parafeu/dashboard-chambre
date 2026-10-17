package fr.alex.chambre.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    private fun store() = SettingsStore(
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("settings.preferences_pb") },
    )

    @Test
    fun defaults() = runBlocking {
        assertEquals(
            Settings(url = "http://homeassistant.local:8123", token = "", sleepDelaySec = 60, sensitivity = 0.03f, room = Room.CHAMBRE, workCalendar = "", alertsCalendar = ""),
            store().settings.first(),
        )
    }

    @Test
    fun round_trip() = runBlocking {
        val s = store()
        val saved = Settings(url = "http://10.0.0.2:8123", token = "abc", sleepDelaySec = 15, sensitivity = 0.05f)
        s.save(saved)
        assertEquals(saved, s.settings.first())
    }

    @Test
    fun trims_url_and_token() = runBlocking { // RF5
        val s = store()
        s.save(Settings(url = " http://ha:8123/ \n", token = "  tok \n"))
        val read = s.settings.first()
        assertEquals("http://ha:8123", read.url)
        assertEquals("tok", read.token)
    }

    @Test
    fun adds_http_scheme_when_missing() = runBlocking { // C1
        val s = store()
        s.save(Settings(url = "192.168.1.10:8123", token = "t"))
        assertEquals("http://192.168.1.10:8123", s.settings.first().url)
    }

    @Test
    fun device_id_is_generated_once_and_kept() = runBlocking {
        val s = store()
        val id = s.deviceId()
        assertEquals(36, id.length)
        assertEquals(id, s.deviceId())
    }

    @Test
    fun webhook_id_round_trip_and_clear() = runBlocking {
        val s = store()
        assertEquals(null, s.webhookId())
        s.saveWebhookId("wh1")
        assertEquals("wh1", s.webhookId())
        s.saveWebhookId(null)
        assertEquals(null, s.webhookId())
    }

    @Test
    fun salon_room_and_calendars_round_trip() = runBlocking {
        val s = store()
        val saved = Settings(url = "http://ha:8123", token = "t", room = Room.SALON, workCalendar = "11", alertsCalendar = "12")
        s.save(saved)
        assertEquals(saved, s.settings.first())
    }

    @Test
    fun camera_api_defaults_to_camerax_and_round_trips() = runBlocking {
        val s = store()
        assertEquals(CameraApi.CAMERAX, s.settings.first().cameraApi)
        s.save(Settings(token = "t", cameraApi = CameraApi.CAMERA1))
        assertEquals(CameraApi.CAMERA1, s.settings.first().cameraApi)
    }

    @Test
    fun unknown_camera_api_falls_back_to_camerax() = runBlocking {
        val ds = PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("raw.preferences_pb") }
        ds.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("camera_api")] = "CAMERA3" }
        assertEquals(CameraApi.CAMERAX, SettingsStore(ds).settings.first().cameraApi)
    }

    @Test
    fun whitespace_inside_a_pasted_token_is_dropped_on_save() = runBlocking {
        val s = store()
        s.save(Settings(token = " abc.def\n.ghi \r\n"))
        assertEquals("abc.def.ghi", s.settings.first().token)
    }

    @Test
    fun a_token_already_saved_with_a_line_break_is_read_clean() = runBlocking {
        // Saved by v1.2.0: a line break inside the token crashed every HA request at start-up.
        val ds = PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("raw.preferences_pb") }
        ds.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("token")] = "abc.def\n.ghi" }
        assertEquals("abc.def.ghi", SettingsStore(ds).settings.first().token)
    }

    @Test
    fun unknown_room_falls_back_to_chambre() = runBlocking {
        val ds = PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("raw.preferences_pb") }
        ds.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("room")] = "GARAGE" }
        assertEquals(Room.CHAMBRE, SettingsStore(ds).settings.first().room)
    }
}
