package fr.alex.chambre.mealie

import java.text.Normalizer

/** The chips above the recipe grid. */
sealed interface RecipeFilter {
    val label: String
    data object All : RecipeFilter { override val label = "Toutes" }
    data object Quick : RecipeFilter { override val label = "Moins de 45 min" }
    data class Category(val name: String) : RecipeFilter { override val label get() = name }
}

private const val QUICK_MINUTES = 45

fun filtersFor(recipes: List<RecipeSummary>): List<RecipeFilter> =
    listOf(RecipeFilter.All, RecipeFilter.Quick) +
        recipes.flatMap { it.categories }.distinct().sortedBy { fold(it) }.map { RecipeFilter.Category(it) }

/** Search ignores case and accents; a recipe without a known time is never « quick ». */
fun filterRecipes(recipes: List<RecipeSummary>, query: String, filter: RecipeFilter): List<RecipeSummary> {
    val q = fold(query.trim())
    return recipes.filter { r ->
        val byFilter = when (filter) {
            RecipeFilter.All -> true
            RecipeFilter.Quick -> (r.totalMinutes ?: Int.MAX_VALUE) < QUICK_MINUTES
            is RecipeFilter.Category -> filter.name in r.categories
        }
        byFilter && (q.isEmpty() || q in fold(r.name))
    }
}

/** « 25 min · 2 pers. », « 2 h 05 · 8 pers. » */
fun cardMeta(r: RecipeSummary): String =
    listOfNotNull(r.totalMinutes?.let { minutesLabel(it) }, formatQuantity(r.servings, null) + " pers.").joinToString(" · ")

fun minutesLabel(minutes: Int): String = timerLabel(minutes * 60)

data class TodayBanner(val label: String, val recipe: RecipeSummary)

/** « Au menu » banner: today's dinner first, then any other planned recipe that exists in the list. */
fun todaysBanner(meals: List<MealPlanEntry>, recipes: List<RecipeSummary>): TodayBanner? {
    val bySlug = recipes.associateBy { it.slug }
    val entry = meals.filter { it.slug in bySlug }.minByOrNull { if (it.entryType == "dinner") 0 else 1 } ?: return null
    val label = when (entry.entryType) {
        "dinner" -> "AU MENU CE SOIR"
        "lunch" -> "AU MENU CE MIDI"
        "breakfast" -> "AU PETIT-DÉJEUNER"
        else -> "AU MENU AUJOURD'HUI"
    }
    return TodayBanner(label, bySlug.getValue(entry.slug))
}

private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
