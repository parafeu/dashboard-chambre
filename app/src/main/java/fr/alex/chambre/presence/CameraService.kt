package fr.alex.chambre.presence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fr.alex.chambre.R
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.settings.SettingsStore
import fr.alex.chambre.settings.dataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * Motion detection that keeps running with the screen off (« Éteindre vraiment l'écran »): the camera lives in this
 * foreground service instead of the activity, the CPU is held awake, and a motion turns the screen back on.
 */
class CameraService : LifecycleService() {
    private var camera: CameraMotionSource? = null
    private var cpu: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Détection de mouvement", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Détection de mouvement")
            .setContentText("Rallume l'écran quand quelqu'un passe.")
            .setSmallIcon(R.drawable.ic_notification_motion)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Screen off, the CPU would otherwise sleep between camera frames.
        cpu = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chambre:camera")
            .also { it.acquire() }

        val settings = SettingsStore(dataStore).settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        val cam = CameraMotionSource(
            context = this,
            lifecycleOwner = this,
            minArea = { settings.value?.sensitivity ?: Settings().sensitivity },
            previewEnabled = { MotionBus.previewWanted.value },
            onMotion = {
                MotionBus.motion.tryEmit(Unit)
                Waker.wakeIfOff(this)
            },
        )
        camera = cam
        cam.start()
        lifecycleScope.launch { cam.preview.collect { MotionBus.preview.value = it } }
        lifecycleScope.launch { MotionBus.screenTransitions.collect { cam.onScreenTransition() } }
        lifecycleScope.launch {
            settings.map { it?.cameraApi }.distinctUntilChanged().collect { api -> if (api != null) cam.setApi(api) }
        }
    }

    override fun onDestroy() {
        camera?.stop()
        camera = null
        MotionBus.preview.value = null
        cpu?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "motion"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, CameraService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraService::class.java))
        }
    }
}
