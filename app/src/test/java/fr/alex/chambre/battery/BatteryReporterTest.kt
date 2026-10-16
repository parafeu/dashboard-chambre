package fr.alex.chambre.battery

import fr.alex.chambre.ha.DeviceInfo
import fr.alex.chambre.ha.HaConfig
import fr.alex.chambre.ha.MobileAppApi
import fr.alex.chambre.ha.SensorDef
import fr.alex.chambre.ha.SensorState
import fr.alex.chambre.ha.WebhookResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMobileApp : MobileAppApi {
    var registrations = 0
    val sensors = mutableListOf<SensorDef>()
    val updates = mutableListOf<List<SensorState>>()
    var nextUpdate = WebhookResult.OK
    override suspend fun register(baseUrl: String, token: String, info: DeviceInfo): String {
        registrations++
        return "wh$registrations"
    }
    override suspend fun registerSensor(baseUrl: String, webhookId: String, sensor: SensorDef): WebhookResult {
        sensors += sensor
        return WebhookResult.OK
    }
    override suspend fun updateStates(baseUrl: String, webhookId: String, states: List<SensorState>): WebhookResult {
        updates += states
        return nextUpdate.also { nextUpdate = WebhookResult.OK }
    }
}

private class FakeStore : RegistrationStore {
    var webhook: String? = null
    override suspend fun deviceId() = "dev-1"
    override suspend fun webhookId() = webhook
    override suspend fun saveWebhookId(id: String?) { webhook = id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryReporterTest {
    private val api = FakeMobileApp()
    private val store = FakeStore()
    private val battery = MutableStateFlow<BatteryState?>(BatteryState(84, true, 25.2))
    private val config = MutableStateFlow(HaConfig("http://ha", "tok"))

    private fun TestScope.start(): BatteryReporter =
        BatteryReporter(
            backgroundScope, battery, config, api, store,
            deviceInfo = { DeviceInfo(it, "1.1.0", "samsung", "SM-T810", "11") },
            now = { testScheduler.currentTime },
        ).also { runCurrent() }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun levelSent(i: Int) = api.updates[i].first { it.uniqueId == "battery_level" }.state.content.toInt()

    @Test
    fun first_state_registers_device_and_three_sensors_then_sends() = runTest {
        start()
        assertEquals(1, api.registrations)
        assertEquals("wh1", store.webhook)
        assertEquals(listOf("battery_level", "battery_charging", "battery_temperature"), api.sensors.map { it.uniqueId })
        assertEquals(1, api.updates.size)
    }

    @Test
    fun level_change_waits_five_minutes() = runTest {
        start()
        advance(60_000)
        battery.value = BatteryState(83, true, 25.2)
        advance(60_000)
        assertEquals(1, api.updates.size)
        advance(3 * 60_000)
        assertEquals(2, api.updates.size)
        assertEquals(83, levelSent(1))
    }

    @Test
    fun charging_change_is_sent_immediately() = runTest {
        start()
        advance(10_000)
        battery.value = BatteryState(84, false, 25.2)
        runCurrent()
        assertEquals(2, api.updates.size)
    }

    @Test
    fun missing_temperature_is_not_sent() = runTest { // RF4
        battery.value = BatteryState(50, false, null)
        start()
        assertEquals(listOf("battery_level", "battery_charging"), api.sensors.map { it.uniqueId })
        assertTrue(api.updates[0].none { it.uniqueId == "battery_temperature" })
    }

    @Test
    fun state_is_resent_every_thirty_minutes() = runTest {
        start()
        advance(29 * 60_000)
        assertEquals(1, api.updates.size)
        advance(60_000)
        assertEquals(2, api.updates.size)
    }

    @Test
    fun gone_webhook_triggers_new_registration() = runTest { // RF3
        start()
        api.nextUpdate = WebhookResult.GONE
        battery.value = BatteryState(84, false, 25.2)
        runCurrent()
        assertEquals(2, api.registrations)
        assertEquals("wh2", store.webhook)
        assertEquals(3, api.updates.size) // initial, the 410 one, and the retry
    }

    @Test
    fun blank_token_sends_nothing() = runTest { // RF2
        config.value = HaConfig("http://ha", "")
        start()
        advance(60 * 60_000)
        assertEquals(0, api.registrations)
        assertEquals(0, api.updates.size)
    }

    @Test
    fun existing_webhook_is_reused() = runTest {
        store.webhook = "saved"
        start()
        assertEquals(0, api.registrations)
        assertEquals(1, api.updates.size)
    }
}
