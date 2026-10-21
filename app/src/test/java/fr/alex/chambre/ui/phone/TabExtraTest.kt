package fr.alex.chambre.ui.phone

import fr.alex.chambre.ui.salon.SalonTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TabExtraTest {
    @Test
    fun `widget extras pick the room tab`() {
        assertEquals(SalonTab.CUISINE, tabFromExtra("cuisine"))
        assertEquals(SalonTab.CHAMBRE, tabFromExtra("chambre"))
        assertEquals(SalonTab.SALON, tabFromExtra("salon"))
    }

    @Test
    fun `no or unknown extra opens the living room`() {
        assertEquals(SalonTab.SALON, tabFromExtra(null))
        assertEquals(SalonTab.SALON, tabFromExtra("x"))
    }
}
