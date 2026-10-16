package fr.alex.chambre.ui

import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.ForecastHour
import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.ha.Swatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

private data class Call(val domain: String, val service: String, val entityId: String, val data: JsonObject)

private class FakeApi : HaApi {
    override val conn = MutableStateFlow(ConnState.CONNECTED)
    override val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    override val forecast = MutableStateFlow<List<ForecastHour>>(emptyList())
    override val errors = MutableSharedFlow<String>()
    val calls = mutableListOf<Call>()
    override suspend fun call(domain: String, service: String, entityId: String, data: JsonObject): Boolean {
        calls += Call(domain, service, entityId, data)
        return true
    }
}

private fun json(vararg pairs: Pair<String, Any>) = JsonObject(
    pairs.associate { (k, v) ->
        k to when (v) {
            is Number -> JsonPrimitive(v)
            is List<*> -> JsonArray(v.map { JsonPrimitive(it as Number) })
            else -> JsonPrimitive(v.toString())
        }
    },
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardControllerTest {

    private fun TestScope.setup(): Pair<FakeApi, DashboardController> {
        val api = FakeApi()
        val c = DashboardController(backgroundScope, api)
        runCurrent()
        return api to c
    }

    @Test
    fun formats_date_in_french() {
        assertEquals("Vendredi 16 octobre", formatDate(LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun run_scene_calls_script_and_remembers_it() = runTest {
        val (api, c) = setup()
        c.runScene("script.chambre_scene_lecture"); runCurrent()
        assertEquals(Call("script", "turn_on", "script.chambre_scene_lecture", json()), api.calls.single())
        assertEquals("script.chambre_scene_lecture", c.lastScene.value)
    }

    @Test
    fun brightness_zero_turns_light_off() = runTest {
        val (api, c) = setup()
        c.setBrightness(0, final = true); runCurrent()
        assertEquals(Call("light", "turn_off", Entities.LIGHT, json()), api.calls.single())
    }

    @Test
    fun brightness_sets_pct() = runTest {
        val (api, c) = setup()
        c.setBrightness(40, final = true); runCurrent()
        assertEquals(Call("light", "turn_on", Entities.LIGHT, json("brightness_pct" to 40)), api.calls.single())
    }

    @Test
    fun slider_drag_is_throttled_and_final_value_sent() = runTest {
        val (api, c) = setup()
        c.setBrightness(10, final = false); runCurrent()
        c.setBrightness(20, final = false); runCurrent()
        c.setBrightness(30, final = false); runCurrent()
        c.setBrightness(35, final = true); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(10, 35), api.calls.map { (it.data["brightness_pct"] as JsonPrimitive).content.toInt() })
    }

    @Test
    fun manual_light_change_clears_last_scene() = runTest {
        val (_, c) = setup()
        c.runScene("script.chambre_scene_nuit"); runCurrent()
        c.setKelvin(3000, final = true); runCurrent()
        assertNull(c.lastScene.value)
    }

    @Test
    fun light_turning_off_clears_last_scene() = runTest {
        val (api, c) = setup()
        api.entities.value = mapOf(Entities.LIGHT to EntityState(Entities.LIGHT, "on", json())); runCurrent()
        c.runScene("script.chambre_scene_coucher"); runCurrent()
        api.entities.value = mapOf(Entities.LIGHT to EntityState(Entities.LIGHT, "off", json())); runCurrent()
        assertNull(c.lastScene.value)
    }

    @Test
    fun swatches_and_color_calls() = runTest {
        val (api, c) = setup()
        c.applySwatch(Swatch.Kelvin(2700, 0)); runCurrent()
        c.applySwatch(Swatch.Rgb(255, 60, 45)); runCurrent()
        c.setHs(120f, 80f, final = true); runCurrent()
        assertEquals(
            listOf(
                json("color_temp_kelvin" to 2700, "transition" to 1),
                json("rgb_color" to listOf(255, 60, 45), "transition" to 1),
                json("hs_color" to listOf(120f, 80f)),
            ),
            api.calls.map { it.data },
        )
    }

    @Test
    fun cover_actions() = runTest {
        val (api, c) = setup()
        c.coverOpen(); c.coverStop(); c.coverClose(); c.coverHalf(); c.setCoverPosition(30)
        runCurrent()
        assertEquals(
            listOf(
                Call("cover", "open_cover", Entities.COVER, json()),
                Call("cover", "stop_cover", Entities.COVER, json()),
                Call("cover", "close_cover", Entities.COVER, json()),
                Call("script", "turn_on", Entities.HALF, json()),
                Call("cover", "set_cover_position", Entities.COVER, json("position" to 30)),
            ),
            api.calls,
        )
    }

    @Test
    fun reset_default_matches_remote_long_press() = runTest {
        val (api, c) = setup()
        c.runScene("script.chambre_scene_coucher"); runCurrent()
        c.resetDefault(); runCurrent()
        assertEquals(
            Call("light", "turn_on", Entities.LIGHT, json("brightness_pct" to 70, "color_temp_kelvin" to 2700, "transition" to 1)),
            api.calls.last(),
        )
        assertNull(c.lastScene.value)
    }
}
