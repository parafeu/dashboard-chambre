package fr.alex.chambre.widget

import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.HaRestClient
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.ha.coverPosition
import kotlinx.serialization.json.JsonObject
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

    suspend fun stored(room: WidgetRoom): Map<String, EntityState> = store.load(room)

    /** The expected state right after [action], from [base] (what the widget shows now, else the stored states). */
    suspend fun optimistic(room: WidgetRoom, action: WidgetAction, base: Map<String, EntityState>? = null): WidgetSnapshot =
        WidgetSnapshot(predict(base ?: store.load(room), room, action), stale = false, configured = settings().configured())

    /**
     * Sends [action], decided from [base] like the prediction the user sees. Returns null once sent (the state is read
     * again later, see [followUpDelaysSec]: right now HA may still report the old one), or the last real states,
     * dimmed, when HA refused or could not be reached.
     */
    suspend fun act(room: WidgetRoom, action: WidgetAction, base: Map<String, EntityState>? = null): WidgetSnapshot? {
        val s = settings()
        if (!s.configured()) return refresh(room)
        val known = base ?: store.load(room)
        try {
            val rest = api(s)
            val (domain, service, extra) = when (action) {
                WidgetAction.ToggleLight -> Triple("light", "toggle", null)
                is WidgetAction.StepLight ->
                    Triple("light", "turn_on", "brightness_pct" to stepBrightness(room.light?.let { known[it] }, action.delta))
                WidgetAction.CoverTapAction -> when (coverTap(known[room.cover])) {
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
        return null
    }

    private fun Settings.configured() = url.isNotBlank() && token.isNotBlank()
}

/**
 * The latest snapshot per room, observed by the widgets' compositions: while a Glance session is alive,
 * update() recomposes it without calling provideGlance again, so fresh states must come through here.
 */
object WidgetSnapshots {
    private val pending = java.util.concurrent.ConcurrentHashMap<WidgetRoom, PendingTap>()
    fun pending(room: WidgetRoom): PendingTap? = pending[room]
    fun setPending(room: WidgetRoom, tap: PendingTap?) { if (tap == null) pending.remove(room) else pending[room] = tap }

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

/**
 * What the widget shows right after a tap, before Home Assistant confirms: the expected new state, applied to the
 * last known ones. The follow-up reads (see [followUpDelaysSec]) replace it with the real state.
 */
fun predict(states: Map<String, EntityState>, room: WidgetRoom, action: WidgetAction): Map<String, EntityState> {
    fun EntityState.with(state: String, vararg attrs: Pair<String, Int>) =
        copy(state = state, attributes = JsonObject(attributes + attrs.associate { (k, v) -> k to JsonPrimitive(v) }))
    val light = room.light?.let { states[it] }
    val cover = states[room.cover]
    val changed: EntityState? = when (action) {
        WidgetAction.ToggleLight -> light?.with(if (lightIsOn(light)) "off" else "on")
        is WidgetAction.StepLight -> light?.with("on", "brightness" to (stepBrightness(light, action.delta) * 255 + 50) / 100)
        WidgetAction.CoverOpen -> cover?.with("opening")
        WidgetAction.CoverClose -> cover?.with("closing")
        WidgetAction.CoverStop -> cover?.with(if ((cover.coverPosition ?: 0) > 0) "open" else "closed")
        WidgetAction.CoverTapAction -> when (coverTap(cover)) {
            CoverTap.OPEN -> cover?.with("opening")
            CoverTap.CLOSE -> cover?.with("closing")
            CoverTap.STOP -> cover?.with(if ((cover.coverPosition ?: 0) > 0) "open" else "closed")
        }
    }
    return if (changed == null) states else states + (changed.entityId to changed)
}

/**
 * What to show after a follow-up read: an entity the tap changed keeps its predicted state while HA still reports
 * what it was before the tap (the device hasn't reported yet), except at the [last] check, where HA always wins.
 */
fun settle(
    before: Map<String, EntityState>,
    predicted: Map<String, EntityState>,
    read: Map<String, EntityState>,
    last: Boolean,
): Map<String, EntityState> {
    if (last) return read
    val pending = predicted.filter { (id, p) -> before[id] != p && read[id] == before[id] }
    return read + pending
}

/** The tap still being confirmed for a room: the states before it and the prediction shown. */
data class PendingTap(val before: Map<String, EntityState>, val predicted: Map<String, EntityState>)
