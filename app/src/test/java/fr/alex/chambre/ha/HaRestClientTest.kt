package fr.alex.chambre.ha

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HaRestClientTest {
    private val server = MockWebServer()
    private lateinit var rest: HaRestClient

    @Before fun setUp() {
        server.start()
        rest = HaRestClient(server.url("/").toString().trimEnd('/'), "t", OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `state reads one entity`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"entity_id":"light.x","state":"on","attributes":{"brightness":102}}"""))
        val s = rest.state("light.x")!!
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/states/light.x", req.path)
        assertEquals("Bearer t", req.getHeader("Authorization"))
        assertEquals("on", s.state)
        assertEquals(40, s.brightnessPct)
    }

    @Test
    fun `unknown entity is null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(rest.state("light.nope"))
    }

    @Test
    fun `call posts the service data`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(rest.call("cover", "open_cover", buildJsonObject { put("entity_id", JsonPrimitive("cover.x")) }))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/services/cover/open_cover", req.path)
        assertEquals("cover.x", kotlinx.serialization.json.Json.parseToJsonElement(req.body.readUtf8()).let { (it as kotlinx.serialization.json.JsonObject)["entity_id"]!!.jsonPrimitive.content })
    }

    @Test(expected = IOException::class)
    fun `malformed url is an io error`(): Unit = runBlocking {
        HaRestClient("http://my ha", "t", OkHttpClient()).state("light.x")
    }

    @Test(expected = IOException::class)
    fun `server error throws`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        rest.call("light", "toggle", buildJsonObject { })
    }
}
