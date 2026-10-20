package fr.alex.chambre.mealie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** A recipe card (Mealie `GET /api/recipes` item). */
data class RecipeSummary(
    val id: String,
    val slug: String,
    val name: String,
    val imageUrl: String?,
    val totalMinutes: Int?,
    val servings: Double,
    val categories: List<String>,
)

/**
 * One ingredient line. [scalable] when Mealie parsed it into a quantity and a food, so it can follow the servings;
 * otherwise [display] is shown as written (free text, or a food « to taste »).
 */
data class Ingredient(
    val section: String?,
    val quantity: Double?,
    val unit: String?,
    val food: String?,
    val note: String?,
    val display: String,
) {
    val scalable: Boolean get() = food != null && (quantity ?: 0.0) > 0.0
}

data class Step(val title: String, val text: String)

data class Recipe(
    val id: String,
    val slug: String,
    val name: String,
    val imageUrl: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val totalMinutes: Int?,
    val baseServings: Double,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
)

/** One of today's meal plan entries that points at a recipe. */
data class MealPlanEntry(val entryType: String, val slug: String, val name: String)

private val HOURS = Regex("""(\d+)\s*(?:h\b|hours?\b|heures?\b)""", RegexOption.IGNORE_CASE)
private val MINUTES = Regex("""(\d+)\s*(?:m\b|min\b|mins\b|minutes?\b)""", RegexOption.IGNORE_CASE)
private val ISO = Regex("""^P(?:T)?(?:(\d+)H)?(?:(\d+)M)?""", RegexOption.IGNORE_CASE)

/** « 1 hour 10 minutes », « 25 minutes », « 1 heure 35 min », « PT1H30M » → minutes; null when none is found. */
fun parseDurationMinutes(text: String?): Int? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    ISO.find(t)?.takeIf { it.value.length > 2 }?.let { m ->
        return (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    }
    val h = HOURS.find(t)?.groupValues?.get(1)?.toIntOrNull()
    val m = MINUTES.find(t)?.groupValues?.get(1)?.toIntOrNull()
    if (h == null && m == null) return null
    return (h ?: 0) * 60 + (m ?: 0)
}

/** Mealie's servings, else its yield quantity, else the first number in the yield text, else 1. */
fun baseServings(recipeServings: Double?, yieldQuantity: Double?, yieldText: String?): Double =
    recipeServings?.takeIf { it > 0 }
        ?: yieldQuantity?.takeIf { it > 0 }
        ?: yieldText?.let { Regex("""\d+(?:[.,]\d+)?""").find(it)?.value?.replace(',', '.')?.toDoubleOrNull() }?.takeIf { it > 0 }
        ?: 1.0

fun parseRecipeList(json: JsonObject, baseUrl: String): List<RecipeSummary> =
    (json["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> summaryOf(o, baseUrl) } }

fun parseRecipe(json: JsonObject, baseUrl: String): Recipe {
    val s = summaryOf(json, baseUrl) ?: error("recipe without slug")
    val ingredients = (json["recipeIngredient"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val disabled = o.bool("disableAmount") == true
        Ingredient(
            section = o.str("title")?.takeIf { it.isNotBlank() },
            quantity = if (disabled) null else o.num("quantity"),
            unit = o.obj("unit")?.str("name")?.takeIf { it.isNotBlank() },
            food = o.obj("food")?.str("name")?.takeIf { it.isNotBlank() },
            note = o.str("note")?.takeIf { it.isNotBlank() },
            display = o.str("display")?.takeIf { it.isNotBlank() } ?: o.str("note").orEmpty(),
        )
    }
    val steps = (json["recipeInstructions"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        Step(o.str("title").orEmpty().trim(), o.str("text").orEmpty().trim()).takeIf { it.text.isNotEmpty() || it.title.isNotEmpty() }
    }
    return Recipe(
        id = s.id,
        slug = s.slug,
        name = s.name,
        imageUrl = s.imageUrl?.replace("/min-original.webp", "/original.webp"),
        prepMinutes = parseDurationMinutes(json.str("prepTime")),
        cookMinutes = parseDurationMinutes(json.str("cookTime") ?: json.str("performTime")),
        totalMinutes = s.totalMinutes,
        baseServings = s.servings,
        ingredients = ingredients,
        steps = steps,
    )
}

fun parseTodayMeals(json: JsonArray): List<MealPlanEntry> = json.mapNotNull { el ->
    val o = el as? JsonObject ?: return@mapNotNull null
    val recipe = o.obj("recipe") ?: return@mapNotNull null
    val slug = recipe.str("slug") ?: return@mapNotNull null
    MealPlanEntry(o.str("entryType").orEmpty(), slug, recipe.str("name").orEmpty())
}

private fun summaryOf(o: JsonObject, baseUrl: String): RecipeSummary? {
    val id = o.str("id") ?: return null
    val slug = o.str("slug") ?: return null
    val total = parseDurationMinutes(o.str("totalTime"))
        ?: listOfNotNull(parseDurationMinutes(o.str("prepTime")), parseDurationMinutes(o.str("cookTime") ?: o.str("performTime")))
            .takeIf { it.isNotEmpty() }?.sum()
    return RecipeSummary(
        id = id,
        slug = slug,
        name = o.str("name").orEmpty(),
        imageUrl = o.str("image")?.takeIf { it.isNotBlank() }?.let { "${baseUrl.trimEnd('/')}/api/media/recipes/$id/images/min-original.webp" },
        totalMinutes = total,
        servings = baseServings(o.num("recipeServings"), o.num("recipeYieldQuantity"), o.str("recipeYield")),
        categories = (o["recipeCategory"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
    )
}

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull
private fun JsonObject.num(key: String): Double? = prim(key)?.doubleOrNull
private fun JsonObject.bool(key: String): Boolean? = prim(key)?.contentOrNull?.toBooleanStrictOrNull()
private fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonElement)?.let { it as? JsonObject }
private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
