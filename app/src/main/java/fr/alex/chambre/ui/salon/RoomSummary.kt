package fr.alex.chambre.ui.salon

import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.brightnessPct
import fr.alex.chambre.ha.coverPosition

private fun coverText(cover: EntityState?): String? = cover?.let {
    val p = it.coverPosition
    when {
        p == null -> if (it.state == "closed") "volet fermé" else "volet ouvert"
        p >= 100 -> "volet ouvert"
        p <= 0 -> "volet fermé"
        else -> "volet à $p %"
    }
}

/** « Lumières à 65 % · volet ouvert » — the line under a room in the navigation rail. */
fun roomSummary(light: EntityState?, cover: EntityState?, plural: Boolean): String {
    val l = light?.let {
        if (it.state == "on") (if (plural) "Lumières à " else "Lumière à ") + (it.brightnessPct ?: 0) + " %"
        else if (plural) "Lumières éteintes" else "Lumière éteinte"
    }
    return listOfNotNull(l, coverText(cover)).joinToString(" · ").ifEmpty { "–" }
}

/** « Volet ouvert · 2 minuteurs en cours » */
fun kitchenSummary(cover: EntityState?, timers: List<EntityState?>): String {
    val running = timers.count { it?.state == "active" || it?.state == "paused" }
    val t = when (running) {
        0 -> "minuteurs prêts"
        1 -> "1 minuteur en cours"
        else -> "$running minuteurs en cours"
    }
    return listOfNotNull(coverText(cover)?.replaceFirstChar { it.uppercase() }, t).joinToString(" · ")
}
