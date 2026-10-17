package fr.alex.chambre.presence

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches the front camera (~3 fps) and calls [onMotion] when [MotionDecider] sees real motion.
 * While [previewEnabled] is true, publishes an annotated preview on [preview].
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CameraMotionSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    minArea: () -> Float,
    private val previewEnabled: () -> Boolean,
    private val onMotion: () -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analyzer: OpenCvMotionAnalyzer? = null
    private val decider = MotionDecider(minArea, SystemClock::elapsedRealtime)
    private var lastFrameAt = 0L
    private var relearn = true
    private var frames = 0

    @Volatile
    private var blankRequested = false

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    /** Call when the screen sleeps or wakes: its own light change must not count as motion. */
    fun onScreenTransition() {
        blankRequested = true
    }

    fun start() {
        if (!OpenCVLoader.initLocal()) {
            Log.w(TAG, "OpenCV failed to load: sleep on touch only")
            return
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get().also { provider = it }
                if (!p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    Log.w(TAG, "No front camera: sleep on touch only")
                    return@addListener
                }
                val builder = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // We analyse ~3 fps: capturing at a fixed 15 fps instead of 30 halves the camera pipeline's work.
                Camera2Interop.Extender(builder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(CAPTURE_FPS, CAPTURE_FPS))
                val analysis = builder.build()
                analysis.setAnalyzer(executor) { image -> image.use { analyze(it) } }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.w(TAG, "Camera unavailable: sleep on touch only", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_INTERVAL_MS) return
        lastFrameAt = now
        if (blankRequested) {
            blankRequested = false
            decider.blank()
            relearn = true
        }
        val a = (analyzer ?: OpenCvMotionAnalyzer().also { analyzer = it })
        val plane = image.planes[0]
        val wantPreview = previewEnabled()
        val relearning = relearn
        val result = a.analyze(plane.buffer, image.width, image.height, plane.rowStride, relearning, wantPreview)
        relearn = false
        _preview.value = if (wantPreview) result.preview else null
        // A full relearn rebuilds the background from this frame; its mask is meaningless.
        if (relearning) return
        val decision = decider.onFrame(result.stats)
        if (decision == MotionResult.GLOBAL_CHANGE) relearn = true
        if (decision == MotionResult.MOTION) onMotion()
        if (decision != MotionResult.NONE || ++frames % 30 == 0) {
            Log.d(TAG, "res=$decision fg=%.3f blob=%.3f".format(result.stats.fgFraction, result.stats.largestBlobFraction))
        }
    }

    fun stop() {
        provider?.unbindAll()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "CameraMotion"
        const val FRAME_INTERVAL_MS = 300L
        const val CAPTURE_FPS = 15
    }
}
