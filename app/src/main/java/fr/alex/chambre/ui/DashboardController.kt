package fr.alex.chambre.ui

import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.ha.Swatch
import fr.alex.chambre.util.Throttle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** « Jeudi 16 octobre » */
fun formatDate(date: LocalDate): String =
    DATE_FORMAT.format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** Turns UI gestures into Home Assistant service calls. */
class DashboardController(
    private val scope: CoroutineScope,
    private val api: HaApi,
    throttleMs: Long = 150,
) {
    private val _lastScene = MutableStateFlow<String?>(null)
    val lastScene: StateFlow<String?> = _lastScene.asStateFlow()

    private val brightness = Throttle<Int>(scope, throttleMs) { pct ->
        if (pct <= 0) api.call("light", "turn_off", Entities.LIGHT)
        else api.call("light", "turn_on", Entities.LIGHT, data("brightness_pct" to JsonPrimitive(pct)))
    }
    private val kelvin = Throttle<Int>(scope, throttleMs) { k ->
        api.call("light", "turn_on", Entities.LIGHT, data("color_temp_kelvin" to JsonPrimitive(k)))
    }
    private val hs = Throttle<Pair<Float, Float>>(scope, throttleMs) { (h, s) ->
        api.call("light", "turn_on", Entities.LIGHT, data("hs_color" to JsonArray(listOf(JsonPrimitive(h), JsonPrimitive(s)))))
    }

    init {
        scope.launch {
            api.entities.map { it[Entities.LIGHT]?.state }.distinctUntilChanged().collect { state ->
                if (state == "off") _lastScene.value = null
            }
        }
    }

    fun runScene(entityId: String) {
        _lastScene.value = entityId
        fire("script", "turn_on", entityId)
    }

    fun toggleLight() {
        _lastScene.value = null
        fire("light", "toggle", Entities.LIGHT)
    }

    fun setBrightness(pct: Int, final: Boolean) {
        _lastScene.value = null
        if (final) brightness.flush(pct) else brightness.submit(pct)
    }

    fun setKelvin(k: Int, final: Boolean) {
        _lastScene.value = null
        if (final) kelvin.flush(k) else kelvin.submit(k)
    }

    fun setHs(hue: Float, saturation: Float, final: Boolean) {
        _lastScene.value = null
        val v = hue to saturation
        if (final) hs.flush(v) else hs.submit(v)
    }

    fun applySwatch(swatch: Swatch) {
        _lastScene.value = null
        val color = when (swatch) {
            is Swatch.Kelvin -> "color_temp_kelvin" to JsonPrimitive(swatch.kelvin)
            is Swatch.Rgb -> "rgb_color" to JsonArray(listOf(swatch.r, swatch.g, swatch.b).map(::JsonPrimitive))
        }
        fire("light", "turn_on", Entities.LIGHT, data(color, "transition" to JsonPrimitive(1)))
    }

    fun coverOpen() = fire("cover", "open_cover", Entities.COVER)
    fun coverStop() = fire("cover", "stop_cover", Entities.COVER)
    fun coverClose() = fire("cover", "close_cover", Entities.COVER)
    fun coverHalf() = fire("script", "turn_on", Entities.HALF)
    fun setCoverPosition(position: Int) =
        fire("cover", "set_cover_position", Entities.COVER, data("position" to JsonPrimitive(position)))

    private fun fire(domain: String, service: String, entityId: String, data: JsonObject = JsonObject(emptyMap())) {
        scope.launch { api.call(domain, service, entityId, data) }
    }

    private fun data(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) = JsonObject(mapOf(*pairs))
}
