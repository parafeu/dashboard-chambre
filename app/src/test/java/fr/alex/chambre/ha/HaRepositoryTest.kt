package fr.alex.chambre.ha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Minimal server-side Home Assistant: answers auth, records everything else. */
private class FakeHa(private val acceptToken: Boolean = true) : WebSocketListener() {
    val received = LinkedBlockingQueue<JsonObject>()
    @Volatile lateinit var socket: WebSocket

    override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        webSocket.send("""{"type":"auth_required","ha_version":"2026.10.1"}""")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val msg = Json.parseToJsonElement(text).jsonObject
        received.add(msg)
        if (msg["type"]?.jsonPrimitive?.content == "auth") {
            webSocket.send(if (acceptToken) """{"type":"auth_ok"}""" else """{"type":"auth_invalid","message":"Invalid access token"}""")
        }
    }

    fun next(): JsonObject = received.poll(5, TimeUnit.SECONDS) ?: error("no message received")
}

class HaRepositoryTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }

    @After fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun repo(backoff: List<Long> = listOf(10, 10)) = HaRepository(
        scope, client, flowOf(HaConfig(server.url("/").toString().trimEnd('/'), "secret")), backoff,
    )

    private fun enqueue(fake: FakeHa) = server.enqueue(MockResponse().withWebSocketUpgrade(fake))

    private fun FakeHa.handshake(): Pair<Int, Int> {
        assertEquals("secret", next()["access_token"]!!.jsonPrimitive.content)
        val sub = next()
        assertEquals("subscribe_entities", sub["type"]!!.jsonPrimitive.content)
        assertEquals(Entities.ALL, sub["entity_ids"]!!.jsonArray.map { it.jsonPrimitive.content })
        val fc = next()
        assertEquals("weather/subscribe_forecast", fc["type"]!!.jsonPrimitive.content)
        assertEquals(Entities.WEATHER, fc["entity_id"]!!.jsonPrimitive.content)
        assertEquals("hourly", fc["forecast_type"]!!.jsonPrimitive.content)
        val daily = next()
        assertEquals("daily", daily["forecast_type"]!!.jsonPrimitive.content)
        assertEquals(3, daily["id"]!!.jsonPrimitive.int)
        return sub["id"]!!.jsonPrimitive.int to fc["id"]!!.jsonPrimitive.int
    }

    @Test
    fun auth_then_subscribes() = runBlocking {
        val fake = FakeHa().also(::enqueue)
        val r = repo()
        val (subId, fcId) = fake.handshake()
        assertEquals(1, subId)
        assertEquals(2, fcId)
        withTimeout(5_000) { r.conn.first { it == ConnState.CONNECTED } }
        Unit
    }

    @Test
    fun entities_and_forecast_flows_update_from_events() = runBlocking {
        val fake = FakeHa().also(::enqueue)
        val r = repo()
        val (subId, fcId) = fake.handshake()
        fake.socket.send("""{"id":$subId,"type":"event","event":{"a":{"light.chambre_lumieres":{"s":"on","a":{"brightness":255}}}}}""")
        fake.socket.send("""{"id":$subId,"type":"event","event":{"c":{"light.chambre_lumieres":{"+":{"s":"off"},"-":{"a":["brightness"]}}}}}""")
        fake.socket.send("""{"id":$fcId,"type":"event","event":{"type":"hourly","forecast":[{"datetime":"2026-10-16T21:00:00+00:00","condition":"sunny","temperature":20.5}]}}""")
        val light = withTimeout(5_000) { r.entities.first { it[Entities.LIGHT]?.state == "off" } }.getValue(Entities.LIGHT)
        assertEquals(null, light.brightnessPct)
        val fc = withTimeout(5_000) { r.forecast.first { it.isNotEmpty() } }
        assertEquals(20.5, fc.single().temperature, 0.0)
        fake.socket.send("""{"id":3,"type":"event","event":{"type":"daily","forecast":[{"datetime":"2026-10-17T10:00:00+00:00","condition":"cloudy","temperature":18.3,"templow":13.5,"precipitation":2.6}]}}""")
        val daily = withTimeout(5_000) { r.forecastDaily.first { it.isNotEmpty() } }
        assertEquals(13.5, daily.single().templow!!, 0.0)
    }

    @Test
    fun call_returns_true_on_success() = runBlocking {
        val fake = FakeHa().also(::enqueue)
        val r = repo()
        fake.handshake()
        withTimeout(5_000) { r.conn.first { it == ConnState.CONNECTED } }
        val result = async(Dispatchers.IO) { r.call("light", "turn_on", Entities.LIGHT, JsonObject(mapOf("brightness_pct" to JsonPrimitive(40)))) }
        val msg = fake.next()
        assertEquals("call_service", msg["type"]!!.jsonPrimitive.content)
        assertEquals(4, msg["id"]!!.jsonPrimitive.int)
        assertEquals(40, msg["service_data"]!!.jsonObject["brightness_pct"]!!.jsonPrimitive.int)
        fake.socket.send("""{"id":4,"type":"result","success":true,"result":null}""")
        assertTrue(withTimeout(5_000) { result.await() })
    }

    @Test
    fun call_returns_false_and_emits_error_on_failure() = runBlocking {
        val fake = FakeHa().also(::enqueue)
        val r = repo()
        fake.handshake()
        withTimeout(5_000) { r.conn.first { it == ConnState.CONNECTED } }
        val error = async { withTimeout(5_000) { r.errors.first() } }
        delay(50) // let the error collector subscribe
        val result = async(Dispatchers.IO) { r.call("cover", "open_cover", Entities.COVER) }
        val id = fake.next()["id"]!!.jsonPrimitive.int
        fake.socket.send("""{"id":$id,"type":"result","success":false,"error":{"code":"x","message":"Volet bloqué"}}""")
        assertFalse(withTimeout(5_000) { result.await() })
        assertEquals("Volet bloqué", error.await())
    }

    @Test
    fun call_while_offline_returns_false() = runBlocking {
        val r = HaRepository(scope, client, flowOf(HaConfig("http://127.0.0.1:1", "secret")), listOf(10_000))
        assertFalse(r.call("light", "toggle", Entities.LIGHT))
    }

    @Test
    fun auth_invalid_sets_AUTH_FAILED_without_retry() = runBlocking {
        enqueue(FakeHa(acceptToken = false))
        val r = repo()
        withTimeout(5_000) { r.conn.first { it == ConnState.AUTH_FAILED } }
        delay(300)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun reconnects_and_resubscribes_after_server_close() = runBlocking { // RF3
        val first = FakeHa().also(::enqueue)
        val second = FakeHa().also(::enqueue)
        val r = repo()
        first.handshake()
        first.socket.close(1001, "restart")
        val (subId, _) = second.handshake()
        assertEquals(1, subId) // ids restart on each connection
        second.socket.send("""{"id":$subId,"type":"event","event":{"a":{"cover.volet_chambre":{"s":"open","a":{"current_position":43}}}}}""")
        val cover = withTimeout(5_000) { r.entities.first { it.containsKey(Entities.COVER) } }.getValue(Entities.COVER)
        assertEquals(43, cover.coverPosition)
        withTimeout(5_000) { r.conn.first { it == ConnState.CONNECTED } }
        Unit
    }

    @Test
    fun invalid_url_goes_offline_instead_of_crashing() = runBlocking { // C1
        val r = HaRepository(scope, client, flowOf(HaConfig("homeassistant.local:8123", "secret")), listOf(10_000))
        withTimeout(5_000) { r.conn.first { it == ConnState.OFFLINE } }
        Unit
    }

    @Test
    fun reconnect_retries_after_auth_failure() = runBlocking { // I1
        enqueue(FakeHa(acceptToken = false))
        val ok = FakeHa().also(::enqueue)
        val r = repo()
        withTimeout(5_000) { r.conn.first { it == ConnState.AUTH_FAILED } }
        r.reconnect()
        ok.handshake()
        withTimeout(5_000) { r.conn.first { it == ConnState.CONNECTED } }
        Unit
    }
}
