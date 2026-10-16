package fr.alex.chambre.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleTest {

    @Test
    fun first_submit_sends_immediately() = runTest {
        val sent = mutableListOf<Int>()
        val t = Throttle<Int>(backgroundScope, 150) { sent += it }
        t.submit(1)
        runCurrent()
        assertEquals(listOf(1), sent)
    }

    @Test
    fun submits_within_interval_send_only_last_at_interval_end() = runTest {
        val sent = mutableListOf<Pair<Int, Long>>()
        val t = Throttle<Int>(backgroundScope, 150) { sent += it to testScheduler.currentTime }
        t.submit(1); runCurrent()
        advanceTimeBy(10); t.submit(2); runCurrent()
        advanceTimeBy(10); t.submit(3); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(1 to 0L, 3 to 150L), sent)
    }

    @Test
    fun flush_sends_now_and_cancels_pending() = runTest {
        val sent = mutableListOf<Int>()
        val t = Throttle<Int>(backgroundScope, 150) { sent += it }
        t.submit(1); runCurrent()
        t.submit(2); runCurrent()
        t.flush(9); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(1, 9), sent)
    }
}
