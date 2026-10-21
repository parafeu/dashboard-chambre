package fr.alex.chambre.ha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Home Assistant's REST API, for the home-screen widgets: they act while the app (and its WebSocket) may be closed.
 * Throws [IOException] when HA can't be reached or answers with an error.
 */
class HaRestClient(private val baseUrl: String, private val token: String, private val client: OkHttpClient) {

    /** One entity's state, or null when HA doesn't know it. */
    suspend fun state(entityId: String): EntityState? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint("/api/states/$entityId")).auth().get().build()
        client.newCall(request).execute().use { r ->
            if (r.code == 404) return@use null
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val o = try {
                Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            } catch (e: IllegalArgumentException) {
                throw IOException("Bad state JSON", e)
            }
            EntityState(
                entityId = o["entity_id"]?.jsonPrimitive?.content ?: entityId,
                state = o["state"]?.jsonPrimitive?.content ?: "unknown",
                attributes = o["attributes"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
    }

    suspend fun call(domain: String, service: String, data: JsonObject): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("/api/services/$domain/$service"))
            .auth()
            .post(data.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            true
        }
    }

    /** A malformed URL typed in the settings is reported like an unreachable server. */
    private fun endpoint(path: String): okhttp3.HttpUrl =
        "$baseUrl$path".toHttpUrlOrNull() ?: throw IOException("Adresse Home Assistant invalide")

    private fun Request.Builder.auth() = header("Authorization", "Bearer ${token.trim()}")

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
