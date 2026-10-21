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
        assertFalse(chambre.timerEvents)
        assertFalse(chambre.announcements)
        assertTrue(chambre.camera)
        assertTrue(chambre.presence)
        assertTrue(chambre.batteryReport)
        assertFalse(chambre.mealie)
        assertTrue(chambre.kiosk)
        assertTrue(chambre.bootStart)

        val salon = deviceFeatures(Settings(room = Room.SALON), FormFactor.TABLET)
        assertEquals(entitiesFor(Room.SALON), salon.entities)
        assertTrue(salon.timerEvents)
        assertTrue(salon.announcements)
        assertTrue(salon.mealie)
        assertFalse(salon.bootStart)

        val cuisine = deviceFeatures(Settings(room = Room.CUISINE), FormFactor.TABLET)
        assertEquals(entitiesFor(Room.CUISINE), cuisine.entities)
        assertTrue(cuisine.mealie)
        assertFalse(cuisine.announcements)
    }

    @Test
    fun `phone stays phone whatever the room`() {
        Room.entries.forEach { room ->
            val f = deviceFeatures(Settings(room = room, autoStart = true), FormFactor.PHONE)
            assertEquals(entitiesFor(Room.SALON), f.entities)
            assertFalse(f.timerEvents)
            assertFalse(f.announcements)
            assertFalse(f.batteryReport)
            assertFalse(f.camera)
            assertFalse(f.presence)
            assertTrue(f.mealie)
            assertFalse(f.bootStart)
            assertFalse(f.kiosk)
        }
    }
}
