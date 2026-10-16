package fr.alex.chambre.presence

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches the front camera at low resolution (~3 fps) and calls [onMotion] when consecutive
 * frames differ by more than [sensitivity] (fraction of pixels). Bound to [lifecycleOwner].
 */
class CameraMotionSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val sensitivity: () -> Float,
    private val onMotion: () -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var previous: ByteArray? = null
    private var lastFrameAt = 0L

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get().also { provider = it }
                if (!p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    Log.w(TAG, "No front camera: sleep on touch only")
                    return@addListener
                }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(160, 120), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    image.use {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFrameAt < FRAME_INTERVAL_MS) return@use
                        lastFrameAt = now
                        val plane = it.planes[0]
                        val buffer = plane.buffer.also { b -> b.rewind() }
                        val bytes = ByteArray(buffer.remaining()).also { b -> buffer.get(b) }
                        val frame = MotionDetector.downsample(bytes, it.width, it.height, plane.rowStride)
                        val prev = previous
                        previous = frame
                        if (prev != null && MotionDetector.score(prev, frame) > sensitivity()) onMotion()
                    }
                }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.w(TAG, "Camera unavailable: sleep on touch only", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        provider?.unbindAll()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "CameraMotion"
        const val FRAME_INTERVAL_MS = 333L
    }
}
