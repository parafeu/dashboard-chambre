package fr.alex.chambre.mealie

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MealieClientTest {
    private val server = MockWebServer()
    private lateinit var client: MealieClient

    @Before
    fun setUp() {
        server.start()
        client = MealieClient(server.url("/").toString().trimEnd('/'), "k3y", OkHttpClient())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun item(slug: String) =
        """{"id":"id-$slug","slug":"$slug","name":"${slug.uppercase()}","image":null,"recipeServings":2,"totalTime":"10 minutes","recipeCategory":[]}"""

    @Test
    fun recipes_are_read_page_by_page_with_the_api_key() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"page":1,"total_pages":2,"items":[${item("a")},${item("b")}]}"""))
        server.enqueue(MockResponse().setBody("""{"page":2,"total_pages":2,"items":[${item("c")}]}"""))
        assertEquals(listOf("a", "b", "c"), client.recipes().map { it.slug })
        val first = server.takeRequest()
        assertEquals("Bearer k3y", first.getHeader("Authorization"))
        assertTrue(first.path!!, first.path!!.startsWith("/api/recipes?page=1&perPage="))
        assertTrue(server.takeRequest().path!!.startsWith("/api/recipes?page=2&"))
    }

    @Test
    fun a_recipe_and_todays_meals_are_read() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"id-a","slug":"a","name":"A","recipeIngredient":[],"recipeInstructions":[{"title":"","text":"Cuire."}]}"""))
        server.enqueue(MockResponse().setBody("""[{"entryType":"dinner","recipe":{"slug":"a","name":"A"}}]"""))
        assertEquals(listOf(Step("", "Cuire.")), client.recipe("a").steps)
        assertEquals(listOf(MealPlanEntry("dinner", "a", "A")), client.todayMeals())
        assertEquals("/api/recipes/a", server.takeRequest().path)
        assertEquals("/api/households/mealplans/today", server.takeRequest().path)
    }

    @Test
    fun a_refused_key_says_so() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Not authenticated"}"""))
        try {
            client.recipes()
            fail("expected an error")
        } catch (e: MealieException) {
            assertEquals("Clé API Mealie refusée", e.message)
        }
    }

    @Test
    fun an_unreachable_server_says_so() = runBlocking {
        server.shutdown()
        try {
            client.recipes()
            fail("expected an error")
        } catch (e: MealieException) {
            assertTrue(e.message!!, e.message!!.startsWith("Mealie injoignable"))
        }
    }
}
