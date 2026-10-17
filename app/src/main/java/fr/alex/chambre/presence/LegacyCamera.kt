@file:Suppress("DEPRECATION") // android.hardware.Camera is the point: the fallback for HALs CameraX cannot open.

package fr.alex.chambre.presence

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer

/**
 * Front camera through the old Camera1 API. Every open/close runs in order on one thread, so they never
 * overlap (the camera service refuses a second client while one is connecting). Delivers NV21 frames:
 * the luminance plane comes first, so [onFrame] gets (buffer, width, height, rowStride = width) like a
 * CameraX Y plane.
 */
class LegacyCamera(private val onFrame: (ByteBuffer, Int, Int, Int) -> Unit) {
    private val thread = HandlerThread("LegacyCamera").apply { start() }
    private val handler = Handler(thread.looper)
    private var camera: Camera? = null
    private var texture: SurfaceTexture? = null

    /** Opens the front camera unless it is already open; retries while the camera is busy. */
    fun open() {
        handler.removeCallbacks(retry)
        handler.post { tryOpen(attempt = 1) }
    }

    fun close() {
        handler.removeCallbacks(retry)
        handler.post { releaseNow() }
    }

    /** Releases the camera and stops the thread; this instance cannot be used afterwards. */
    fun shutdown() {
        handler.removeCallbacks(retry)
        handler.post {
            releaseNow()
            thread.quitSafely()
        }
    }

    private var nextAttempt = 1
    private val retry = Runnable { tryOpen(nextAttempt) }

    private fun tryOpen(attempt: Int) {
        if (camera != null) return
        try {
            openNow()
        } catch (e: RuntimeException) {
            releaseNow()
            if (attempt < MAX_ATTEMPTS) {
                // Typically "too many clients" while a previous user (an old process after an update) lets go.
                Log.w(TAG, "Camera1 busy (attempt $attempt): ${e.message}")
                nextAttempt = attempt + 1
                handler.postDelayed(retry, RETRY_MS)
            } else {
                Log.w(TAG, "Camera1 could not open the front camera", e)
            }
        }
    }

    private fun openNow() {
        val info = Camera.CameraInfo()
        val id = (0 until Camera.getNumberOfCameras()).firstOrNull {
            Camera.getCameraInfo(it, info)
            info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
        } ?: run {
            Log.w(TAG, "Camera1: no front camera")
            return
        }
        val cam = Camera.open(id).also { camera = it }
        val p = cam.parameters
        val sizes = p.supportedPreviewSizes
        val size = sizes.filter { it.width >= 320 && it.height >= 240 }.minByOrNull { it.width * it.height }
            ?: sizes.minBy { it.width * it.height }
        p.setPreviewSize(size.width, size.height)
        p.previewFormat = ImageFormat.NV21
        cam.parameters = p
        // ~15 fps is plenty for ~3 analysed frames per second; not every HAL accepts every range.
        p.supportedPreviewFpsRange?.filter { it[1] >= 15_000 }?.minByOrNull { it[1] }?.let { r ->
            try {
                p.setPreviewFpsRange(r[0], r[1])
                cam.parameters = p
            } catch (e: RuntimeException) {
                Log.w(TAG, "Camera1: fps range ${r[0]}-${r[1]} refused", e)
            }
        }
        val w = size.width
        val h = size.height
        cam.setPreviewTexture(SurfaceTexture(0).also { texture = it }) // preview needs a target, never shown
        val bytes = w * h * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8
        repeat(2) { cam.addCallbackBuffer(ByteArray(bytes)) }
        cam.setPreviewCallbackWithBuffer { data, c ->
            onFrame(ByteBuffer.wrap(data), w, h, w)
            c.addCallbackBuffer(data)
        }
        cam.setErrorCallback { error, _ -> Log.w(TAG, "Camera1 error $error") }
        cam.startPreview()
        Log.i(TAG, "Camera1 started: front camera $id, ${w}x$h")
    }

    private fun releaseNow() {
        camera?.let {
            runCatching { it.setPreviewCallbackWithBuffer(null) }
            runCatching { it.stopPreview() }
            it.release()
        }
        camera = null
        texture?.release()
        texture = null
    }

    private companion object {
        const val TAG = "CameraMotion"
        const val MAX_ATTEMPTS = 5
        const val RETRY_MS = 2_000L
    }
}
