package fr.alex.chambre.settings

import fr.alex.chambre.ha.entitiesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFeaturesTest {
    @Test
    fun `phone below 600 dp`() {
        assertEquals(FormFactor.PHONE, formFactorFor(599))
        assertEquals(FormFactor.TABLET, formFactorFor(600))
    }

    @Test
    fun `tablet keeps today's behaviour per room`() {
        val chambre = deviceFeatures(Settings(room = Room.CHAMBRE, autoStart = true), FormFactor.TABLET)
        assertEquals(entitiesFor(Room.CHAMBRE), chambre.entities)
        assertFalse(chambre.announcements)
        assertFalse(chambre.mealie)
        assertTrue(chambre.bootStart)

        val salon = deviceFeatures(Settings(room = Room.SALON), FormFactor.TABLET)
        assertEquals(entitiesFor(Room.SALON), salon.entities)
        assertTrue(salon.announcements)
        assertTrue(salon.mealie)
        assertFalse(salon.bootStart)

        val cuisine = deviceFeatures(Settings(room = Room.CUISINE), FormFactor.TABLET)
        assertEquals(entitiesFor(Room.CUISINE), cuisine.entities)
        assertTrue(cuisine.mealie)
        assertFalse(cuisine.announcements)
    }

    @Test
    fun `every tablet follows the shared timers, the panel only where there is a kitchen screen`() {
        val chambre = deviceFeatures(Settings(room = Room.CHAMBRE), FormFactor.TABLET)
        assertTrue(chambre.timers)
        assertFalse(chambre.timerPanel)
        listOf(Room.SALON, Room.CUISINE).forEach { room ->
            val f = deviceFeatures(Settings(room = room), FormFactor.TABLET)
            assertTrue(f.timers)
            assertTrue(f.timerPanel)
        }
    }

    @Test
    fun `phone stays phone whatever the room`() {
        Room.entries.forEach { room ->
            val f = deviceFeatures(Settings(room = room, autoStart = true), FormFactor.PHONE)
            assertEquals(entitiesFor(Room.SALON), f.entities)
            assertFalse(f.timers)
            assertFalse(f.timerPanel)
            assertFalse(f.announcements)
            assertTrue(f.mealie)
            assertFalse(f.bootStart)
        }
    }
}
