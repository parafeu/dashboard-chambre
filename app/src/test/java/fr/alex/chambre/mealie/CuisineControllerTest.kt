package fr.alex.chambre.mealie

import fr.alex.chambre.settings.FormFactor
import fr.alex.chambre.settings.Room
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.settings.deviceFeatures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMealie : MealieApi {
    var list = listOf(card("dahl"), card("lasagnes"))
    var meals = listOf(MealPlanEntry("dinner", "dahl", "DAHL"))
    var failWith: String? = null
    var calls = 0
    override suspend fun recipes(): List<RecipeSummary> {
        calls++
        failWith?.let { throw MealieException(it) }
        return list
    }
    override suspend fun recipe(slug: String): Recipe {
        failWith?.let { throw MealieException(it) }
        return Recipe("id-$slug", slug, slug.uppercase(), null, 5, 20, 25, 1.0, emptyList(), listOf(Step("", "Cuire.")))
    }
    override suspend fun todayMeals(): List<MealPlanEntry> = meals
}

private fun card(slug: String) = RecipeSummary("id-$slug", slug, slug.uppercase(), null, 25, 1.0, emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class CuisineControllerTest {
    private val api = FakeMealie()
    private val cuisine = Settings(token = "t", room = Room.CUISINE, mealieUrl = "https://m.example", mealieToken = "k")
    private val settings = MutableStateFlow(cuisine)

    private fun TestScope.start() = CuisineController(
        backgroundScope, settings,
        apiFor = { s -> api.takeIf { s.mealieToken.isNotBlank() } },
        enabled = { deviceFeatures(it, FormFactor.TABLET).mealie },
    )
        .also { runCurrent() }

    @Test
    fun recipes_and_todays_meals_load_in_cuisine_mode() = runTest {
        val c = start()
        assertEquals(listOf("dahl", "lasagnes"), c.recipes.value.map { it.slug })
        assertEquals(listOf("dahl"), c.today.value.map { it.slug })
        assertNull(c.error.value)
    }

    @Test
    fun the_living_room_loads_recipes_for_its_cuisine_tab() = runTest {
        settings.value = cuisine.copy(room = Room.SALON)
        val c = start()
        assertEquals(1, api.calls)
        assertEquals(listOf("dahl", "lasagnes"), c.recipes.value.map { it.slug })
    }

    @Test
    fun the_bedroom_never_calls_mealie() = runTest {
        settings.value = cuisine.copy(room = Room.CHAMBRE)
        val c = start()
        advanceTimeBy(2 * 3_600_000L)
        runCurrent()
        assertEquals(0, api.calls)
        assertTrue(c.recipes.value.isEmpty())
        assertNull(c.error.value)
    }

    @Test
    fun missing_key_is_reported() = runTest {
        settings.value = cuisine.copy(mealieToken = "")
        val c = start()
        assertEquals("Renseigne l'adresse et la clé API Mealie dans les Réglages", c.error.value)
    }

    @Test
    fun a_failure_is_reported_and_the_last_list_kept() = runTest {
        val c = start()
        api.failWith = "Mealie injoignable (timeout)"
        c.refresh()
        runCurrent()
        assertEquals("Mealie injoignable (timeout)", c.error.value)
        assertEquals(2, c.recipes.value.size)
    }

    @Test
    fun the_list_is_read_again_every_30_minutes() = runTest {
        val c = start()
        api.list = listOf(card("soupe"))
        advanceTimeBy(30 * 60_000L + 1)
        runCurrent()
        assertEquals(listOf("soupe"), c.recipes.value.map { it.slug })
    }

    @Test
    fun opening_a_recipe_loads_it_and_closing_clears_it() = runTest {
        val c = start()
        c.open("dahl")
        runCurrent()
        assertEquals("dahl", (c.opened.value as OpenedRecipe.Ready).recipe.slug)
        c.close()
        assertNull(c.opened.value)
    }

    @Test
    fun a_recipe_that_cannot_load_says_why() = runTest {
        val c = start()
        api.failWith = "Clé API Mealie refusée"
        c.open("dahl")
        runCurrent()
        assertEquals(OpenedRecipe.Failed("dahl", "Clé API Mealie refusée"), c.opened.value)
    }
}
