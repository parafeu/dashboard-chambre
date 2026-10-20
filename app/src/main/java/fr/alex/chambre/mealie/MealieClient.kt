package fr.alex.chambre.mealie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A Mealie failure, with a message for the screen. */
class MealieException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface MealieApi {
    suspend fun recipes(): List<RecipeSummary>
    suspend fun recipe(slug: String): Recipe
    suspend fun todayMeals(): List<MealPlanEntry>
}

/** Read-only Mealie v3 API client (`Authorization: Bearer <API key>`). */
class MealieClient(
    private val baseUrl: String,
    private val token: String,
    private val http: OkHttpClient,
) : MealieApi {

    override suspend fun recipes(): List<RecipeSummary> {
        val all = mutableListOf<RecipeSummary>()
        var page = 1
        do {
            val json = get("/api/recipes?page=$page&perPage=$PAGE_SIZE&orderBy=name&orderDirection=asc").jsonObject
            all += parseRecipeList(json, baseUrl)
            val pages = (json["total_pages"] as? JsonPrimitive)?.intOrNull ?: 1
            page++
        } while (page <= pages && page <= MAX_PAGES)
        return all
    }

    override suspend fun recipe(slug: String): Recipe = parseRecipe(get("/api/recipes/$slug").jsonObject, baseUrl)

    override suspend fun todayMeals(): List<MealPlanEntry> = parseTodayMeals(get("/api/households/mealplans/today").jsonArray)

    private suspend fun get(path: String): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> throw MealieException("Clé API Mealie refusée")
                    !response.isSuccessful -> throw MealieException("Mealie a répondu ${response.code}")
                }
                Json.parseToJsonElement(response.body?.string().orEmpty())
            }
        } catch (e: MealieException) {
            throw e
        } catch (e: IOException) {
            throw MealieException("Mealie injoignable (${e.message ?: e.javaClass.simpleName})", e)
        } catch (e: IllegalArgumentException) {
            throw MealieException("Adresse Mealie invalide", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw MealieException("Réponse Mealie illisible", e)
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
    }
}

