package fr.alex.chambre.presence

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches the front camera (~3 fps) and calls [onMotion] when [MotionDecider] sees real motion.
 * While [previewEnabled] is true, publishes an annotated preview on [preview]. Uses CameraX, and falls
 * back to Camera1 ([LegacyCamera]) when CameraX delivers no frame (see [CameraWatchdog]).
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
    private val watchdog = CameraWatchdog(SystemClock::elapsedRealtime)
    private val main = Handler(Looper.getMainLooper())
    private var legacy: LegacyCamera? = null

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
                bind(p)
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                main.postDelayed(watch, WATCH_INTERVAL_MS)
            } catch (e: Exception) {
                Log.w(TAG, "Camera unavailable: sleep on touch only", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Acts on a camera that delivers no frames (see [CameraWatchdog]); only while the app is visible. */
    private val watch: Runnable = object : Runnable {
        override fun run() {
            val p = provider ?: return
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                watchdog.onBound() // camera closed by the lifecycle: silence is expected
            } else {
                when (watchdog.check()) {
                    CameraWatchdog.Action.NONE -> Unit
                    CameraWatchdog.Action.FALLBACK_TO_CAMERA1 -> {
                        Log.w(TAG, "CameraX delivered no frame: falling back to Camera1")
                        p.unbindAll() // frees the device if CameraX still holds it
                        openLegacy()
                    }
                    CameraWatchdog.Action.RESTART -> {
                        Log.w(TAG, "Camera1 delivered no frame: reopening it")
                        openLegacy()
                    }
                }
            }
            main.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    /** Camera1 does not follow the lifecycle by itself: close it when the app stops, reopen it after. */
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (watchdog.backend == CameraWatchdog.Backend.CAMERA1) openLegacy()
        }

        override fun onStop(owner: LifecycleOwner) {
            legacy?.close()
            legacy = null
        }
    }

    private fun openLegacy() {
        legacy?.close()
        watchdog.onBound()
        legacy = LegacyCamera(::analyzeFrame).also { it.open() }
    }

    private fun bind(p: ProcessCameraProvider) {
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
        watchdog.onBound()
        p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        val plane = image.planes[0]
        analyzeFrame(plane.buffer, image.width, image.height, plane.rowStride)
    }

    /** One luminance frame from either backend (they never run together, the lock covers a switch). */
    @Synchronized
    private fun analyzeFrame(y: ByteBuffer, width: Int, height: Int, rowStride: Int) {
        watchdog.onFrame()
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_INTERVAL_MS) return
        lastFrameAt = now
        if (blankRequested) {
            blankRequested = false
            decider.blank()
            relearn = true
        }
        val a = (analyzer ?: OpenCvMotionAnalyzer().also { analyzer = it })
        val wantPreview = previewEnabled()
        val relearning = relearn
        val result = a.analyze(y, width, height, rowStride, relearning, wantPreview)
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
        main.removeCallbacks(watch)
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        legacy?.close()
        legacy = null
        provider?.unbindAll()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "CameraMotion"
        const val FRAME_INTERVAL_MS = 300L
        const val CAPTURE_FPS = 15
        const val WATCH_INTERVAL_MS = 5_000L
    }
}
