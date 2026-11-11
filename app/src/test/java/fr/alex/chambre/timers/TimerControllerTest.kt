package fr.alex.chambre.timers

import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.ForecastEntry
import fr.alex.chambre.ha.HaApi
import fr.alex.chambre.ha.Incoming
import fr.alex.chambre.ha.TodoItem
import fr.alex.chambre.settings.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private data class Call(val service: String, val entityId: String, val data: JsonObject, val quiet: Boolean)

private class FakeHa : HaApi {
    override val conn = MutableStateFlow(ConnState.CONNECTED)
    override val entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    override val forecast = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val forecastDaily = MutableStateFlow<List<ForecastEntry>>(emptyList())
    override val errors = MutableSharedFlow<String>()
    override val timerItems = MutableStateFlow<List<TodoItem>?>(emptyList())
    val calls = mutableListOf<Call>()
    var answer = true

    override suspend fun call(domain: String, service: String, entityId: String, data: JsonObject): Boolean {
        calls += Call("$domain.$service", entityId, data, quiet = false)
        return answer
    }

    override suspend fun callQuietly(domain: String, service: String, entityId: String, data: JsonObject): Boolean {
        calls += Call("$domain.$service", entityId, data, quiet = true)
        return answer
    }

    fun offline() {
        conn.value = ConnState.OFFLINE
        timerItems.value = null
    }

    fun online(items: List<TodoItem>) {
        conn.value = ConnState.CONNECTED
        timerItems.value = items
    }
}

private class MemCache(var items: List<TodoItem> = emptyList(), var stoppedIds: Set<String> = emptySet()) : TimerCache {
    override suspend fun lastKnown() = items
    override suspend fun saveLastKnown(items: List<TodoItem>) { this.items = items }
    override suspend fun stopped() = stoppedIds
    override suspend fun saveStopped(ids: Set<String>) { stoppedIds = ids }
}

