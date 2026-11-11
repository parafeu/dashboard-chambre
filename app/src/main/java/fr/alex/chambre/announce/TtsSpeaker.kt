package fr.alex.chambre.announce

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Offline Android speech synthesis in French; queues what is said before the engine is ready.
 *
 * The engine runs in another app (Google TTS). When that app is updated or killed, this
 * TextToSpeech stays unbound for good and every speak() fails silently, so a failed speak
 * or a failed start builds a new engine and says the text once it is connected again.
 */
class TtsSpeaker(context: Context) : Speaker {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentLinkedQueue<String>()
    private val lock = Any()
    @Volatile private var ready = false
    @Volatile private var closed = false
    private var counter = 0
    private var tts: TextToSpeech = create()

    private fun create(): TextToSpeech {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(appContext) { status -> main.post { onInit(engine, status) } }
        return engine
    }

    private fun onInit(engine: TextToSpeech, status: Int) {
        synchronized(lock) {
            if (closed || engine !== tts) return
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TextToSpeech unavailable ($status); retrying in ${RETRY_MS / 1000} s")
                main.postDelayed({ rebuild() }, RETRY_MS)
                return
            }
            val lang = engine.setLanguage(Locale.FRANCE)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "French voice not installed; using the default voice")
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ready = true
        }
        flush()
    }

    override fun say(text: String) {
        pending += text
        if (ready) main.post { flush() }
    }

    private fun flush() {
        synchronized(lock) {
            while (ready && !closed) {
                val text = pending.peek() ?: return
                if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, "annonce-${counter++}") == TextToSpeech.SUCCESS) {
                    pending.poll()
                } else {
                    Log.w(TAG, "speak failed; reconnecting to the speech engine")
                    rebuild()
                    return
                }
            }
        }
    }

    private fun rebuild() {
        synchronized(lock) {
            if (closed) return
            ready = false
            runCatching { tts.shutdown() }
            tts = create()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            closed = true
            ready = false
            main.removeCallbacksAndMessages(null)
            tts.shutdown()
        }
    }

    private companion object {
        const val TAG = "TtsSpeaker"
        const val RETRY_MS = 30_000L
    }
}
