package fr.alex.chambre.widget

import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.HaRestClient
import fr.alex.chambre.settings.Settings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException

/** What a widget shows: the last states it got, whether they are old (HA unreachable), whether the app is set up. */
data class WidgetSnapshot(val states: Map<String, EntityState>, val stale: Boolean, val configured: Boolean)

sealed interface WidgetAction {
    data object ToggleLight : WidgetAction
    data class StepLight(val delta: Int) : WidgetAction
    data object CoverTapAction : WidgetAction
    data object CoverOpen : WidgetAction
    data object CoverStop : WidgetAction
    data object CoverClose : WidgetAction
}

/** The last states each widget got, so it still shows something when HA is out of reach. */
interface WidgetStore {
    suspend fun load(room: WidgetRoom): Map<String, EntityState>
    suspend fun save(room: WidgetRoom, states: Map<String, EntityState>)
}

/** Entities a widget reads: its light and shutter, plus the living-room climate sensors. */
fun widgetEntities(room: WidgetRoom): List<String> = listOfNotNull(room.light, room.cover) +
    if (room == WidgetRoom.SALON) listOf(Entities.SALON_TEMPERATURE, Entities.SALON_HUMIDITY) else emptyList()

class WidgetRepository(
    private val store: WidgetStore,
    private val settings: suspend () -> Settings,
    private val api: (Settings) -> HaRestClient,
) {
    suspend fun refresh(room: WidgetRoom): WidgetSnapshot {
        val s = settings()
        if (!s.configured()) return WidgetSnapshot(store.load(room), stale = true, configured = false)
        return try {
            val rest = api(s)
            val fresh = widgetEntities(room).mapNotNull { id -> rest.state(id)?.let { id to it } }.toMap()
            store.save(room, fresh)
            WidgetSnapshot(fresh, stale = false, configured = true)
        } catch (e: IOException) {
            WidgetSnapshot(store.load(room), stale = true, configured = true)
        }
    }

    suspend fun act(room: WidgetRoom, action: WidgetAction): WidgetSnapshot {
        val s = settings()
        if (!s.configured()) return refresh(room)
        try {
            val rest = api(s)
            val (domain, service, extra) = when (action) {
                WidgetAction.ToggleLight -> Triple("light", "toggle", null)
                is WidgetAction.StepLight -> {
                    // The current brightness, read now: the stored one may be minutes old.
                    val light = room.light?.let { rest.state(it) }
                    Triple("light", "turn_on", "brightness_pct" to stepBrightness(light, action.delta))
                }
                WidgetAction.CoverTapAction -> when (coverTap(rest.state(room.cover))) {
                    CoverTap.OPEN -> Triple("cover", "open_cover", null)
                    CoverTap.CLOSE -> Triple("cover", "close_cover", null)
                    CoverTap.STOP -> Triple("cover", "stop_cover", null)
                }
                WidgetAction.CoverOpen -> Triple("cover", "open_cover", null)
                WidgetAction.CoverStop -> Triple("cover", "stop_cover", null)
                WidgetAction.CoverClose -> Triple("cover", "close_cover", null)
            }
            val entity = if (domain == "light") room.light ?: return refresh(room) else room.cover
            rest.call(domain, service, buildJsonObject {
                put("entity_id", JsonPrimitive(entity))
                extra?.let { (k, v) -> put(k, JsonPrimitive(v)) }
            })
        } catch (e: IOException) {
            return WidgetSnapshot(store.load(room), stale = true, configured = true)
        }
        return refresh(room)
    }

    private fun Settings.configured() = url.isNotBlank() && token.isNotBlank()
}

/**
 * The latest snapshot per room, observed by the widgets' compositions: while a Glance session is alive,
 * update() recomposes it without calling provideGlance again, so fresh states must come through here.
 */
object WidgetSnapshots {
    private val flows = WidgetRoom.entries.associateWith { kotlinx.coroutines.flow.MutableStateFlow<WidgetSnapshot?>(null) }
    fun flow(room: WidgetRoom): kotlinx.coroutines.flow.StateFlow<WidgetSnapshot?> = flows.getValue(room)
    private val publishedAt = java.util.concurrent.ConcurrentHashMap<WidgetRoom, Long>()

    fun publish(room: WidgetRoom, snapshot: WidgetSnapshot, at: Long = System.currentTimeMillis()) {
        publishedAt[room] = at
        flows.getValue(room).value = snapshot
    }

    /** The snapshot published less than [maxAgeMs] ago (a tap or refresh just read HA), or null. */
    fun recent(room: WidgetRoom, now: Long = System.currentTimeMillis(), maxAgeMs: Long = 5_000): WidgetSnapshot? {
        val at = publishedAt[room] ?: return null
        return flows.getValue(room).value.takeIf { now - at < maxAgeMs }
    }
}

/**
 * When to read HA again after an action: right after the call HA often still reports the old state (a light
 * fading out, a shutter moving), so the widget is redrawn once the device has reported.
 */
fun followUpDelaysSec(action: WidgetAction): List<Long> = when (action) {
    WidgetAction.ToggleLight, is WidgetAction.StepLight -> listOf(2L, 6L)
    WidgetAction.CoverTapAction, WidgetAction.CoverOpen, WidgetAction.CoverStop, WidgetAction.CoverClose -> listOf(3L, 20L)
}
