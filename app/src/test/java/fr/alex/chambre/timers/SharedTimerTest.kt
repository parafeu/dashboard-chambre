package fr.alex.chambre.timers

import fr.alex.chambre.ha.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SharedTimerTest {
    private val end = Instant.parse("2026-11-11T12:10:00Z")

    private fun timer(id: String, end: Instant) = SharedTimer(id, id, end, 600, "")

    @Test
    fun round_trip_description_parses_back() {
        val item = TodoItem("u1", "Pâtes", timerDescription(end, 600, "Lasagnes · étape 4", "cuisine"))
        assertEquals(SharedTimer("u1", "Pâtes", end, 600, "Lasagnes · étape 4"), parseTimer(item))
    }

    @Test
    fun blank_summary_becomes_Minuterie() {
        val item = TodoItem("u1", "  ", timerDescription(end, 60, "Lancée en cuisine", "cuisine"))
        assertEquals("Minuterie", parseTimer(item)?.name)
    }

    @Test
    fun foreign_items_are_ignored() {
        assertNull(parseTimer(TodoItem("a", "x", null)))
        assertNull(parseTimer(TodoItem("b", "x", "acheter du pain")))
        assertNull(parseTimer(TodoItem("c", "x", """{"v":1,"duration":60}""")))
        assertNull(parseTimer(TodoItem("d", "x", """{"v":2,"end":"2026-11-11T12:10:00Z","duration":60}""")))
        assertNull(parseTimer(TodoItem("e", "x", """{"v":1,"end":"demain","duration":60}""")))
        assertNull(parseTimer(TodoItem("f", "x", """[1,2]""")))
    }

    @Test
    fun ringing_at_and_after_end() {
        val t = timer("t", end)
        assertFalse(t.isRinging(end.minusSeconds(1)))
        assertTrue(t.isRinging(end))
        assertTrue(t.isRinging(end.plusSeconds(300)))
    }

    @Test
    fun remaining_rounds_up() {
        val t = timer("t", end)
        assertEquals(5, t.remainingSec(end.minusMillis(4_200)))
        assertEquals(0, t.remainingSec(end.plusSeconds(3)))
    }

    @Test
    fun ringing_first_then_soonest() {
        val now = end
        val run10 = timer("run10", now.plusSeconds(600))
        val ring1 = timer("ring1", now.minusSeconds(60))
        val run2 = timer("run2", now.plusSeconds(120))
        val ring3 = timer("ring3", now.minusSeconds(180))
        assertEquals(
            listOf("ring3", "ring1", "run2", "run10"),
            sortTimers(listOf(run10, ring1, run2, ring3), now).map { it.id },
        )
    }

    @Test
    fun clock_format() {
        assertEquals("0:00", formatClock(0))
        assertEquals("4:12", formatClock(252))
        assertEquals("27:05", formatClock(1625))
        assertEquals("1:02:03", formatClock(3723))
    }
}
