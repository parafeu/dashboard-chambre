package fr.alex.chambre.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import fr.alex.chambre.battery.RegistrationStore
import java.util.UUID

/** Which room this tablet serves: the bedroom panel, or the living-room dashboard with announcements. */
enum class Room { CHAMBRE, SALON }

data class Settings(
    val url: String = "http://homeassistant.local:8123",
    val token: String = "",
    val sleepDelaySec: Int = 60,
    /** Minimum moving-region size, as a fraction of the image (0.005–0.10). */
    val sensitivity: Float = 0.03f,
    val room: Room = Room.CHAMBRE,
    /** Android calendar ids (see AndroidCalendarSource); blank until chosen in the settings. */
    val workCalendar: String = "",
    val alertsCalendar: String = "",
)

/** Trims, drops trailing slashes and adds `http://` when no scheme was typed. */
fun normalizeUrl(url: String): String {
    val u = url.trim().trimEnd('/')
    return if (u.isEmpty() || "://" in u) u else "http://$u"
}

class SettingsStore(private val dataStore: DataStore<Preferences>) : RegistrationStore {

    val settings: Flow<Settings> = dataStore.data.map { p ->
        val d = Settings()
        Settings(
            url = p[URL] ?: d.url,
            token = p[TOKEN] ?: d.token,
            sleepDelaySec = p[SLEEP] ?: d.sleepDelaySec,
            sensitivity = p[SENSITIVITY] ?: d.sensitivity,
            room = Room.entries.firstOrNull { it.name == p[ROOM] } ?: d.room,
            workCalendar = p[WORK_CAL] ?: d.workCalendar,
            alertsCalendar = p[ALERTS_CAL] ?: d.alertsCalendar,
        )
    }

    suspend fun save(s: Settings) {
        dataStore.edit { p ->
            p[URL] = normalizeUrl(s.url)
            p[TOKEN] = s.token.trim()
            p[SLEEP] = s.sleepDelaySec
            p[SENSITIVITY] = s.sensitivity
            p[ROOM] = s.room.name
            p[WORK_CAL] = s.workCalendar.trim()
            p[ALERTS_CAL] = s.alertsCalendar.trim()
        }
    }

    override suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        dataStore.edit { p -> if (p[DEVICE_ID] == null) p[DEVICE_ID] = id }
        return dataStore.data.first()[DEVICE_ID]!!
    }

    override suspend fun webhookId(): String? = dataStore.data.first()[WEBHOOK_ID]

    override suspend fun saveWebhookId(id: String?) {
        dataStore.edit { p -> if (id == null) p.remove(WEBHOOK_ID) else p[WEBHOOK_ID] = id }
    }

    private companion object {
        val ROOM = stringPreferencesKey("room")
        val WORK_CAL = stringPreferencesKey("work_calendar")
        val ALERTS_CAL = stringPreferencesKey("alerts_calendar")
        val DEVICE_ID = stringPreferencesKey("mobile_app_device_id")
        val WEBHOOK_ID = stringPreferencesKey("mobile_app_webhook_id")
        val URL = stringPreferencesKey("url")
        val TOKEN = stringPreferencesKey("token")
        val SLEEP = intPreferencesKey("sleep_delay_sec")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
    }
}
