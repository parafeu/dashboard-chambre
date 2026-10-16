package fr.alex.chambre.presence

/** Per-frame output of the background subtractor, as fractions of the image area. */
data class FrameStats(val fgFraction: Float, val largestBlobFraction: Float)

enum class MotionResult { NONE, MOTION, GLOBAL_CHANGE, BLANKED }

/**
 * Tells real motion (a localized region changing over several frames) apart from global image
 * changes (lights, auto-exposure, the screen itself turning on/off) and sensor noise.
 */
class MotionDecider(
    private val minArea: () -> Float,
    private val now: () -> Long,
    private val globalThreshold: Float = 0.6f,
    private val blankMs: Long = 2_000,
) {
    private val history = ArrayDeque<Boolean>()
    private var blankedUntil = Long.MIN_VALUE

    /** Ignore frames for [blankMs], e.g. right after the screen goes to sleep or wakes up. */
    fun blank() {
        blankedUntil = now() + blankMs
        history.clear()
    }

    fun onFrame(s: FrameStats): MotionResult {
        if (now() < blankedUntil) return MotionResult.BLANKED
        if (s.fgFraction >= globalThreshold) {
            history.clear()
            return MotionResult.GLOBAL_CHANGE
        }
        history.addLast(s.largestBlobFraction >= minArea())
        if (history.size > WINDOW) history.removeFirst()
        return if (history.count { it } >= REQUIRED) MotionResult.MOTION else MotionResult.NONE
    }

    private companion object {
        const val WINDOW = 3
        const val REQUIRED = 2
    }
}
