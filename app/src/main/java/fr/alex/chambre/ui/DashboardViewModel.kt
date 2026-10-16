package fr.alex.chambre.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.alex.chambre.ha.HaConfig
import fr.alex.chambre.ha.HaRepository
import fr.alex.chambre.presence.PresenceController
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Wires settings, the Home Assistant connection, the controller and presence together. */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app.dataStore)

    /** null until the stored settings are loaded. */
    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()

    val ha = HaRepository(
        viewModelScope,
        client,
        settings.filterNotNull().map { HaConfig(it.url, it.token) }.distinctUntilChanged(),
    )

    val controller = DashboardController(viewModelScope, ha)

    val presence = PresenceController(
        viewModelScope,
        sleepDelayMs = { (settings.value?.sleepDelaySec ?: Settings().sleepDelaySec) * 1_000L },
    ).also { it.start() }

    fun saveSettings(s: Settings) {
        viewModelScope.launch {
            store.save(s)
            ha.reconnect()
        }
    }
}
