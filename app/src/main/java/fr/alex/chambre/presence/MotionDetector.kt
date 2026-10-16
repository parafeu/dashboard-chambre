package fr.alex.chambre.presence

import kotlin.math.abs

/** Frame-difference motion detection on tiny grayscale (luminance) frames. */
object MotionDetector {
    const val W = 40
    const val H = 30

    /** Shrinks a luminance plane to [W]×[H] by averaging each block. */
    fun downsample(y: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val out = ByteArray(W * H)
        for (r in 0 until H) {
            val y0 = r * height / H
            val y1 = maxOf(y0 + 1, (r + 1) * height / H)
            for (c in 0 until W) {
                val x0 = c * width / W
                val x1 = maxOf(x0 + 1, (c + 1) * width / W)
                var sum = 0
                for (yy in y0 until y1) {
                    val base = yy * rowStride
                    for (xx in x0 until x1) sum += y[base + xx].toInt() and 0xFF
                }
                out[r * W + c] = (sum / ((y1 - y0) * (x1 - x0))).toByte()
            }
        }
        return out
    }

    /** Fraction (0..1) of pixels whose luminance changed by more than [pixelThreshold]. */
    fun score(prev: ByteArray, cur: ByteArray, pixelThreshold: Int = 25): Float {
        var changed = 0
        for (i in cur.indices) {
            val d = abs((cur[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
            if (d > pixelThreshold) changed++
        }
        return changed.toFloat() / cur.size
    }
}
