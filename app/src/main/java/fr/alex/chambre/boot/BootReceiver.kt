package fr.alex.chambre.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import fr.alex.chambre.MainActivity
import fr.alex.chambre.settings.SettingsStore
import fr.alex.chambre.settings.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Opens the dashboard when the tablet boots (or after an update) if « Démarrage automatique » is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsStore(app.dataStore).settings.first().autoStart
                when (bootStart(enabled, Build.VERSION.SDK_INT, Settings.canDrawOverlays(app))) {
                    BootStart.START -> app.startActivity(
                        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    BootStart.NEEDS_OVERLAY -> Log.w(TAG, "Auto-start needs « display over other apps »")
                    BootStart.OFF -> Unit
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-start failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, // an update kills the app: bring it back
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
