package fr.alex.chambre.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeatherFormatTest {
    private val today = LocalDate.of(2026, 10, 16) // a Friday

    @Test
    fun today_is_auj() = assertEquals("Auj.", dayLabel(today, today))

    @Test
    fun other_days_are_short_french_names() {
        assertEquals("Sam.", dayLabel(today.plusDays(1), today))
        assertEquals("Mer.", dayLabel(today.plusDays(5), today))
    }
}
