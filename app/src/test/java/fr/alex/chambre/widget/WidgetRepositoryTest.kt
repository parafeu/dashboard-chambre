package fr.alex.chambre.widget

import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.HaRestClient
import fr.alex.chambre.settings.Settings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemoryStore : WidgetStore {
    val saved = mutableMapOf<WidgetRoom, Map<String, EntityState>>()
    override suspend fun load(room: WidgetRoom) = saved[room].orEmpty()
    override suspend fun save(room: WidgetRoom, states: Map<String, EntityState>) { saved[room] = states }
}

class WidgetRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryStore()
    private val posts = mutableListOf<Pair<String, JsonObject>>()
    private var states = mapOf(
        "light.chambre_lumieres" to """{"entity_id":"light.chambre_lumieres","state":"off","attributes":{}}""",
        "cover.volet_chambre" to """{"entity_id":"cover.volet_chambre","state":"open","attributes":{"current_position":100}}""",
    )
    private var failing = false
    private var failPosts = 0
    private var settings = Settings(token = "t")

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (failing) return MockResponse().setResponseCode(500)
                if (failPosts != 0 && request.method == "POST") return MockResponse().setResponseCode(failPosts)
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/states/") -> states[path.removePrefix("/api/states/")]?.let { MockResponse().setBody(it) }
                        ?: MockResponse().setResponseCode(404)
                    path.startsWith("/api/services/") -> {
                        posts += path.removePrefix("/api/services/") to Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
                        MockResponse().setBody("[]")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        settings = Settings(url = server.url("/").toString().trimEnd('/'), token = "t")
    }

    @After fun tearDown() = server.shutdown()

    private fun repo() = WidgetRepository(store, settings = { settings }, api = { s -> HaRestClient(s.url, s.token, OkHttpClient()) })

    @Test
    fun `refresh stores fresh states`() = runBlocking {
        val snap = repo().refresh(WidgetRoom.CHAMBRE)
        assertFalse(snap.stale)
        assertTrue(snap.configured)
        assertEquals("off", snap.states["light.chambre_lumieres"]?.state)
        assertEquals(snap.states, store.saved[WidgetRoom.CHAMBRE])
    }

    @Test
    fun `failed refresh keeps last states and marks stale`() = runBlocking {
        repo().refresh(WidgetRoom.CHAMBRE)
        failing = true
        val snap = repo().refresh(WidgetRoom.CHAMBRE)
        assertTrue(snap.stale)
        assertEquals("open", snap.states["cover.volet_chambre"]?.state)
    }

    @Test
    fun `unreachable server is stale too`() = runBlocking {
        settings = settings.copy(url = "http://127.0.0.1:1")
        val snap = repo().refresh(WidgetRoom.CUISINE)
        assertTrue(snap.stale)
        assertTrue(snap.configured)
    }

    @Test
    fun `blank settings give the unconfigured model`() = runBlocking {
        settings = Settings(url = settings.url, token = "")
        val snap = repo().refresh(WidgetRoom.SALON)
        assertFalse(snap.configured)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `minus on an off light sends turn_on at 10 percent`() = runBlocking {
        repo().act(WidgetRoom.CHAMBRE, WidgetAction.StepLight(-10))
        val (service, data) = posts.single()
        assertEquals("light/turn_on", service)
        assertEquals("light.chambre_lumieres", data["entity_id"]!!.jsonPrimitive.content)
        assertEquals(10, data["brightness_pct"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a refused command keeps the last states dimmed`() = runBlocking {
        repo().refresh(WidgetRoom.CHAMBRE)
        failPosts = 500
        val snap = repo().act(WidgetRoom.CHAMBRE, WidgetAction.ToggleLight)!!
        assertTrue(snap.stale)
        assertEquals("off", snap.states["light.chambre_lumieres"]?.state)
    }

    @Test
    fun `a wrong token is stale, not a crash`() = runBlocking {
        repo().refresh(WidgetRoom.CHAMBRE)
        failPosts = 401
        assertTrue(repo().act(WidgetRoom.CHAMBRE, WidgetAction.CoverClose)!!.stale)
    }

    @Test
    fun `the widget shows the expected state before HA answers`() = runBlocking {
        repo().refresh(WidgetRoom.CHAMBRE)
        val shown = repo().optimistic(WidgetRoom.CHAMBRE, WidgetAction.ToggleLight)
        assertEquals("on", shown.states["light.chambre_lumieres"]?.state)
        assertFalse(shown.stale)
        assertEquals("off", store.saved[WidgetRoom.CHAMBRE]!!["light.chambre_lumieres"]?.state) // the store keeps the truth
    }

    @Test
    fun `a sent command is not read back at once (HA may still report the old state)`() = runBlocking {
        repo().refresh(WidgetRoom.CHAMBRE)
        val before = server.requestCount
        org.junit.Assert.assertNull(repo().act(WidgetRoom.CHAMBRE, WidgetAction.ToggleLight))
        assertEquals(before + 1, server.requestCount) // the POST only
    }

    @Test
    fun `tapping an open shutter closes it`() = runBlocking {
        repo().refresh(WidgetRoom.CHAMBRE) // the tile the user tapped shows it open
        repo().act(WidgetRoom.CHAMBRE, WidgetAction.CoverTapAction)
        assertEquals("cover/close_cover", posts.single().first)
    }

    @Test
    fun `salon refresh also reads the climate sensors`() = runBlocking {
        repo().refresh(WidgetRoom.SALON)
        val paths = (1..server.requestCount).map { server.takeRequest().path }
        assertTrue("/api/states/sensor.salon_temperature" in paths)
        assertTrue("/api/states/sensor.salon_humidity" in paths)
    }
}
