package fr.alex.chambre.mealie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeFilterTest {
    private fun r(slug: String, name: String, minutes: Int?, vararg cats: String) =
        RecipeSummary("id-$slug", slug, name, null, minutes, 2.0, cats.toList())

    private val list = listOf(
        r("dahl", "Dahl express lentilles corail", 25),
        r("lasagnes", "Lasagnes à la bolognaise", 125, "Plat Principal"),
        r("curry", "Curry japonais", 70, "Plat Principal", "Asie"),
        r("salade", "Salade niçoise", null),
    )

    @Test
    fun filters_are_all_quick_then_the_categories_in_use() {
        assertEquals(listOf(RecipeFilter.All, RecipeFilter.Quick, RecipeFilter.Category("Asie"), RecipeFilter.Category("Plat Principal")), filtersFor(list))
    }

    @Test
    fun quick_means_under_45_minutes_with_a_known_time() {
        assertEquals(listOf("dahl"), filterRecipes(list, "", RecipeFilter.Quick).map { it.slug })
    }

    @Test
    fun category_and_search_combine_and_ignore_accents_and_case() {
        assertEquals(listOf("lasagnes", "curry"), filterRecipes(list, "", RecipeFilter.Category("Plat Principal")).map { it.slug })
        assertEquals(listOf("salade"), filterRecipes(list, "NICOISE", RecipeFilter.All).map { it.slug })
        assertEquals(listOf("lasagnes"), filterRecipes(list, "bolo", RecipeFilter.Category("Plat Principal")).map { it.slug })
    }

    @Test
    fun card_line_shows_time_and_servings() {
        assertEquals("25 min · 2 pers.", cardMeta(list[0]))
        assertEquals("2 h 05 · 2 pers.", cardMeta(list[1]))
        assertEquals("2 pers.", cardMeta(list[3]))
    }

    @Test
    fun todays_banner_picks_dinner_first_and_only_known_recipes() {
        val meals = listOf(MealPlanEntry("lunch", "dahl", "Dahl"), MealPlanEntry("dinner", "curry", "Curry"), MealPlanEntry("dinner", "inconnu", "?"))
        val banner = todaysBanner(meals, list)!!
        assertEquals("curry", banner.recipe.slug)
        assertEquals("AU MENU CE SOIR", banner.label)
        assertEquals("AU MENU CE MIDI", todaysBanner(meals.take(1), list)!!.label)
        assertNull(todaysBanner(listOf(MealPlanEntry("dinner", "inconnu", "?")), list))
    }
}
