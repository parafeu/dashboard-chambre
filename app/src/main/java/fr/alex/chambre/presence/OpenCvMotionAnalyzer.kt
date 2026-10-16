package fr.alex.chambre.presence

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video
import java.nio.ByteBuffer

data class Analysis(val stats: FrameStats, val preview: Bitmap?)

/**
 * Background subtraction on a small grayscale frame: MOG2 learns what the empty room looks like
 * and reports only what departs from it. Not thread-safe: call from the camera analyzer thread.
 */
class OpenCvMotionAnalyzer {
    private val mog2: BackgroundSubtractorMOG2 =
        Video.createBackgroundSubtractorMOG2(HISTORY, VAR_THRESHOLD, false)
    private val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
    private val small = Mat()
    private val blurred = Mat()
    private val mask = Mat()
    private val labels = Mat()
    private val compStats = Mat()
    private val centroids = Mat()
    private var raw = ByteArray(0)

    fun analyze(y: ByteBuffer, width: Int, height: Int, rowStride: Int, relearn: Boolean, wantPreview: Boolean): Analysis {
        val needed = rowStride * height
        if (raw.size != needed) raw = ByteArray(needed)
        y.rewind()
        y.get(raw, 0, minOf(y.remaining(), needed))
        val full = Mat(height, rowStride, CvType.CV_8UC1)
        full.put(0, 0, raw)
        val roi = full.submat(0, height, 0, width)
        Imgproc.resize(roi, small, Size(W.toDouble(), H.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        roi.release()
        full.release()

        Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)
        mog2.apply(blurred, mask, if (relearn) 1.0 else -1.0)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)

        val area = (W * H).toFloat()
        val fg = Core.countNonZero(mask) / area
        val n = Imgproc.connectedComponentsWithStats(mask, labels, compStats, centroids, 8, CvType.CV_32S)
        var best = 0
        var bestLabel = -1
        for (i in 1 until n) { // label 0 is the background
            val a = compStats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            if (a > best) {
                best = a
                bestLabel = i
            }
        }
        val stats = FrameStats(fgFraction = fg, largestBlobFraction = best / area)
        return Analysis(stats, if (wantPreview) preview(bestLabel) else null)
    }

    /** Gray frame with moving pixels tinted green and the largest region boxed. */
    private fun preview(bestLabel: Int): Bitmap {
        val rgba = Mat()
        Imgproc.cvtColor(small, rgba, Imgproc.COLOR_GRAY2RGBA)
        rgba.setTo(GREEN, mask)
        if (bestLabel > 0) {
            val x = compStats.get(bestLabel, Imgproc.CC_STAT_LEFT)[0]
            val yy = compStats.get(bestLabel, Imgproc.CC_STAT_TOP)[0]
            val w = compStats.get(bestLabel, Imgproc.CC_STAT_WIDTH)[0]
            val h = compStats.get(bestLabel, Imgproc.CC_STAT_HEIGHT)[0]
            Imgproc.rectangle(rgba, Point(x, yy), Point(x + w - 1, yy + h - 1), GREEN, 1)
        }
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        rgba.release()
        return bmp
    }

    private companion object {
        const val W = 160
        const val H = 120
        const val HISTORY = 150
        const val VAR_THRESHOLD = 25.0
        val GREEN = Scalar(127.0, 219.0, 160.0, 255.0)
    }
}
