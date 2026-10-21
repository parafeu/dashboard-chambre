package fr.alex.chambre.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotsTest {
    @Test
    fun `a snapshot just published is reused instead of asking HA again`() {
        val snap = WidgetSnapshot(emptyMap(), stale = false, configured = true)
        WidgetSnapshots.publish(WidgetRoom.CUISINE, snap, at = 1_000)
        assertEquals(snap, WidgetSnapshots.recent(WidgetRoom.CUISINE, now = 4_000))
        assertNull(WidgetSnapshots.recent(WidgetRoom.CUISINE, now = 7_000))
    }
}
