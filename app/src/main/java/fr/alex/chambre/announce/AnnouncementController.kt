package fr.alex.chambre.announce

import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.settings.Room
import fr.alex.chambre.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId

/** Where announcements already made / confirmed are kept. */
interface DoneStore {
    suspend fun done(): Set<String>
    suspend fun markDone(key: String, at: Instant)
}

interface Speaker {
    fun say(text: String)
}

/**
 * Living-room announcements: reads the work and alerts calendars every few minutes, listens for
 * finished timers, runs [AnnouncementEngine] every [tickMs] and speaks what it decides.
 */
class AnnouncementController(
    private val scope: CoroutineScope,
    private val api: HaApi,
    settings: Flow<Settings>,
    private val store: DoneStore,
    private val speaker: Speaker,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val wake: () -> Unit = {},
    private val tickMs: Long = 5_000,
    private val refreshMs: Long = 5 * 60_000,
) {
    private val empty = Tick(emptyList(), null, emptyList())
    private val _state = MutableStateFlow(empty)
    val state: StateFlow<Tick> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Why a calendar could not be read (shown in the settings), or null. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val lock = Mutex()
    private var engine: AnnouncementEngine? = null
    private val doneCache = HashSet<String>()

    init {
        scope.launch {
            settings
                .map { Triple(it.room, it.workCalendar, it.alertsCalendar) }
                .distinctUntilChanged()
                .collectLatest { (room, work, alerts) ->
                    lock.withLock { engine = null }
                    _state.value = empty
                    _error.value = null
                    if (room != Room.SALON) return@collectLatest
                    run(work, alerts)
                }
        }
    }

    private suspend fun run(work: String, alerts: String) = coroutineScope {
        val e = AnnouncementEngine(work, alerts, zone)
        lock.withLock {
            engine = e
            doneCache.clear()
            doneCache += store.done()
        }
        launch {
            api.events.collect { ev ->
                if (ev.eventType != "timer.finished") return@collect
                val id = (ev.data["entity_id"] as? JsonPrimitive)?.contentOrNull ?: return@collect
                val name = api.entities.value[id]?.attributes?.get("friendly_name")?.jsonPrimitive?.contentOrNull ?: id
                lock.withLock { e.timerFinished(name, now()) }
                tick()
            }
        }
        launch {
            while (true) {
                refresh(e, work, alerts)
                delay(refreshMs)
            }
        }
        while (true) {
            tick()
            delay(tickMs)
        }
    }

    private suspend fun refresh(e: AnnouncementEngine, work: String, alerts: String) {
        val t = now()
        val window = JsonObject(
            mapOf(
                "start_date_time" to JsonPrimitive(t.minusSeconds(3_600).atZone(zone).toOffsetDateTime().toString()),
                "end_date_time" to JsonPrimitive(t.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()),
            ),
        )
        val missing = mutableListOf<String>()
        val events = listOf(work, alerts).flatMap { cal ->
            val r = api.callForResponse("calendar", "get_events", cal, window)
            if (r == null) {
                missing += cal
                emptyList()
            } else {
                parseCalendarEvents(r, cal, zone)
            }
        }
        _error.value = if (missing.isEmpty()) null else "Calendrier illisible : " + missing.joinToString(", ")
        if (missing.size < 2) lock.withLock { e.setEvents(events) }
    }

    private suspend fun tick() {
        val t = lock.withLock {
            val e = engine ?: return
            val tick = e.tick(now(), doneCache)
            tick.speak.filterIsInstance<Announcement.Meeting>().forEach { m ->
                doneCache += m.key
                store.markDone(m.key, now())
            }
            tick
        }
        if (t.speak.isNotEmpty()) {
            wake()
            t.speak.forEach { speaker.say(speechFor(it)) }
        }
        _state.value = t
    }

    /** « C'est fait » / OK / ✕: confirmed alerts are remembered across restarts. */
    fun acknowledge(key: String) {
        scope.launch {
            lock.withLock {
                val e = engine ?: return@launch
                val isAlert = _state.value.alerts.any { it.key == key }
                e.acknowledge(key)
                if (isAlert) {
                    doneCache += key
                    store.markDone(key, now())
                }
            }
            tick()
        }
    }
}
