package fr.alex.chambre.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.OffsetDateTime

/** One item of a Home Assistant to-do list (`todo/item/subscribe`). */
data class TodoItem(val uid: String, val summary: String, val description: String?)

/** A Home Assistant to-do list entity, for the settings picker. */
data class TodoList(val entityId: String, val name: String)

/** The `todo.*` entities of a `get_states` result, by name. */
fun todoListsFrom(states: JsonElement?): List<TodoList> =
    (states as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = (o["entity_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
        if (!id.startsWith("todo.")) return@mapNotNull null
        val name = ((o["attributes"] as? JsonObject)?.get("friendly_name") as? JsonPrimitive)?.takeIf { it.isString }?.content
        TodoList(id, name ?: id)
    }.sortedBy { it.name.lowercase() }

sealed interface Incoming {
    data object AuthRequired : Incoming
    data object AuthOk : Incoming
    data class AuthInvalid(val message: String) : Incoming
    data class Result(val id: Int, val success: Boolean, val errorMessage: String?, val result: JsonElement? = null) : Incoming
    data class EntitiesEvent(
        val id: Int,
        val added: Map<String, EntityState>,
        val changed: Map<String, JsonObject>,
        val removed: List<String>,
    ) : Incoming
    data class ForecastEvent(val id: Int, val hours: List<ForecastEntry>) : Incoming
    /** The whole list, on subscribe and after every change. */
    data class TodoItemsEvent(val id: Int, val items: List<TodoItem>) : Incoming
    data object Unknown : Incoming
}

/** Encodes and decodes Home Assistant WebSocket API messages. */
object HaProtocol {

    fun parse(text: String): Incoming {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return Incoming.Unknown
        val id = obj["id"]?.jsonPrimitive?.intOrNull
        return when (obj.str("type")) {
            "auth_required" -> Incoming.AuthRequired
            "auth_ok" -> Incoming.AuthOk
            "auth_invalid" -> Incoming.AuthInvalid(obj.str("message") ?: "")
            "result" -> Incoming.Result(
                id = id ?: return Incoming.Unknown,
                success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false,
                errorMessage = (obj["error"] as? JsonObject)?.str("message"),
                result = obj["result"]?.takeUnless { it is JsonNull },
            )
            "event" -> parseEvent(id ?: return Incoming.Unknown, obj["event"] as? JsonObject ?: return Incoming.Unknown)
            else -> Incoming.Unknown
        }
    }

    private fun parseEvent(id: Int, event: JsonObject): Incoming {
        (event["items"] as? JsonArray)?.let { items ->
            return Incoming.TodoItemsEvent(
                id,
                items.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    TodoItem(o.str("uid") ?: return@mapNotNull null, o.str("summary") ?: "", o.str("description"))
                },
            )
        }
        val forecast = event["forecast"] as? JsonArray
        if (forecast != null) {
            val hours = forecast.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val time = o.str("datetime")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
                val temp = (o["temperature"] as? JsonPrimitive)?.doubleOrNull
                if (time == null || temp == null) {
                    null
                } else {
                    ForecastEntry(
                        time, o.str("condition") ?: "", temp,
                        templow = (o["templow"] as? JsonPrimitive)?.doubleOrNull,
                        precipitation = (o["precipitation"] as? JsonPrimitive)?.doubleOrNull,
                    )
                }
            }
            return Incoming.ForecastEvent(id, hours)
        }
        val added = (event["a"] as? JsonObject).orEmpty().mapValues { (entityId, v) ->
            val o = v.jsonObject
            EntityState(entityId, o.str("s") ?: "unknown", o["a"] as? JsonObject ?: JsonObject(emptyMap()))
        }
        val changed = (event["c"] as? JsonObject).orEmpty().mapValues { it.value.jsonObject }
        val removed = (event["r"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        return Incoming.EntitiesEvent(id, added, changed, removed)
    }

    fun auth(token: String): String = buildJsonObject {
        put("type", "auth")
        put("access_token", token.trim())
    }.toString()

    fun subscribeEntities(id: Int, ids: List<String>): String = buildJsonObject {
        put("id", id)
        put("type", "subscribe_entities")
        putJsonArray("entity_ids") { ids.forEach { add(JsonPrimitive(it)) } }
    }.toString()

    fun subscribeForecast(id: Int, entityId: String, type: String = "hourly"): String = buildJsonObject {
        put("id", id)
        put("type", "weather/subscribe_forecast")
        put("entity_id", entityId)
        put("forecast_type", type)
    }.toString()

    fun callService(
        id: Int,
        domain: String,
        service: String,
        entityId: String,
        data: JsonObject = JsonObject(emptyMap()),
    ): String = buildJsonObject {
        put("id", id)
        put("type", "call_service")
        put("domain", domain)
        put("service", service)
        putJsonObject("target") { put("entity_id", entityId) }
        put("service_data", data)
    }.toString()

    fun getStates(id: Int): String = buildJsonObject {
        put("id", id)
        put("type", "get_states")
    }.toString()

    fun subscribeTodoItems(id: Int, entityId: String): String = buildJsonObject {
        put("id", id)
        put("type", "todo/item/subscribe")
        put("entity_id", entityId)
    }.toString()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
