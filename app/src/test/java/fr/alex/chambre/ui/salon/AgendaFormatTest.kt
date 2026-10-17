package fr.alex.chambre.ui.salon

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AgendaFormatTest {
    private val now = Instant.parse("2026-10-17T11:45:00Z")

    @Test
    fun countdown() {
        assertEquals("DANS 2 H 15", untilLabel(now, Instant.parse("2026-10-17T14:00:00Z")))
        assertEquals("DANS 12 MIN", untilLabel(now, Instant.parse("2026-10-17T11:57:00Z")))
        assertEquals("DANS 1 H", untilLabel(now, Instant.parse("2026-10-17T12:45:00Z")))
        assertEquals("MAINTENANT", untilLabel(now, Instant.parse("2026-10-17T11:45:30Z")))
    }
}
