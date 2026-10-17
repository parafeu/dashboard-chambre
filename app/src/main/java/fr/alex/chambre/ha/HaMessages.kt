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

sealed interface Incoming {
    data object AuthRequired : Incoming
    data object AuthOk : Incoming
    data class AuthInvalid(val message: String) : Incoming
    data class Result(val id: Int, val success: Boolean, val errorMessage: String?, val result: JsonElement? = null) : Incoming
    data class HaEvent(val id: Int, val eventType: String, val data: JsonObject) : Incoming
    data class EntitiesEvent(
        val id: Int,
        val added: Map<String, EntityState>,
        val changed: Map<String, JsonObject>,
        val removed: List<String>,
    ) : Incoming
    data class ForecastEvent(val id: Int, val hours: List<ForecastEntry>) : Incoming
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
        event.str("event_type")?.let { type ->
            return Incoming.HaEvent(id, type, event["data"] as? JsonObject ?: JsonObject(emptyMap()))
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

    /** Same as [callService] but asks HA to return the service's response (e.g. calendar.get_events). */
    fun callServiceWithResponse(
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
        put("return_response", true)
    }.toString()

    fun subscribeEvents(id: Int, eventType: String): String = buildJsonObject {
        put("id", id)
        put("type", "subscribe_events")
        put("event_type", eventType)
    }.toString()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
