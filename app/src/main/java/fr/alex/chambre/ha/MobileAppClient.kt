package fr.alex.chambre.ha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class DeviceInfo(
    val deviceId: String,
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
)

/** A sensor as registered with the Mobile App integration (entity_category is always diagnostic). */
data class SensorDef(
    val uniqueId: String,
    val type: String,
    val name: String,
    val deviceClass: String,
    val unit: String?,
    val stateClass: String?,
    val icon: String,
    val state: JsonPrimitive,
)

data class SensorState(val uniqueId: String, val type: String, val state: JsonPrimitive, val icon: String)

enum class WebhookResult { OK, GONE, ERROR }

/** Home Assistant's Mobile App integration, as used by the official companion app. */
interface MobileAppApi {
    /** Registers the device; returns the webhook id, or null on failure. */
    suspend fun register(baseUrl: String, token: String, info: DeviceInfo): String?
    suspend fun registerSensor(baseUrl: String, webhookId: String, sensor: SensorDef): WebhookResult
    suspend fun updateStates(baseUrl: String, webhookId: String, states: List<SensorState>): WebhookResult
}

class MobileAppClient(private val client: OkHttpClient) : MobileAppApi {

    override suspend fun register(baseUrl: String, token: String, info: DeviceInfo): String? {
        val body = buildJsonObject {
            put("device_id", info.deviceId)
            put("app_id", APP_ID)
            put("app_name", "Dashboard Chambre")
            put("app_version", info.appVersion)
            put("device_name", DEVICE_NAME)
            put("manufacturer", info.manufacturer)
            put("model", info.model)
            put("os_name", "Android")
            put("os_version", info.osVersion)
            put("supports_encryption", false)
            putJsonObject("app_data") {}
        }
        val request = Request.Builder()
            .url("$baseUrl/api/mobile_app/registrations")
            .header("Authorization", "Bearer ${token.trim()}")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val json = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                    json["webhook_id"]?.jsonPrimitive?.content
                }
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override suspend fun registerSensor(baseUrl: String, webhookId: String, sensor: SensorDef): WebhookResult =
        webhook(baseUrl, webhookId, "register_sensor", buildJsonObject {
            put("unique_id", sensor.uniqueId)
            put("type", sensor.type)
            put("name", sensor.name)
            put("device_class", sensor.deviceClass)
            sensor.unit?.let { put("unit_of_measurement", it) }
            sensor.stateClass?.let { put("state_class", it) }
            put("entity_category", "diagnostic")
            put("icon", sensor.icon)
            put("state", sensor.state)
        })

    override suspend fun updateStates(baseUrl: String, webhookId: String, states: List<SensorState>): WebhookResult =
        webhook(baseUrl, webhookId, "update_sensor_states", JsonArray(states.map { s ->
            buildJsonObject {
                put("unique_id", s.uniqueId)
                put("type", s.type)
                put("state", s.state)
                put("icon", s.icon)
            }
        }))

    private suspend fun webhook(baseUrl: String, webhookId: String, type: String, data: kotlinx.serialization.json.JsonElement): WebhookResult {
        val payload = JsonObject(mapOf("type" to JsonPrimitive(type), "data" to data))
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/webhook/$webhookId")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
                client.newCall(request).execute().use { r ->
                    when {
                        r.code == 410 -> WebhookResult.GONE
                        r.isSuccessful -> WebhookResult.OK
                        else -> WebhookResult.ERROR
                    }
                }
            } catch (e: IOException) {
                WebhookResult.ERROR
            } catch (e: IllegalArgumentException) {
                WebhookResult.ERROR
            }
        }
    }

    companion object {
        const val APP_ID = "fr.alex.chambre"
        const val DEVICE_NAME = "Tablette chambre"
        private val JSON = "application/json".toMediaType()
    }
}
