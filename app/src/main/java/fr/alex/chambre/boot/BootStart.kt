package fr.alex.chambre.boot

import android.os.Build

enum class BootStart { OFF, START, NEEDS_OVERLAY }

/**
 * Whether the dashboard can open itself when the tablet boots. Since Android 10 an app may only start an
 * activity from the background with the « display over other apps » permission; Android 9 allows it.
 */
fun bootStart(enabled: Boolean, sdk: Int, canDrawOverlays: Boolean): BootStart = when {
    !enabled -> BootStart.OFF
    sdk >= Build.VERSION_CODES.Q && !canDrawOverlays -> BootStart.NEEDS_OVERLAY
    else -> BootStart.START
}
