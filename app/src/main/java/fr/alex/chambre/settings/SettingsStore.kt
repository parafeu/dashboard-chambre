package fr.alex.chambre.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val url: String = "http://homeassistant.local:8123",
    val token: String = "",
    val sleepDelaySec: Int = 60,
    val sensitivity: Float = 0.02f,
)

/** Trims, drops trailing slashes and adds `http://` when no scheme was typed. */
fun normalizeUrl(url: String): String {
    val u = url.trim().trimEnd('/')
    return if (u.isEmpty() || "://" in u) u else "http://$u"
}

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data.map { p ->
        val d = Settings()
        Settings(
            url = p[URL] ?: d.url,
            token = p[TOKEN] ?: d.token,
            sleepDelaySec = p[SLEEP] ?: d.sleepDelaySec,
            sensitivity = p[SENSITIVITY] ?: d.sensitivity,
        )
    }

    suspend fun save(s: Settings) {
        dataStore.edit { p ->
            p[URL] = normalizeUrl(s.url)
            p[TOKEN] = s.token.trim()
            p[SLEEP] = s.sleepDelaySec
            p[SENSITIVITY] = s.sensitivity
        }
    }

    private companion object {
        val URL = stringPreferencesKey("url")
        val TOKEN = stringPreferencesKey("token")
        val SLEEP = intPreferencesKey("sleep_delay_sec")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
    }
}
