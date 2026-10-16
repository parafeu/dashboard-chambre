package fr.alex.chambre.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.math.roundToInt

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JsonObject,
)

data class ForecastHour(
    val time: Instant,
    val condition: String,
    val temperature: Double,
)

private fun EntityState.number(key: String): Double? =
    (attributes[key] as? JsonPrimitive)?.doubleOrNull

val EntityState.brightnessPct: Int?
    get() = number("brightness")?.let { (it / 255.0 * 100).roundToInt() }

val EntityState.colorTempK: Int?
    get() = number("color_temp_kelvin")?.roundToInt()

val EntityState.minK: Int
    get() = number("min_color_temp_kelvin")?.roundToInt() ?: 2000

val EntityState.maxK: Int
    get() = number("max_color_temp_kelvin")?.roundToInt() ?: 6500

val EntityState.coverPosition: Int?
    get() = number("current_position")?.roundToInt()

val EntityState.temperature: Double?
    get() = number("temperature")

val EntityState.isUnavailable: Boolean
    get() = state == "unavailable" || state == "unknown"

/**
 * Applies a compressed `subscribe_entities` diff: `+` carries a new state `s` and/or attributes
 * `a` to merge, `-` lists attribute keys to drop.
 */
fun EntityState.applyDiff(diff: JsonObject): EntityState {
    val plus = diff["+"] as? JsonObject
    val minus = diff["-"] as? JsonObject
    val attrs = attributes.toMutableMap()
    (plus?.get("a") as? JsonObject)?.let { attrs.putAll(it) }
    (minus?.get("a") as? JsonArray)?.forEach { attrs.remove(it.jsonPrimitive.content) }
    val newState = (plus?.get("s") as? JsonPrimitive)?.content ?: state
    return copy(state = newState, attributes = JsonObject(attrs))
}