private fun JsonObject.text(key: String) = (this[key] as JsonPrimitive).content

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private val t0 = Instant.parse("2026-11-11T12:00:00Z")
    private var clock = t0
    private val ha = FakeHa()
    private val cache = MemCache()

    private fun TestScope.controller(room: Room = Room.CUISINE) =
        TimerController(backgroundScope, ha, cache, room = { room }, clock = { clock }, tickMs = 1_000).also { runCurrent() }

    private fun item(uid: String, endIn: Long, name: String = uid) =
        TodoItem(uid, name, timerDescription(t0.plusSeconds(endIn), 600, "", "cuisine"))

    private fun TestScope.tick(seconds: Long) {
        clock = clock.plusSeconds(seconds)
        advanceTimeBy(seconds * 1_000 + 1)
    }

    @Test
    fun start_adds_item_with_end_and_origin() = runTest {
        val c = controller()
        assertTrue(c.start(600, "Pâtes", "Lasagnes · étape 4"))
        val call = ha.calls.single()
        assertEquals("todo.add_item", call.service)
        assertEquals(Entities.TIMERS_LIST, call.entityId)
        assertEquals("Pâtes", call.data.text("item"))
        val parsed = parseTimer(TodoItem("x", "Pâtes", call.data.text("description")))!!
        assertEquals(Instant.parse("2026-11-11T12:10:00Z"), parsed.end)
        assertEquals(600, parsed.durationSec)
        assertEquals("Lasagnes · étape 4", parsed.origin)
    }

    @Test
    fun free_timer_from_kitchen() = runTest {
        val c = controller(Room.CUISINE)
        assertTrue(c.startFree(5, " "))
        val call = ha.calls.single()
        assertEquals("Minuterie", call.data.text("item"))
        val parsed = parseTimer(TodoItem("x", "Minuterie", call.data.text("description")))!!
        assertEquals("Lancée en cuisine", parsed.origin)
        assertEquals(300, parsed.durationSec)
    }

    @Test
    fun timer_rings_at_end() = runTest {
        ha.timerItems.value = listOf(item("a", 600))
        val c = controller()
        tick(599)
        assertEquals(listOf("a"), c.timers.value.map { it.id })
        assertTrue(c.ringing.value.isEmpty())
        tick(1)
        assertEquals(listOf("a"), c.ringing.value.map { it.id })
    }

    @Test
    fun timer_already_ended_on_load_rings() = runTest {
        ha.timerItems.value = listOf(item("a", -30))
        val c = controller()
        assertEquals(listOf("a"), c.ringing.value.map { it.id })
    }

    @Test
    fun add_minute_pushes_end() = runTest {
        ha.timerItems.value = listOf(item("a", 600))
        val c = controller()
        assertTrue(c.addMinute("a"))
        val call = ha.calls.single()
        assertEquals("todo.update_item", call.service)
        assertEquals("a", call.data.text("item"))
        val parsed = parseTimer(TodoItem("a", "a", call.data.text("description")))!!
        assertEquals(t0.plusSeconds(660), parsed.end)
        assertEquals(660, parsed.durationSec)
    }

    @Test
    fun delete_removes_item() = runTest {
        ha.timerItems.value = listOf(item("a", 600))
        val c = controller()
        assertTrue(c.delete("a"))
        assertEquals(Call("todo.remove_item", Entities.TIMERS_LIST, JsonObject(mapOf("item" to JsonPrimitive("a"))), quiet = false), ha.calls.single())
    }

    @Test
    fun stop_hides_at_once_and_removes() = runTest {
        ha.timerItems.value = listOf(item("a", -5))
        val c = controller()
        c.stop("a")
        runCurrent()
        assertTrue(c.timers.value.isEmpty())
        assertTrue(c.ringing.value.isEmpty())
        val call = ha.calls.single()
        assertEquals("todo.remove_item", call.service)
        assertEquals("a", call.data.text("item"))
    }

    @Test
    fun stop_of_an_already_removed_timer_is_silent() = runTest {
        ha.timerItems.value = listOf(item("a", -5))
        ha.answer = false
        val c = controller()
        c.stop("a")
        runCurrent()
        assertTrue(ha.calls.single().quiet)
        assertTrue(c.ringing.value.isEmpty())
    }

    @Test
    fun stopped_offline_is_removed_on_reconnect() = runTest {
        ha.timerItems.value = listOf(item("a", -5))
        val c = controller()
        ha.offline()
        runCurrent()
        c.stop("a")
        runCurrent()
        assertTrue(ha.calls.isEmpty())
        assertEquals(setOf("a"), cache.stoppedIds)
        ha.online(listOf(item("a", -5)))
        runCurrent()
        assertEquals("todo.remove_item", ha.calls.single().service)
    }

    @Test
    fun locally_stopped_timer_does_not_ring_again() = runTest {
        ha.timerItems.value = listOf(item("a", -5))
        val c = controller()
        ha.offline()
        runCurrent()
        c.stop("a")
        ha.online(listOf(item("a", -5)))
        tick(1)
        assertTrue(c.ringing.value.isEmpty())
        ha.online(emptyList()) // HA removed it: the remembered stop is forgotten
        runCurrent()
        assertTrue(cache.stoppedIds.isEmpty())
    }

    @Test
    fun offline_uses_last_known_list() = runTest {
        ha.timerItems.value = listOf(item("a", 600))
        val c = controller()
        assertTrue(c.online.value)
        ha.offline()
        runCurrent()
        assertFalse(c.online.value)
        assertEquals(listOf("a"), c.timers.value.map { it.id })
        assertFalse(c.start(60, "x", "y"))
        assertTrue(ha.calls.isEmpty())
    }

    @Test
    fun restart_offline_reads_cache() = runTest {
        cache.items = listOf(item("a", 2))
        ha.offline()
        val c = controller()
        assertEquals(listOf("a"), c.timers.value.map { it.id })
        tick(2)
        assertEquals(listOf("a"), c.ringing.value.map { it.id })
    }

    @Test
    fun foreign_items_are_not_timers() = runTest {
        ha.timerItems.value = listOf(TodoItem("z", "acheter du pain", null), item("a", 60))
        val c = controller()
        assertEquals(listOf("a"), c.timers.value.map { it.id })
    }
}
