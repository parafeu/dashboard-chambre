package fr.alex.chambre.timers

import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.ha.TodoItem
import fr.alex.chambre.settings.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * The shared kitchen timers as this tablet sees them: Home Assistant's list when connected, the last known one
 * (kept on disk) otherwise. « Arrêter » always works: it hides the timer here at once and removes it from HA now,
 * or as soon as HA is back.
 */
class TimerController(
    private val scope: CoroutineScope,
    private val ha: HaApi,
    private val cache: TimerCache,
    private val room: () -> Room,
    private val clock: () -> Instant = Instant::now,
    private val tickMs: Long = 1_000,
) {
    private val source = MutableStateFlow<List<TodoItem>>(emptyList())
    private val stopped = MutableStateFlow<Set<String>>(emptySet())

    private val _now = MutableStateFlow(clock())
    /** Ticks every second: the countdowns and the ringing state read it. */
    val now: StateFlow<Instant> = _now.asStateFlow()

    val online: StateFlow<Boolean> = combine(ha.conn, ha.timerItems) { c, items -> c == ConnState.CONNECTED && items != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Ringing first, then running ones by end; timers stopped on this tablet are left out. */
    val timers: StateFlow<List<SharedTimer>> = combine(source, stopped, _now) { items, st, t ->
        sortTimers(items.mapNotNull(::parseTimer).filter { it.id !in st }, t)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val ringing: StateFlow<List<SharedTimer>> = combine(timers, _now) { ts, t -> ts.filter { it.isRinging(t) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _refusals = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** « Home Assistant est hors ligne » when a start, « +1 min » or × cannot reach HA (for a snackbar). */
    val refusals: SharedFlow<String> = _refusals.asSharedFlow()

    /** Stops whose `remove_item` was already sent on the current connection. */
    private val removeSent = mutableSetOf<String>()

    init {
        scope.launch {
            // Stops first: a timer stopped here must never show as ringing, even for one frame at start-up.
            stopped.value = cache.stopped()
            source.value = cache.lastKnown()
            ha.timerItems.collect { items ->
                if (items == null) {
                    removeSent.clear()
                    return@collect
                }
                source.value = items
                cache.saveLastKnown(items)
                val ids = items.map { it.uid }.toSet()
                val kept = stopped.value intersect ids
                if (kept != stopped.value) {
                    stopped.value = kept
                    cache.saveStopped(kept)
                }
                (kept - removeSent).forEach(::sendRemove)
            }
        }
        scope.launch {
            while (true) {
                _now.value = clock()
                delay(tickMs)
            }
        }
    }

    suspend fun start(seconds: Int, name: String, origin: String): Boolean {
        if (refusedOffline()) return false
        val label = name.trim().ifEmpty { DEFAULT_TIMER_NAME }
        val description = timerDescription(clock().plusSeconds(seconds.toLong()), seconds, origin, roomKey(room()))
        return ha.call("todo", "add_item", Entities.TIMERS_LIST, data("item" to label, "description" to description))
    }

    /** « + Nouvelle minuterie »: [minutes] from now, named [name] or « Minuterie ». */
    suspend fun startFree(minutes: Int, name: String): Boolean = start(minutes * 60, name, freeOrigin(room()))

    suspend fun addMinute(id: String): Boolean {
        if (refusedOffline()) return false
        val t = timers.value.firstOrNull { it.id == id } ?: return false
        val description = timerDescription(t.end.plusSeconds(60), t.durationSec + 60, t.origin, roomKey(room()))
        return ha.call("todo", "update_item", Entities.TIMERS_LIST, data("item" to id, "description" to description))
    }

    suspend fun delete(id: String): Boolean {
        if (refusedOffline()) return false
        return ha.call("todo", "remove_item", Entities.TIMERS_LIST, data("item" to id))
    }

    /** « Arrêter »: silent here at once, removed from HA now or on reconnection. */
    fun stop(id: String) {
        stopped.update { it + id }
        val ids = stopped.value
        scope.launch { cache.saveStopped(ids) }
        if (online.value) sendRemove(id)
    }

    private fun sendRemove(id: String) {
        removeSent += id
        // Another tablet may have removed it first: HA's « not found » must not show as an error. Any failure is
        // retried while the timer is still listed (a « not found » one is gone from the next list, so it stops there).
        scope.launch {
            if (ha.callQuietly("todo", "remove_item", Entities.TIMERS_LIST, data("item" to id))) return@launch
            removeSent -= id
            delay(STOP_RETRY_MS)
            if (id in stopped.value && id !in removeSent && online.value && source.value.any { it.uid == id }) sendRemove(id)
        }
    }

    private fun refusedOffline(): Boolean {
        if (online.value) return false
        _refusals.tryEmit(OFFLINE_MESSAGE)
        return true
    }

    private companion object {
        const val STOP_RETRY_MS = 5_000L
        const val OFFLINE_MESSAGE = "Home Assistant est hors ligne"
    }

    private fun data(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })
}
