package fr.alex.chambre.announce

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/** Offline Android speech synthesis in French; queues what is said before the engine is ready. */
class TtsSpeaker(context: Context) : Speaker {
    private val pending = ConcurrentLinkedQueue<String>()
    @Volatile private var ready = false
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val lang = tts.setLanguage(Locale.FRANCE)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "French voice not installed; using the default voice")
            }
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ready = true
            while (true) speakNow(pending.poll() ?: break)
        } else {
            Log.w(TAG, "TextToSpeech unavailable ($status)")
        }
    }

    override fun say(text: String) {
        if (ready) speakNow(text) else pending += text
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "annonce-${counter++}")
    }

    fun shutdown() = tts.shutdown()

    private companion object {
        const val TAG = "TtsSpeaker"
    }
}
