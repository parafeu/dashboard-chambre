package fr.alex.chambre.ha

import fr.alex.chambre.settings.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitiesTest {
    @Test
    fun chambre_keeps_the_v1_list() {
        assertEquals(Entities.ALL, entitiesFor(Room.CHAMBRE, "calendar.travail", "calendar.alertes"))
    }

    @Test
    fun salon_covers_every_room_and_both_calendars() {
        val l = entitiesFor(Room.SALON, "calendar.travail", "calendar.alertes")
        for (e in listOf("cover.volet_salon", "light.lumieres_salon", "cover.volet_cuisine", "timer.minuteur_2",
            "todo.liste_dachats", "switch.grillplats_plug", "sensor.salon_temperature", "calendar.alertes",
            "calendar.travail", Entities.LIGHT, Entities.COVER, Entities.WEATHER)) {
            assertTrue(e, e in l)
        }
        assertEquals(l.size, l.toSet().size)
    }

    @Test
    fun salon_room_has_no_half_script() {
        assertEquals("cover.volet_salon", Entities.SALON_ROOM.cover)
        assertEquals(null, Entities.SALON_ROOM.halfScript)
        assertEquals(Entities.HALF, Entities.CHAMBRE_ROOM.halfScript)
        assertFalse(Entities.SALON_ROOM.colors)
        assertTrue(Entities.CHAMBRE_ROOM.colors)
    }
}
