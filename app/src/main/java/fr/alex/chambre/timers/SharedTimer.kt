package fr.alex.chambre.timers

import fr.alex.chambre.ha.TodoItem
import fr.alex.chambre.settings.Room
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant

/** A kitchen timer shared by every wall tablet, stored as one item of the HA to-do list `todo.minuteries_foyer`. */
data class SharedTimer(
    val id: String,
    val name: String,
    val end: Instant,
    val durationSec: Int,
    /** « Lasagnes · étape 4 », « Lancée en cuisine »… */
    val origin: String,
)

const val DEFAULT_TIMER_NAME = "Minuterie"

/** The item's description: `{"v":1,"end":"<ISO UTC>","duration":600,"origin":"…","room":"cuisine"}`. */
fun timerDescription(end: Instant, durationSec: Int, origin: String, room: String): String = buildJsonObject {
    put("v", 1)
    put("end", end.toString())
    put("duration", durationSec)
    put("origin", origin)
    put("room", room)
}.toString()

/** null for anything that is not one of our timers (e.g. an item typed by hand in HA's to-do panel). */
fun parseTimer(item: TodoItem): SharedTimer? {
    val o = item.description?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return null
    if ((o["v"] as? JsonPrimitive)?.intOrNull != 1) return null
    val endText = (o["end"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val end = runCatching { Instant.parse(endText) }.getOrNull() ?: return null
    val duration = (o["duration"] as? JsonPrimitive)?.intOrNull ?: return null
    val origin = (o["origin"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    return SharedTimer(item.uid, item.summary.trim().ifEmpty { DEFAULT_TIMER_NAME }, end, duration, origin)
}

fun SharedTimer.isRinging(now: Instant): Boolean = !now.isBefore(end)

/** Whole seconds left, rounded up; 0 once ended. */
fun SharedTimer.remainingSec(now: Instant): Long {
    val ms = end.toEpochMilli() - now.toEpochMilli()
    return if (ms <= 0) 0 else (ms + 999) / 1000
}

/** Ringing timers first (the one ringing longest first), then running ones, soonest end first. */
fun sortTimers(timers: List<SharedTimer>, now: Instant): List<SharedTimer> =
    timers.sortedWith(compareBy<SharedTimer> { !it.isRinging(now) }.thenBy { it.end })

/** `4:12`, `27:05`, `1:02:03`. */
fun formatClock(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Origin line of a timer started with « + Nouvelle minuterie ». */
fun freeOrigin(room: Room): String = when (room) {
    Room.CUISINE -> "Lancée en cuisine"
    Room.SALON -> "Lancée au salon"
    Room.CHAMBRE -> "Lancée dans la chambre"
}

/** The `room` field of the description. */
fun roomKey(room: Room): String = room.name.lowercase()
