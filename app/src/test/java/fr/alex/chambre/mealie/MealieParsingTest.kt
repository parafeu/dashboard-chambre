package fr.alex.chambre.mealie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic recipes shaped like Mealie v3 responses (the repository is public: no real data). */
class MealieParsingTest {
    private val base = "https://mealie.example"
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun durations_in_mealie_strings_become_minutes() {
        assertEquals(70, parseDurationMinutes("1 hour 10 minutes"))
        assertEquals(25, parseDurationMinutes("25 minutes"))
        assertEquals(120, parseDurationMinutes("2 hours"))
        assertEquals(95, parseDurationMinutes("1 heure 35 min"))
        assertEquals(40, parseDurationMinutes("PT40M"))
        assertEquals(90, parseDurationMinutes("PT1H30M"))
        assertNull(parseDurationMinutes(null))
        assertNull(parseDurationMinutes(""))
        assertNull(parseDurationMinutes("selon le four"))
    }

    @Test
    fun base_servings_fall_back_from_servings_to_yield_to_one() {
        assertEquals(4.0, baseServings(4.0, 0.0, ""), 0.0)
        assertEquals(6.0, baseServings(0.0, 6.0, "6 parts"), 0.0)
        assertEquals(1.0, baseServings(0.0, 0.0, "1 portion"), 0.0)
        assertEquals(8.0, baseServings(null, null, "pour 8 personnes"), 0.0)
        assertEquals(1.0, baseServings(null, null, null), 0.0)
    }

    @Test
    fun recipe_list_gives_cards_with_public_image_urls() {
        val json = obj(
            """{"page":1,"total":2,"items":[
              {"id":"a1","slug":"soupe","name":"Soupe","image":"x9","recipeServings":4,"recipeYieldQuantity":0,
               "recipeYield":"","totalTime":"35 minutes","prepTime":"10 minutes","cookTime":"25 minutes",
               "recipeCategory":[{"name":"Entrée"}]},
              {"id":"b2","slug":"tarte","name":"Tarte","image":null,"recipeServings":0,"recipeYieldQuantity":0,
               "recipeYield":"1 portion","totalTime":null,"prepTime":"15 minutes","cookTime":"30 minutes",
               "recipeCategory":[]}]}""",
        )
        val list = parseRecipeList(json, base)
        assertEquals(listOf("soupe", "tarte"), list.map { it.slug })
        val soupe = list[0]
        assertEquals("$base/api/media/recipes/a1/images/min-original.webp", soupe.imageUrl)
        assertEquals(35, soupe.totalMinutes)
        assertEquals(4.0, soupe.servings, 0.0)
        assertEquals(listOf("Entrée"), soupe.categories)
        val tarte = list[1]
        assertNull(tarte.imageUrl)
        assertEquals(45, tarte.totalMinutes) // no total: prep + cook
        assertEquals(1.0, tarte.servings, 0.0)
    }

    @Test
    fun recipe_detail_keeps_sections_structured_and_free_text_ingredients() {
        val json = obj(
            """{"id":"a1","slug":"soupe","name":"Soupe","image":"x9","recipeServings":4,"recipeYieldQuantity":0,
              "recipeYield":"","totalTime":"35 minutes","prepTime":"10 minutes","cookTime":"25 minutes","recipeCategory":[],
              "recipeIngredient":[
                {"title":"Bouillon","quantity":1.5,"unit":{"name":"L"},"food":{"name":"eau"},"note":"froide","display":"1,5 L eau froide","disableAmount":false},
                {"title":null,"quantity":2,"unit":null,"food":{"name":"carottes"},"note":"","display":"2 carottes"},
                {"title":null,"quantity":0,"unit":null,"food":{"name":"sel"},"note":"","display":"sel"},
                {"title":null,"quantity":0,"unit":null,"food":null,"note":"3 belles échalotes","display":"3 belles échalotes"}],
              "recipeInstructions":[{"title":"Cuire","text":"Cuire 20 minutes."},{"title":"","text":"Mixer.\nServir chaud."}]}""",
        )
        val r = parseRecipe(json, base)
        assertEquals("$base/api/media/recipes/a1/images/original.webp", r.imageUrl)
        assertEquals(10, r.prepMinutes)
        assertEquals(25, r.cookMinutes)
        assertEquals(35, r.totalMinutes)
        assertEquals(4.0, r.baseServings, 0.0)
        val (eau, carottes, sel, echalotes) = r.ingredients
        assertEquals("Bouillon", eau.section)
        assertTrue(eau.scalable)
        assertEquals(1.5, eau.quantity!!, 0.0)
        assertEquals("L", eau.unit)
        assertEquals("eau", eau.food)
        assertEquals("froide", eau.note)
        assertNull(carottes.section)
        assertTrue(carottes.scalable)
        assertFalse(sel.scalable) // food without amount: « to taste »
        assertFalse(echalotes.scalable) // free text, not parsed in Mealie
        assertEquals("3 belles échalotes", echalotes.display)
        assertEquals(listOf(Step("Cuire", "Cuire 20 minutes."), Step("", "Mixer.\nServir chaud.")), r.steps)
    }

    @Test
    fun todays_meal_plan_gives_the_recipe_slugs() {
        val arr = Json.parseToJsonElement(
            """[{"date":"2026-10-20","entryType":"dinner","title":"","recipe":{"slug":"soupe","name":"Soupe"}},
               {"date":"2026-10-20","entryType":"note","title":"Restes","recipe":null}]""",
        ).jsonArray
        assertEquals(listOf(MealPlanEntry("dinner", "soupe", "Soupe")), parseTodayMeals(arr))
    }
}
