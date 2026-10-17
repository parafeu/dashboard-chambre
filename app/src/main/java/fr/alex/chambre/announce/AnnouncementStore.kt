package fr.alex.chambre.announce

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/** Announcements already made or confirmed, kept across restarts for two days. */
class AnnouncementStore(private val dataStore: DataStore<Preferences>) {

    suspend fun done(): Set<String> =
        (dataStore.data.first()[DONE] ?: emptySet()).mapNotNull { decode(it)?.second }.toSet()

    suspend fun markDone(key: String, at: Instant) {
        val horizon = at.minus(KEEP)
        dataStore.edit { p ->
            val kept = (p[DONE] ?: emptySet()).filter { e -> decode(e)?.first?.isAfter(horizon) == true }
            p[DONE] = (kept + encode(at, key)).toSet()
        }
    }

    // "<epoch seconds> <key>": the key itself may contain any character.
    private fun encode(at: Instant, key: String) = "${at.epochSecond} $key"

    private fun decode(e: String): Pair<Instant, String>? {
        val space = e.indexOf(' ')
        val sec = e.substring(0, maxOf(space, 0)).toLongOrNull() ?: return null
        return Instant.ofEpochSecond(sec) to e.substring(space + 1)
    }

    private companion object {
        val DONE = stringSetPreferencesKey("announcements_done")
        val KEEP: Duration = Duration.ofDays(2)
    }
}
