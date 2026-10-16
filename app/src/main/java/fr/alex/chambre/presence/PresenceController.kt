package fr.alex.chambre.presence

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Presence { AWAKE, ASLEEP }

/** Puts the screen to sleep after [sleepDelayMs] without motion or touch; wakes on either. */
class PresenceController(
    private val scope: CoroutineScope,
    private val sleepDelayMs: () -> Long,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _state = MutableStateFlow(Presence.AWAKE)
    val state: StateFlow<Presence> = _state.asStateFlow()

    @Volatile
    private var lastActivity = now()

    /** While true (e.g. the settings screen is open) the screen never sleeps. */
    @Volatile
    var hold = false
        set(value) {
            field = value
            activity()
        }
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        lastActivity = now()
        job = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (!hold && _state.value == Presence.AWAKE && now() - lastActivity >= sleepDelayMs()) {
                    _state.value = Presence.ASLEEP
                }
            }
        }
    }

    fun onMotion() = activity()

    fun onTouch() = activity()

    private fun activity() {
        lastActivity = now()
        _state.value = Presence.AWAKE
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 1_000L
    }
}
