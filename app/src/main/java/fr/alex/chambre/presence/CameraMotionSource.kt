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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import fr.alex.chambre.settings.CameraApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches the front camera (~3 fps) and calls [onMotion] when [MotionDecider] sees real motion.
 * While [previewEnabled] is true, publishes an annotated preview on [preview]. Opens nothing until
 * [setApi] says which camera API to use: CameraX, or Camera1 ([LegacyCamera]) for old HALs CameraX cannot open.
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
    private val legacy by lazy { LegacyCamera(::analyzeFrame) }
    private var legacyUsed = false
    private var api: CameraApi? = null
    private var ready = false

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
            // CameraX failing to initialise (old or flaky HALs) must not stop Camera1, which does without it.
            try {
                val p = future.get()
                if (p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) provider = p else Log.w(TAG, "CameraX sees no front camera")
            } catch (e: Exception) {
                Log.w(TAG, "CameraX unavailable", e)
            }
            ready = true
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            applyApi()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Selects the camera API (main thread); switching closes one and opens the other. */
    fun setApi(value: CameraApi) {
        if (value == api) return
        api = value
        if (ready) applyApi()
    }

    private fun applyApi() {
        val a = api ?: return
        val p = provider
        if (legacyUsed) legacy.close()
        p?.unbindAll()
        blankRequested = true // a new source: relearn the background
        Log.i(TAG, "Motion detection with $a")
        when (a) {
            CameraApi.CAMERAX -> if (p != null) bind(p) else Log.w(TAG, "CameraX unavailable: try Camera1 in the settings")
            CameraApi.CAMERA1 -> if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) openLegacy()
        }
    }

    /** Camera1 does not follow the lifecycle by itself: close it when the app stops, reopen it after. */
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (api == CameraApi.CAMERA1) openLegacy()
        }

        override fun onStop(owner: LifecycleOwner) {
            if (legacyUsed) legacy.close()
        }
    }

    private fun openLegacy() {
        legacyUsed = true
        legacy.open() // no-op when already open
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
        p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        val plane = image.planes[0]
        analyzeFrame(plane.buffer, image.width, image.height, plane.rowStride)
    }

    /** One luminance frame from either API (they never run together; the lock covers a switch). */
    @Synchronized
    private fun analyzeFrame(y: ByteBuffer, width: Int, height: Int, rowStride: Int) {
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
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        if (legacyUsed) legacy.shutdown()
        provider?.unbindAll()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "CameraMotion"
        const val FRAME_INTERVAL_MS = 300L
        const val CAPTURE_FPS = 15
    }
}
