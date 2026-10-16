package fr.alex.chambre.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
            Settings(url = "http://homeassistant.local:8123", token = "", sleepDelaySec = 60, sensitivity = 0.03f),
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
}
