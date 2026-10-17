package fr.alex.chambre.announce

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class AnnouncementStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    private fun store() = AnnouncementStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("a.preferences_pb") })

    @Test
    fun keys_round_trip_even_with_separators_in_titles() = runBlocking {
        val s = store()
        val key = "calendar.alertes|Litière | chat|2026-10-17T17:00:00Z"
        s.markDone(key, Instant.parse("2026-10-17T17:05:00Z"))
        assertEquals(setOf(key), s.done())
    }

    @Test
    fun keys_older_than_two_days_are_purged() = runBlocking {
        val s = store()
        s.markDone("old", Instant.parse("2026-10-14T10:00:00Z"))
        s.markDone("new", Instant.parse("2026-10-17T10:00:00Z"))
        assertEquals(setOf("new"), s.done())
    }
}
