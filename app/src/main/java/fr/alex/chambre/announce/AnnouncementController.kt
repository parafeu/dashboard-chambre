package fr.alex.chambre.announce

import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.settings.Room
import fr.alex.chambre.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * Living-room announcements: reads the tablet’s work and alerts calendars every minute, listens for
 * finished kitchen timers (Home Assistant), runs [AnnouncementEngine] every [tickMs] and speaks what it decides.
 */
class AnnouncementController(
    private val scope: CoroutineScope,
    private val api: HaApi,
    private val source: CalendarSource,
    settings: Flow<Settings>,
    private val store: DoneStore,
    private val speaker: Speaker,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val wake: () -> Unit = {},
    private val tickMs: Long = 5_000,
    private val refreshMs: Long = 60_000,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val empty = Tick(emptyList(), null, emptyList())
    private val _state = MutableStateFlow(empty)
    val state: StateFlow<Tick> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Why a calendar could not be read (shown in the settings), or null. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _upcoming = MutableStateFlow<List<CalendarEvent>>(emptyList())
    /** Today's remaining work meetings and alerts, in order (the « Aujourd'hui » panel). */
    val upcoming: StateFlow<List<CalendarEvent>> = _upcoming.asStateFlow()

    private val lock = Mutex()
    private val poke = Channel<Unit>(Channel.CONFLATED)
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
                    _upcoming.value = emptyList()
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
                if (id !in Entities.KITCHEN_TIMERS) return@collect // other HA timers (automations) stay silent
                val name = api.entities.value[id]?.attributes?.get("friendly_name")?.jsonPrimitive?.contentOrNull ?: id
                lock.withLock { e.timerFinished(name, now()) }
                tick()
            }
        }
        launch {
            // The tablet's calendar database is local: read it every refreshMs, with or without HA.
            while (true) {
                refresh(e, work, alerts)
                withTimeoutOrNull(refreshMs) { poke.receive() }
            }
        }
        while (true) {
            tick()
            delay(tickMs)
        }
    }

    private suspend fun refresh(e: AnnouncementEngine, work: String, alerts: String) {
        val chosen = listOf("Travail" to work, "Alertes" to alerts)
        val unchosen = chosen.filter { it.second.isBlank() }.map { it.first }
        if (unchosen.isNotEmpty()) {
            _error.value = "Choisir " + (if (unchosen.size == 2) "les agendas Travail et Alertes" else "l'agenda " + unchosen.single())
            return
        }
        val t = now()
        val from = t.minusSeconds(3_600) // late alerts: shown up to one hour after their start
        val to = t.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val reads = withContext(io) { chosen.map { (name, id) -> name to source.read(id, from, to) } }
        val events = reads.flatMap { (_, r) -> (r as? CalendarRead.Events)?.events.orEmpty() }.filter { it.isToday(t, zone) }
        _error.value = when {
            reads.any { it.second is CalendarRead.NoPermission } -> "Accès aux agendas non autorisé"
            else -> reads.filter { it.second is CalendarRead.NotFound }.map { it.first }.takeIf { it.isNotEmpty() }
                ?.let { names -> names.joinToString(" et ", prefix = if (names.size == 1) "Agenda " else "Agendas ") + " introuvable" + (if (names.size == 1) "" else "s") + " sur la tablette" }
        }
        if (reads.any { it.second is CalendarRead.Events }) {
            lock.withLock { e.setEvents(events) }
            _upcoming.value = events.filter { it.allDay || !it.start.isBefore(t) }.sortedBy { it.start }
        }
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

    /** Reads the calendars again now (e.g. right after the calendar permission is granted). */
    fun refreshNow() {
        poke.trySend(Unit)
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
