package fr.alex.chambre.widget

import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.brightnessPct
import fr.alex.chambre.ha.coverPosition
import fr.alex.chambre.ha.isUnavailable

/** The three home-screen widgets: the room's light (none in the kitchen) and shutter; [tab] opens the app on it. */
enum class WidgetRoom(val tab: String, val title: String, val light: String?, val cover: String) {
    CHAMBRE("chambre", "Chambre", Entities.CHAMBRE_ROOM.light, Entities.CHAMBRE_ROOM.cover),
    SALON("salon", "Salon", Entities.SALON_ROOM.light, Entities.SALON_ROOM.cover),
    CUISINE("cuisine", "Cuisine", null, Entities.KITCHEN_COVER),
}

private fun EntityState?.known(): EntityState? = this?.takeUnless { it.isUnavailable }

fun lightIsOn(light: EntityState?): Boolean = light.known()?.state == "on"

/** « 40 % », « Éteinte », or « – » when Home Assistant gave nothing. */
fun lightText(light: EntityState?): String {
    val l = light.known() ?: return "–"
    return if (l.state == "on") "${l.brightnessPct ?: 0} %" else "Éteinte"
}

/** The brightness « − » / « + » ask for: ±[deltaPct] from now, an off light comes on at the minimum, 10–100 %. */
fun stepBrightness(light: EntityState?, deltaPct: Int): Int {
    if (!lightIsOn(light)) return MIN_PCT
    val now = light?.brightnessPct ?: MIN_PCT
    return (now + deltaPct).coerceIn(MIN_PCT, 100)
}

private const val MIN_PCT = 10

private fun moving(cover: EntityState?): Boolean = cover.known()?.state.let { it == "opening" || it == "closing" }

fun coverIsOpen(cover: EntityState?): Boolean {
    val c = cover.known() ?: return false
    val p = c.coverPosition
    return if (p != null) p > 0 else c.state == "open" || c.state == "opening"
}

/** « Ouvert », « Fermé », « Ouvert à 35 % », « Ouverture… », « Fermeture… », or « – ». */
fun coverText(cover: EntityState?): String {
    val c = cover.known() ?: return "–"
    if (c.state == "opening") return "Ouverture…"
    if (c.state == "closing") return "Fermeture…"
    return when (val p = c.coverPosition) {
        null -> if (c.state == "closed") "Fermé" else "Ouvert"
        100 -> "Ouvert"
        0 -> "Fermé"
        else -> "Ouvert à $p %"
    }
}

enum class CoverTap { OPEN, CLOSE, STOP }

/** A tap on the shutter tile: stop it if it moves, else close it if open, else open it. */
fun coverTap(cover: EntityState?): CoverTap = when {
    moving(cover) -> CoverTap.STOP
    coverIsOpen(cover) -> CoverTap.CLOSE
    else -> CoverTap.OPEN
}

/** « 21,4 °C · 52 % » for the living-room title; empty when neither sensor has a value. */
fun climateText(temperature: EntityState?, humidity: EntityState?): String {
    val t = temperature.known()?.state?.toDoubleOrNull()?.let { "%.1f °C".format(java.util.Locale.ROOT, it).replace('.', ',') }
    val h = humidity.known()?.state?.toDoubleOrNull()?.let { "${it.toInt()} %" }
    return listOfNotNull(t, h).joinToString(" · ")
}
