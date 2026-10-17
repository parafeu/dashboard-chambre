package fr.alex.chambre.presence

/**
 * Watches the camera's frames. Old legacy HALs can fail CameraX's first open (5 s timeout) and leave it
 * waiting forever: after [STALL_MS] without a frame, CameraX is abandoned for the Camera1 API. A silent
 * Camera1 is reopened, waiting twice as long after each fruitless attempt up to [MAX_WAIT_MS]; any frame
 * resets the wait. Thread-safe: frames arrive on the camera thread.
 */
class CameraWatchdog(private val clock: () -> Long) {
    enum class Backend { CAMERAX, CAMERA1 }
    enum class Action { NONE, FALLBACK_TO_CAMERA1, RESTART }

    @Volatile
    var backend = Backend.CAMERAX
        private set

    private var since = 0L
    private var failures = 0

    /** A camera was (re)opened: give it a full wait to deliver its first frame. */
    @Synchronized
    fun onBound() {
        since = clock()
    }

    @Synchronized
    fun onFrame() {
        since = clock()
        failures = 0
    }

    /** What to do now; anything but [Action.NONE] counts as a failed attempt. */
    @Synchronized
    fun check(): Action {
        val wait = minOf(STALL_MS shl minOf(failures, 10), MAX_WAIT_MS)
        if (clock() - since < wait) return Action.NONE
        if (backend == Backend.CAMERAX) {
            backend = Backend.CAMERA1
            return Action.FALLBACK_TO_CAMERA1
        }
        failures++
        return Action.RESTART
    }

    private companion object {
        const val STALL_MS = 20_000L
        const val MAX_WAIT_MS = 300_000L
    }
}
