package fr.alex.chambre.ha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

enum class ConnState { CONNECTING, CONNECTED, OFFLINE, AUTH_FAILED }

data class HaConfig(
    val url: String,
    val token: String,
    val entities: List<String> = Entities.ALL,
    /** Also subscribe to HA's timer.finished events (living-room announcements). */
    val timerEvents: Boolean = false,
)

/** What the UI needs from Home Assistant. */
interface HaApi {
    val conn: StateFlow<ConnState>
    val entities: StateFlow<Map<String, EntityState>>
    val forecast: StateFlow<List<ForecastEntry>>
    val forecastDaily: StateFlow<List<ForecastEntry>>
    val errors: SharedFlow<String>

    /** HA bus events this connection subscribed to (timer.finished in living-room mode). */
    val events: SharedFlow<Incoming.HaEvent>

    suspend fun call(
        domain: String,
        service: String,
        entityId: String,
        data: JsonObject = JsonObject(emptyMap()),
    ): Boolean
}

/**
 * Keeps a WebSocket session to Home Assistant alive (reconnecting with [backoffMs]), subscribes
 * to [Entities.ALL] and the hourly forecast, and exposes their state as flows.
 */
class HaRepository(
    scope: CoroutineScope,
    private val client: OkHttpClient,
    /** null = paused (phone in the background): no connection, the last states are kept. */
    config: Flow<HaConfig?>,
    private val backoffMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000),
) : HaApi {
    private val _conn = MutableStateFlow(ConnState.CONNECTING)
    override val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    override val entities: StateFlow<Map<String, EntityState>> = _entities.asStateFlow()

    private val _forecast = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val forecast: StateFlow<List<ForecastEntry>> = _forecast.asStateFlow()

    private val _forecastDaily = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val forecastDaily: StateFlow<List<ForecastEntry>> = _forecastDaily.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _events = MutableSharedFlow<Incoming.HaEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<Incoming.HaEvent> = _events.asSharedFlow()

    @Volatile
    private var current: HaConnection? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Incoming.Result>>()

    private val retries = MutableStateFlow(0)

    init {
        scope.launch { config.combine(retries) { cfg, _ -> cfg }.collectLatest { run(it) } }
    }

    private suspend fun run(cfg: HaConfig?) {
        if (cfg == null) {
            _conn.value = ConnState.CONNECTING
            return
        }
        if (cfg.token.isBlank()) {
            _conn.value = ConnState.OFFLINE
            return
        }
        var attempt = 0
        while (true) {
            _conn.value = ConnState.CONNECTING
            if (session(cfg) { attempt = 0 } == ConnState.AUTH_FAILED) {
                _conn.value = ConnState.AUTH_FAILED
                return
            }
            _conn.value = ConnState.OFFLINE
            delay(backoffMs[minOf(attempt, backoffMs.lastIndex)])
            attempt++
        }
    }

    /** Runs one connection until it closes; returns AUTH_FAILED or OFFLINE. */
    private suspend fun session(cfg: HaConfig, onConnected: () -> Unit): ConnState {
        val c = try {
            HaConnection(client, cfg.url)
        } catch (e: IllegalArgumentException) {
            return ConnState.OFFLINE // malformed URL: stay offline instead of crashing the launcher
        }
        var subId = -1
        var forecastId = -1
        var dailyId = -1
        var timerEventsId = -1
        try {
            for (msg in c.incoming) {
                when (msg) {
                    Incoming.AuthRequired -> c.send(HaProtocol.auth(cfg.token))
                    Incoming.AuthOk -> {
                        subId = c.nextId()
                        c.send(HaProtocol.subscribeEntities(subId, cfg.entities))
                        forecastId = c.nextId()
                        c.send(HaProtocol.subscribeForecast(forecastId, Entities.WEATHER))
                        dailyId = c.nextId()
                        c.send(HaProtocol.subscribeForecast(dailyId, Entities.WEATHER, "daily"))
                        if (cfg.timerEvents) {
                            timerEventsId = c.nextId()
                            c.send(HaProtocol.subscribeEvents(timerEventsId, "timer.finished"))
                        }
                        current = c
                        _conn.value = ConnState.CONNECTED
                        onConnected()
                    }
                    is Incoming.AuthInvalid -> return ConnState.AUTH_FAILED
                    is Incoming.Result -> pending.remove(msg.id)?.complete(msg)
                    is Incoming.EntitiesEvent -> if (msg.id == subId) applyEntities(msg)
                    is Incoming.ForecastEvent -> when (msg.id) {
                        forecastId -> _forecast.value = msg.hours
                        dailyId -> _forecastDaily.value = msg.hours
                    }
                    is Incoming.HaEvent -> if (msg.id == timerEventsId) _events.tryEmit(msg)
                    Incoming.Unknown -> Unit
                }
            }
            return ConnState.OFFLINE
        } finally {
            current = null
            c.close()
            pending.keys.toList().forEach { id -> pending.remove(id)?.complete(Incoming.Result(id, false, OFFLINE_MESSAGE)) }
        }
    }

    private fun applyEntities(e: Incoming.EntitiesEvent) {
        _entities.update { old ->
            val map = old.toMutableMap()
            map.putAll(e.added)
            e.changed.forEach { (id, diff) -> map[id]?.let { map[id] = it.applyDiff(diff) } }
            e.removed.forEach { map.remove(it) }
            map
        }
    }

    /** Forces a new connection attempt (e.g. after saving settings, even when unchanged). */
    fun reconnect() {
        retries.value++
    }

    override suspend fun call(domain: String, service: String, entityId: String, data: JsonObject): Boolean {
        val result = request(reportErrors = true) { id -> HaProtocol.callService(id, domain, service, entityId, data) }
        return result?.success == true
    }

    /** Sends one request and waits for its result; null when offline or timed out. */
    private suspend fun request(reportErrors: Boolean, build: (Int) -> String): Incoming.Result? {
        val c = current ?: run {
            if (reportErrors) _errors.tryEmit(OFFLINE_MESSAGE)
            return null
        }
        val id = c.nextId()
        val deferred = CompletableDeferred<Incoming.Result>()
        pending[id] = deferred
        if (!c.send(build(id))) {
            pending.remove(id)
            if (reportErrors) _errors.tryEmit(OFFLINE_MESSAGE)
            return null
        }
        val result = withTimeoutOrNull(CALL_TIMEOUT_MS) { deferred.await() }
        pending.remove(id)
        if (result?.success != true && reportErrors) _errors.tryEmit(result?.errorMessage ?: "Pas de réponse de Home Assistant")
        return result
    }

    private companion object {
        const val CALL_TIMEOUT_MS = 10_000L
        const val OFFLINE_MESSAGE = "Home Assistant est hors ligne"
    }
}
