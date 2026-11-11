package fr.alex.chambre.presence

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display

/** Turns the screen back on when it is really off (« Éteindre vraiment l'écran », or the power button). */
object Waker {
    /** The display state, not PowerManager.isInteractive: that one stays true while Android dozes. */
    fun screenIsOn(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON

    fun wakeIfOff(context: Context) {
        if (screenIsOn(context)) return
        val pm = context.getSystemService(PowerManager::class.java)
        // The only screen wake-up an ordinary app can request; MainActivity then shows over the lock screen.
        @Suppress("DEPRECATION")
        pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "chambre:wake")
            .acquire(WAKE_HOLD_MS)
    }

    private const val WAKE_HOLD_MS = 3_000L
}
