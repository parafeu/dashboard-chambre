package fr.alex.chambre.ha

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MobileAppClientTest {
    private lateinit var server: MockWebServer
    private val api = MobileAppClient(OkHttpClient())
    private val info = DeviceInfo(deviceId = "dev-1", appVersion = "1.1.0", manufacturer = "samsung", model = "SM-T810", osVersion = "11")

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun body(): JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test
    fun register_sends_device_info_with_bearer_and_returns_webhook() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"webhook_id":"wh123","cloudhook_url":null,"remote_ui_url":null,"secret":null}"""))
        assertEquals("wh123", api.register(base(), "tok", info))
        val req = server.takeRequest()
        assertEquals("/api/mobile_app/registrations", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        val b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("dev-1", b["device_id"]!!.jsonPrimitive.content)
        assertEquals("fr.alex.chambre", b["app_id"]!!.jsonPrimitive.content)
        assertEquals("Tablette chambre", b["device_name"]!!.jsonPrimitive.content)
        assertEquals("Android", b["os_name"]!!.jsonPrimitive.content)
        assertEquals("SM-T810", b["model"]!!.jsonPrimitive.content)
        assertEquals(false, b["supports_encryption"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun register_failure_returns_null() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(api.register(base(), "bad", info))
    }

    @Test
    fun register_sensor_posts_definition() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        val def = SensorDef("battery_level", "sensor", "Batterie", "battery", "%", "measurement", "mdi:battery", JsonPrimitive(84))
        assertEquals(WebhookResult.OK, api.registerSensor(base(), "wh123", def))
        val req = server.takeRequest()
        assertEquals("/api/webhook/wh123", req.path)
        val b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("register_sensor", b["type"]!!.jsonPrimitive.content)
        val d = b["data"]!!.jsonObject
        assertEquals("battery_level", d["unique_id"]!!.jsonPrimitive.content)
        assertEquals("sensor", d["type"]!!.jsonPrimitive.content)
        assertEquals("battery", d["device_class"]!!.jsonPrimitive.content)
        assertEquals("%", d["unit_of_measurement"]!!.jsonPrimitive.content)
        assertEquals("measurement", d["state_class"]!!.jsonPrimitive.content)
        assertEquals("diagnostic", d["entity_category"]!!.jsonPrimitive.content)
        assertEquals(84, d["state"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun binary_sensor_definition_has_no_unit() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        api.registerSensor(base(), "wh", SensorDef("battery_charging", "binary_sensor", "En charge", "battery_charging", null, null, "mdi:power-plug", JsonPrimitive(true)))
        val d = body()["data"]!!.jsonObject
        assertNull(d["unit_of_measurement"])
        assertNull(d["state_class"])
    }

    @Test
    fun update_posts_states() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"battery_level":{"success":true}}"""))
        val r = api.updateStates(base(), "wh123", listOf(SensorState("battery_level", "sensor", JsonPrimitive(83), "mdi:battery-80")))
        assertEquals(WebhookResult.OK, r)
        val b = body()
        assertEquals("update_sensor_states", b["type"]!!.jsonPrimitive.content)
        val item = (b["data"] as JsonArray).single().jsonObject
        assertEquals("battery_level", item["unique_id"]!!.jsonPrimitive.content)
        assertEquals(83, item["state"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun gone_and_errors() = runBlocking { // RF3
        server.enqueue(MockResponse().setResponseCode(410))
        assertEquals(WebhookResult.GONE, api.updateStates(base(), "wh", emptyList()))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(WebhookResult.ERROR, api.updateStates(base(), "wh", emptyList()))
        assertEquals(WebhookResult.ERROR, api.updateStates("http://127.0.0.1:1", "wh", emptyList()))
    }

    @Test
    fun register_uses_the_device_name_given() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"webhook_id":"w"}"""))
        api.register(base(), "tok", info.copy(deviceName = "Tablette salon"))
        assertEquals("Tablette salon", body()["device_name"]!!.jsonPrimitive.content)
    }
}
