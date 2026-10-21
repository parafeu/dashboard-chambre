package fr.alex.chambre.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundGraceTest {
    @Test
    fun `leaving stops after the grace period, coming back is immediate`() = runTest {
        val foreground = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        activeWithGrace(foreground, graceMs = 30_000).onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        foreground.value = false
        advanceTimeBy(29_000); runCurrent()
        assertEquals(listOf(true), seen)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf(true, false), seen)
        foreground.value = true
        runCurrent()
        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun `a short trip away never stops`() = runTest {
        val foreground = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        activeWithGrace(foreground, graceMs = 30_000).onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        foreground.value = false
        advanceTimeBy(10_000); runCurrent()
        foreground.value = true
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(true), seen)
    }
}
