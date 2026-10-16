package fr.alex.chambre.battery

import fr.alex.chambre.ha.DeviceInfo
import fr.alex.chambre.ha.HaConfig
import fr.alex.chambre.ha.MobileAppApi
import fr.alex.chambre.ha.SensorDef
import fr.alex.chambre.ha.SensorState
import fr.alex.chambre.ha.WebhookResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/** Where the Mobile App registration is persisted. */
interface RegistrationStore {
    suspend fun deviceId(): String
    suspend fun webhookId(): String?
    suspend fun saveWebhookId(id: String?)
}

sealed interface ReportStatus {
    data object Idle : ReportStatus
    data class Sent(val atMs: Long) : ReportStatus
    data class Error(val message: String) : ReportStatus
}

/**
 * Publishes the tablet battery to Home Assistant as Mobile App sensors: immediately on the first
 * state and on (un)plugging, otherwise at most every 5 min when the level or temperature moves,
 * and every 30 min regardless.
 */
class BatteryReporter(
    scope: CoroutineScope,
    private val battery: StateFlow<BatteryState?>,
    config: kotlinx.coroutines.flow.Flow<HaConfig>,
    private val api: MobileAppApi,
    private val store: RegistrationStore,
    private val deviceInfo: (deviceId: String) -> DeviceInfo,
    private val now: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 60_000,
) {
    private val _status = MutableStateFlow<ReportStatus>(ReportStatus.Idle)
    val status: StateFlow<ReportStatus> = _status.asStateFlow()

    private var lastSent: BatteryState? = null
    private var lastSentAt = 0L
    private var sensorsRegisteredFor: String? = null

    init {
        scope.launch {
            config.collectLatest { cfg ->
                lastSent = null
                if (cfg.token.isBlank()) {
                    _status.value = ReportStatus.Idle
                    return@collectLatest
                }
                val ticks = flow { while (true) { delay(tickMs); emit(Unit) } }
                merge(battery.map { }, ticks).collect { maybeSend(cfg) }
            }
        }
    }

    private suspend fun maybeSend(cfg: HaConfig) {
        val cur = battery.value ?: return
        if (!shouldSend(lastSent, cur, now() - lastSentAt)) return
        if (send(cfg, cur, allowRetry = true)) {
            lastSent = cur
            lastSentAt = now()
            _status.value = ReportStatus.Sent(now())
        }
    }

    private fun shouldSend(prev: BatteryState?, cur: BatteryState, sinceMs: Long): Boolean {
        if (prev == null || prev.charging != cur.charging) return true
        if (sinceMs >= RESEND_MS) return true
        val tempMoved = (prev.tempC == null) != (cur.tempC == null) ||
            (prev.tempC != null && cur.tempC != null && abs(prev.tempC - cur.tempC) >= 1.0)
        return (prev.levelPct != cur.levelPct || tempMoved) && sinceMs >= MIN_INTERVAL_MS
    }

    private suspend fun send(cfg: HaConfig, cur: BatteryState, allowRetry: Boolean): Boolean {
        val webhook = store.webhookId() ?: api.register(cfg.url, cfg.token, deviceInfo(store.deviceId()))
            ?.also { store.saveWebhookId(it) }
        if (webhook == null) {
            _status.value = ReportStatus.Error("Enregistrement refusé par Home Assistant")
            return false
        }
        if (sensorsRegisteredFor != webhook) {
            for (def in definitions(cur)) {
                if (api.registerSensor(cfg.url, webhook, def) != WebhookResult.OK) {
                    _status.value = ReportStatus.Error("Capteurs non enregistrés")
                    return false
                }
            }
            sensorsRegisteredFor = webhook
        }
        return when (api.updateStates(cfg.url, webhook, states(cur))) {
            WebhookResult.OK -> true
            WebhookResult.GONE -> {
                // The device was removed in Home Assistant: register again from scratch.
                store.saveWebhookId(null)
                sensorsRegisteredFor = null
                allowRetry && send(cfg, cur, allowRetry = false)
            }
            WebhookResult.ERROR -> {
                _status.value = ReportStatus.Error("Home Assistant injoignable")
                false
            }
        }
    }

    private fun definitions(s: BatteryState): List<SensorDef> = buildList {
        add(SensorDef(LEVEL, "sensor", "Batterie", "battery", "%", "measurement", levelIcon(s), JsonPrimitive(s.levelPct)))
        add(SensorDef(CHARGING, "binary_sensor", "En charge", "battery_charging", null, null, chargingIcon(s), JsonPrimitive(s.charging)))
        s.tempC?.let { add(SensorDef(TEMP, "sensor", "Température batterie", "temperature", "°C", "measurement", "mdi:thermometer", JsonPrimitive(it))) }
    }

    private fun states(s: BatteryState): List<SensorState> = buildList {
        add(SensorState(LEVEL, "sensor", JsonPrimitive(s.levelPct), levelIcon(s)))
        add(SensorState(CHARGING, "binary_sensor", JsonPrimitive(s.charging), chargingIcon(s)))
        s.tempC?.let { add(SensorState(TEMP, "sensor", JsonPrimitive(it), "mdi:thermometer")) }
    }

    private fun levelIcon(s: BatteryState): String {
        val tens = (s.levelPct / 10) * 10
        return when {
            s.charging -> "mdi:battery-charging"
            tens >= 100 -> "mdi:battery"
            tens <= 0 -> "mdi:battery-outline"
            else -> "mdi:battery-$tens"
        }
    }

    private fun chargingIcon(s: BatteryState) = if (s.charging) "mdi:power-plug" else "mdi:power-plug-off"

    private companion object {
        const val LEVEL = "battery_level"
        const val CHARGING = "battery_charging"
        const val TEMP = "battery_temperature"
        const val MIN_INTERVAL_MS = 5 * 60_000L
        const val RESEND_MS = 30 * 60_000L
    }
}
