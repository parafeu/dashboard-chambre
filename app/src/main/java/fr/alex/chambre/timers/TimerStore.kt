package fr.alex.chambre.timers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.alex.chambre.ha.TodoItem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What a tablet remembers about the shared timers while Home Assistant is out of reach. */
interface TimerCache {
    suspend fun lastKnown(): List<TodoItem>
    suspend fun saveLastKnown(items: List<TodoItem>)
    /** Timers stopped on this tablet whose removal HA has not confirmed yet. */
    suspend fun stopped(): Set<String>
    suspend fun saveStopped(ids: Set<String>)
}

class TimerStore(private val dataStore: DataStore<Preferences>) : TimerCache {

    override suspend fun lastKnown(): List<TodoItem> {
        val text = dataStore.data.first()[LAST_KNOWN] ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val uid = o.str("uid") ?: return@mapNotNull null
            TodoItem(uid, o.str("summary") ?: "", o.str("description"))
        }
    }

    override suspend fun saveLastKnown(items: List<TodoItem>) {
        val text = buildJsonArray {
            items.forEach { i ->
                add(
                    buildJsonObject {
                        put("uid", i.uid)
                        put("summary", i.summary)
                        i.description?.let { put("description", it) }
                    },
                )
            }
        }.toString()
        dataStore.edit { it[LAST_KNOWN] = text }
    }

    override suspend fun stopped(): Set<String> = dataStore.data.first()[STOPPED] ?: emptySet()

    override suspend fun saveStopped(ids: Set<String>) {
        dataStore.edit { it[STOPPED] = ids }
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        val LAST_KNOWN = stringPreferencesKey("timers_last_known")
        val STOPPED = stringSetPreferencesKey("timers_stopped")
    }
}
