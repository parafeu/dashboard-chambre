package fr.alex.chambre.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Follows the sticky ACTION_BATTERY_CHANGED broadcast. */
class BatteryMonitor(private val context: Context) {
    private val _state = MutableStateFlow<BatteryState?>(null)
    val state: StateFlow<BatteryState?> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update(intent)
    }

    fun start() {
        // Registering for a sticky broadcast returns its current value right away.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::update)
    }

    fun stop() {
        context.unregisterReceiver(receiver)
    }

    private fun update(intent: Intent) {
        batteryStateFrom(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
        )?.let { _state.value = it }
    }
}
