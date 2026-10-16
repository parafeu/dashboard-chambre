package fr.alex.chambre.battery

import kotlin.math.roundToInt

data class BatteryState(val levelPct: Int, val charging: Boolean, val tempC: Double?)

private const val STATUS_CHARGING = 2 // BatteryManager.BATTERY_STATUS_CHARGING
private const val STATUS_FULL = 5 // BatteryManager.BATTERY_STATUS_FULL

/** Builds a state from ACTION_BATTERY_CHANGED extras; null when the extras are unusable. */
fun batteryStateFrom(level: Int, scale: Int, status: Int, tempTenths: Int): BatteryState? {
    if (scale <= 0 || level < 0) return null
    return BatteryState(
        levelPct = (level * 100f / scale).roundToInt().coerceIn(0, 100),
        charging = status == STATUS_CHARGING || status == STATUS_FULL,
        tempC = if (tempTenths > 0) tempTenths / 10.0 else null,
    )
}

enum class BatteryIconKind { CHARGING, FULL, BAR_6, BAR_5, BAR_4, BAR_3, BAR_2, BAR_1, ALERT }

fun batteryIconKind(s: BatteryState): BatteryIconKind = when {
    s.charging -> BatteryIconKind.CHARGING
    s.levelPct >= 95 -> BatteryIconKind.FULL
    s.levelPct < 15 -> BatteryIconKind.ALERT
    else -> when ((s.levelPct * 7 / 100).coerceIn(1, 6)) {
        6 -> BatteryIconKind.BAR_6
        5 -> BatteryIconKind.BAR_5
        4 -> BatteryIconKind.BAR_4
        3 -> BatteryIconKind.BAR_3
        2 -> BatteryIconKind.BAR_2
        else -> BatteryIconKind.BAR_1
    }
}
