package fr.alex.chambre.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.alex.chambre.ha.EntityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreWidgetStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    @Test
    fun `states survive a round trip per room`() = runBlocking {
        val store = DataStoreWidgetStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("w.preferences_pb") })
        val light = EntityState("light.x", "on", buildJsonObject { put("brightness", JsonPrimitive(102)) })
        store.save(WidgetRoom.CHAMBRE, mapOf("light.x" to light))
        assertEquals(mapOf("light.x" to light), store.load(WidgetRoom.CHAMBRE))
        assertEquals(emptyMap<String, EntityState>(), store.load(WidgetRoom.SALON))
    }
}
