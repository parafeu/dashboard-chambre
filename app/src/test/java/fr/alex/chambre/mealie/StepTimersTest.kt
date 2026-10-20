package fr.alex.chambre.mealie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepTimersTest {
    private fun found(text: String) = findTimers(text).map { Triple(text.substring(it.start, it.end), it.seconds, it.label) }

    @Test
    fun minutes_seconds_and_hours_are_found_in_place() {
        assertEquals(listOf(Triple("10 minutes", 600, "10 min")), found("Laisser mijoter 10 minutes à couvert."))
        assertEquals(listOf(Triple("5 minutes", 300, "5 min"), Triple("3 minutes", 180, "3 min")),
            found("Mijoter 5 minutes. Ajouter les petits pois, puis encore 3 minutes."))
        assertEquals(listOf(Triple("2 heures", 7200, "2 h")), found("Laisser reposer 2 heures au frais."))
        assertEquals(listOf(Triple("20 min", 1200, "20 min")), found("Enfourner 20 min."))
    }

    @Test
    fun a_range_uses_its_upper_bound() {
        assertEquals(listOf(Triple("30 à 45 secondes", 45, "45 s")), found("remuer 30 à 45 secondes jusqu'à ce que ça embaume"))
        assertEquals(listOf(Triple("8-10 min", 600, "10 min")), found("Cuire 8-10 min."))
    }

    @Test
    fun hours_with_minutes_make_one_timer() {
        assertEquals(listOf(Triple("1 h 30", 5400, "1 h 30")), found("Cuire 1 h 30 à feu doux."))
        assertEquals(listOf(Triple("1h15", 4500, "1 h 15")), found("Mijoter 1h15."))
    }

    @Test
    fun other_numbers_are_left_alone() {
        assertTrue(findTimers("Couper en dés de 1 cm, ajouter 5 sachets, 180 °C, pour 4 personnes, 2 hamburgers.").isEmpty())
    }
}

class StepHeadingTest {
    @Test
    fun a_titled_step_keeps_its_title() {
        assertEquals("Lancer le riz", stepHeading(Step("Lancer le riz", "Rincer le riz."), current = true))
        assertEquals("Lancer le riz", stepHeading(Step("Lancer le riz", "Rincer le riz."), current = false))
    }

    @Test
    fun an_untitled_step_shows_its_first_line_only_when_folded() {
        val step = Step("", "Épluchez l'oignon.\nCoupez la carotte.")
        assertEquals("Épluchez l'oignon.", stepHeading(step, current = false))
        assertEquals("", stepHeading(step, current = true)) // its full text is shown below: no repeated line
    }
}
