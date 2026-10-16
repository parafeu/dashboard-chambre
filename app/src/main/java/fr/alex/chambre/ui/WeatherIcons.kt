package fr.alex.chambre.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a Home Assistant weather condition to an icon. */
fun weatherIcon(condition: String): ImageVector = when (condition) {
    "sunny" -> Icons.Filled.WbSunny
    "clear-night" -> Icons.Filled.NightsStay
    "partlycloudy" -> Icons.Filled.WbCloudy
    "cloudy" -> Icons.Filled.Cloud
    "rainy", "pouring" -> Icons.Filled.Umbrella
    "lightning", "lightning-rainy" -> Icons.Filled.Thunderstorm
    "snowy", "snowy-rainy", "hail" -> Icons.Filled.AcUnit
    "fog" -> Icons.Filled.BlurOn
    "windy", "windy-variant" -> Icons.Filled.Air
    else -> Icons.Filled.Warning
}

/** French label for the current condition. */
fun weatherLabel(condition: String): String = when (condition) {
    "sunny" -> "Ensoleillé"
    "clear-night" -> "Dégagé"
    "partlycloudy" -> "Éclaircies"
    "cloudy" -> "Nuageux"
    "rainy" -> "Pluie"
    "pouring" -> "Averses"
    "lightning", "lightning-rainy" -> "Orage"
    "snowy" -> "Neige"
    "snowy-rainy" -> "Pluie et neige"
    "hail" -> "Grêle"
    "fog" -> "Brouillard"
    "windy", "windy-variant" -> "Venteux"
    else -> ""
}

private val DAY = java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.FRENCH)

/** « Auj. » for today, otherwise the short French day name (« Sam. »). */
fun dayLabel(day: java.time.LocalDate, today: java.time.LocalDate): String =
    if (day == today) "Auj." else DAY.format(day).replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }
