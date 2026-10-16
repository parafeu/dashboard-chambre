package fr.alex.chambre.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HaMessagesTest {

    @Test
    fun parses_auth_messages() {
        assertEquals(Incoming.AuthRequired, HaProtocol.parse("""{"type":"auth_required","ha_version":"2026.10.1"}"""))
        assertEquals(Incoming.AuthOk, HaProtocol.parse("""{"type":"auth_ok","ha_version":"2026.10.1"}"""))
        assertEquals(Incoming.AuthInvalid("Invalid access token"), HaProtocol.parse("""{"type":"auth_invalid","message":"Invalid access token"}"""))
    }

    @Test
    fun parses_results() {
        assertEquals(Incoming.Result(3, true, null), HaProtocol.parse("""{"id":3,"type":"result","success":true,"result":null}"""))
        assertEquals(
            Incoming.Result(4, false, "Entity not found"),
            HaProtocol.parse("""{"id":4,"type":"result","success":false,"error":{"code":"not_found","message":"Entity not found"}}"""),
        )
    }

    @Test
    fun parses_entity_added_event() {
        val msg = HaProtocol.parse(
            """{"id":1,"type":"event","event":{"a":{"light.chambre_lumieres":{"s":"on","a":{"brightness":133,"color_temp_kelvin":2702,"min_color_temp_kelvin":1801,"max_color_temp_kelvin":6535},"c":"x","lc":1.0,"lu":1.0}}}}""",
        ) as Incoming.EntitiesEvent
        val light = msg.added.getValue("light.chambre_lumieres")
        assertEquals("on", light.state)
        assertEquals(52, light.brightnessPct)
        assertEquals(2702, light.colorTempK)
        assertEquals(1801, light.minK)
        assertEquals(6535, light.maxK)
    }

    @Test
    fun diff_changes_state_and_adds_attribute() {
        val before = EntityState("light.x", "off", JsonObject(emptyMap()))
        val diff = Json.parseToJsonElement("""{"+":{"s":"on","a":{"brightness":255}}}""").jsonObject
        val after = before.applyDiff(diff)
        assertEquals("on", after.state)
        assertEquals(100, after.brightnessPct)
    }

    @Test
    fun diff_removing_brightness_gives_null_pct() { // RF1
        val before = EntityState("light.x", "on", buildJsonObject { put("brightness", 133); put("color_temp_kelvin", 2702) })
        val diff = Json.parseToJsonElement("""{"+":{"s":"off"},"-":{"a":["brightness","color_temp_kelvin"]}}""").jsonObject
        val after = before.applyDiff(diff)
        assertEquals("off", after.state)
        assertNull(after.brightnessPct)
        assertNull(after.colorTempK)
    }

    @Test
    fun parses_changed_and_removed_events() {
        val msg = HaProtocol.parse("""{"id":1,"type":"event","event":{"c":{"cover.volet_chambre":{"+":{"a":{"current_position":20}}}},"r":["light.old"]}}""") as Incoming.EntitiesEvent
        assertTrue(msg.changed.containsKey("cover.volet_chambre"))
        assertEquals(listOf("light.old"), msg.removed)
    }

    @Test
    fun parses_forecast_event() {
        val msg = HaProtocol.parse(
            """{"id":2,"type":"event","event":{"type":"hourly","forecast":[
              {"datetime":"2026-10-16T21:00:00+00:00","condition":"clear-night","temperature":16.1},
              {"datetime":"2026-10-16T22:00:00+00:00","condition":"cloudy","temperature":15.0}]}}""",
        ) as Incoming.ForecastEvent
        assertEquals(2, msg.id)
        assertEquals(
            listOf(
                ForecastHour(Instant.parse("2026-10-16T21:00:00Z"), "clear-night", 16.1),
                ForecastHour(Instant.parse("2026-10-16T22:00:00Z"), "cloudy", 15.0),
            ),
            msg.hours,
        )
    }

    @Test
    fun auth_trims_token() { // RF5
        val json = Json.parseToJsonElement(HaProtocol.auth("  tok\n")).jsonObject
        assertEquals(JsonPrimitive("auth"), json["type"])
        assertEquals(JsonPrimitive("tok"), json["access_token"])
    }

    @Test
    fun builds_subscriptions() {
        assertEquals(
            Json.parseToJsonElement("""{"id":1,"type":"subscribe_entities","entity_ids":["a.b","c.d"]}"""),
            Json.parseToJsonElement(HaProtocol.subscribeEntities(1, listOf("a.b", "c.d"))),
        )
        assertEquals(
            Json.parseToJsonElement("""{"id":2,"type":"weather/subscribe_forecast","entity_id":"weather.forecast_maison","forecast_type":"hourly"}"""),
            Json.parseToJsonElement(HaProtocol.subscribeForecast(2, "weather.forecast_maison")),
        )
    }

    @Test
    fun builds_call_service() {
        val data = buildJsonObject { put("brightness_pct", 40) }
        assertEquals(
            Json.parseToJsonElement("""{"id":7,"type":"call_service","domain":"light","service":"turn_on","target":{"entity_id":"light.chambre_lumieres"},"service_data":{"brightness_pct":40}}"""),
            Json.parseToJsonElement(HaProtocol.callService(7, "light", "turn_on", "light.chambre_lumieres", data)),
        )
    }

    @Test
    fun missing_attributes_are_null() { // RF2, RF4
        val e = EntityState("x.y", "open", JsonObject(emptyMap()))
        assertNull(e.colorTempK)
        assertNull(e.coverPosition)
        assertEquals(2000, e.minK)
        assertEquals(6500, e.maxK)
    }

    @Test
    fun unavailable_states() {
        assertTrue(EntityState("x.y", "unavailable", JsonObject(emptyMap())).isUnavailable)
        assertTrue(EntityState("x.y", "unknown", JsonObject(emptyMap())).isUnavailable)
        assertFalse(EntityState("x.y", "on", JsonObject(emptyMap())).isUnavailable)
    }

    @Test
    fun unknown_messages_are_tolerated() {
        assertEquals(Incoming.Unknown, HaProtocol.parse("""{"type":"pong","id":9}"""))
        assertEquals(Incoming.Unknown, HaProtocol.parse("not json"))
    }

    @Test
    fun reads_hs_color() {
        val e = HaProtocol.parse("""{"id":1,"type":"event","event":{"a":{"light.x":{"s":"on","a":{"hs_color":[28.4,65.7]}}}}}""") as Incoming.EntitiesEvent
        assertEquals(28.4f to 65.7f, e.added.getValue("light.x").hsColor)
        assertNull(EntityState("x.y", "off", JsonObject(emptyMap())).hsColor)
    }
}
