package fr.alex.chambre.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDetectorTest {

    private fun frame(fill: Int) = ByteArray(MotionDetector.W * MotionDetector.H) { fill.toByte() }

    @Test
    fun identical_frames_score_zero() {
        assertEquals(0f, MotionDetector.score(frame(100), frame(100)), 0f)
    }

    @Test
    fun changed_block_score_is_fraction() {
        val prev = frame(0)
        val cur = frame(0).also { for (i in 0 until 120) it[i] = 200.toByte() }
        assertEquals(0.1f, MotionDetector.score(prev, cur), 1e-6f)
    }

    @Test
    fun small_noise_ignored() {
        assertEquals(0f, MotionDetector.score(frame(100), frame(120)), 0f)
    }

    @Test
    fun unsigned_bytes_handled() {
        // 0x10 = 16 and 0xF0 = 240 : a big change, even though 0xF0 is negative as a signed byte.
        assertEquals(1f, MotionDetector.score(frame(0x10), frame(0xF0)), 0f)
    }

    @Test
    fun downsample_averages_blocks() {
        val width = 160
        val height = 120
        val rowStride = 176
        val y = ByteArray(rowStride * height)
        for (row in 0 until height) for (col in 0 until width) {
            y[row * rowStride + col] = (if (col < width / 2) 0 else 255).toByte()
        }
        val out = MotionDetector.downsample(y, width, height, rowStride)
        assertEquals(MotionDetector.W * MotionDetector.H, out.size)
        for (r in 0 until MotionDetector.H) for (c in 0 until MotionDetector.W) {
            val expected = if (c < MotionDetector.W / 2) 0 else 255
            assertEquals("r=$r c=$c", expected, out[r * MotionDetector.W + c].toInt() and 0xFF)
        }
    }
}
