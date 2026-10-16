package fr.alex.chambre.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDeciderTest {
    private var now = 0L
    private var minArea = 0.03f
    private val d = MotionDecider(minArea = { minArea }, now = { now })

    private val idle = FrameStats(fgFraction = 0.001f, largestBlobFraction = 0.001f)
    private val active = FrameStats(fgFraction = 0.08f, largestBlobFraction = 0.06f)
    private val global = FrameStats(fgFraction = 0.75f, largestBlobFraction = 0.70f)

    @Test
    fun single_active_frame_is_not_motion() {
        assertEquals(MotionResult.NONE, d.onFrame(active))
    }

    @Test
    fun two_active_frames_out_of_three_is_motion() {
        d.onFrame(active)
        d.onFrame(idle)
        assertEquals(MotionResult.MOTION, d.onFrame(active))
    }

    @Test
    fun blob_below_min_area_is_ignored() {
        val small = FrameStats(fgFraction = 0.02f, largestBlobFraction = 0.02f)
        d.onFrame(small)
        assertEquals(MotionResult.NONE, d.onFrame(small))
    }

    @Test
    fun global_change_is_reported_and_clears_history() {
        d.onFrame(active)
        assertEquals(MotionResult.GLOBAL_CHANGE, d.onFrame(global))
        // history cleared: one more active frame is not enough
        assertEquals(MotionResult.NONE, d.onFrame(active))
    }

    @Test
    fun blank_suppresses_decisions_for_two_seconds() {
        d.blank()
        d.onFrame(active)
        now = 1_999
        assertEquals(MotionResult.BLANKED, d.onFrame(active))
        now = 2_000
        d.onFrame(active)
        now = 2_300
        assertEquals(MotionResult.MOTION, d.onFrame(active))
    }

    @Test
    fun min_area_is_read_live() {
        minArea = 0.10f
        d.onFrame(active)
        assertEquals(MotionResult.NONE, d.onFrame(active))
        minArea = 0.05f
        assertEquals(MotionResult.MOTION, d.onFrame(active).let { d.onFrame(active) })
    }
}
