package fr.alex.chambre.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.alex.chambre.ha.EntityState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_states")

/** One JSON object per room: { entity_id: { "state": …, "attributes": {…} } }. */
class DataStoreWidgetStore(private val dataStore: DataStore<Preferences>) : WidgetStore {

    override suspend fun load(room: WidgetRoom): Map<String, EntityState> {
        val raw = dataStore.data.first()[key(room)] ?: return emptyMap()
        return try {
            Json.parseToJsonElement(raw).jsonObject.mapValues { (id, v) ->
                val o = v.jsonObject
                EntityState(id, o["state"]?.jsonPrimitive?.content ?: "unknown", o["attributes"] as? JsonObject ?: JsonObject(emptyMap()))
            }
        } catch (e: IllegalArgumentException) {
            emptyMap()
        }
    }

    override suspend fun save(room: WidgetRoom, states: Map<String, EntityState>) {
        val json = buildJsonObject {
            states.forEach { (id, s) -> put(id, buildJsonObject { put("state", JsonPrimitive(s.state)); put("attributes", s.attributes) }) }
        }
        dataStore.edit { it[key(room)] = json.toString() }
    }

    private fun key(room: WidgetRoom) = stringPreferencesKey("room_" + room.name)
}
