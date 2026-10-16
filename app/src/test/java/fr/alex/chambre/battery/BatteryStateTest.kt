package fr.alex.chambre.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryStateTest {
    // android.os.BatteryManager constants
    private val charging = 2
    private val discharging = 3
    private val full = 5

    @Test
    fun parses_level_charging_and_temperature() {
        assertEquals(BatteryState(84, true, 25.2), batteryStateFrom(level = 84, scale = 100, status = charging, tempTenths = 252))
    }

    @Test
    fun full_counts_as_charging_and_scale_is_applied() {
        assertEquals(BatteryState(100, true, 30.0), batteryStateFrom(level = 200, scale = 200, status = full, tempTenths = 300))
    }

    @Test
    fun invalid_scale_gives_null() {
        assertNull(batteryStateFrom(level = 50, scale = 0, status = discharging, tempTenths = 250))
    }

    @Test
    fun missing_temperature_is_null() { // RF4
        assertNull(batteryStateFrom(level = 50, scale = 100, status = discharging, tempTenths = 0)!!.tempC)
        assertNull(batteryStateFrom(level = 50, scale = 100, status = discharging, tempTenths = -1)!!.tempC)
    }

    @Test
    fun icon_kinds() {
        assertEquals(BatteryIconKind.CHARGING, batteryIconKind(BatteryState(40, true, null)))
        assertEquals(BatteryIconKind.FULL, batteryIconKind(BatteryState(100, false, null)))
        assertEquals(BatteryIconKind.BAR_3, batteryIconKind(BatteryState(50, false, null)))
        assertEquals(BatteryIconKind.BAR_1, batteryIconKind(BatteryState(16, false, null)))
        assertEquals(BatteryIconKind.ALERT, batteryIconKind(BatteryState(14, false, null)))
    }
}
