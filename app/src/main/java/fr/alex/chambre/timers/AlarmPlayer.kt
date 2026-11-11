package fr.alex.chambre.timers

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.util.Log

/** Loops the tablet's default alarm sound on the alarm stream (its own volume) until [stop]. */
class AlarmPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var ringtone: Ringtone? = null

    fun start() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = uri?.let { RingtoneManager.getRingtone(appContext, it) }
        if (r == null) {
            Log.w(TAG, "No alarm or ringtone sound on this device")
            return
        }
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        r.isLooping = true
        r.play()
        ringtone = r
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
    }

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}
