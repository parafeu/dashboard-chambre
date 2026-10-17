package fr.alex.chambre.ui.salon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.isUnavailable
import fr.alex.chambre.ui.CoverPanel
import fr.alex.chambre.ui.DashboardController
import fr.alex.chambre.ui.Section
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** « 4:05 » for a running timer, « 12:30 · pause » when paused, null when idle. */
fun timerRemaining(timer: EntityState, now: Instant): String? {
    fun attr(k: String) = (timer.attributes[k] as? JsonPrimitive)?.contentOrNull
    fun fmt(sec: Long) = "${sec / 60}:${"%02d".format(sec % 60)}"
    return when (timer.state) {
        "active" -> attr("finishes_at")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
            ?.let { fmt(Duration.between(now, it).seconds.coerceAtLeast(0)) }
        "paused" -> attr("remaining")?.split(":")?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 3 }
            ?.let { (h, m, s) -> fmt(h * 3600 + m * 60 + s) + " · pause" }
        else -> null
    }
}

@Composable
fun CuisineScreen(
    entities: Map<String, EntityState>,
    online: Boolean,
    coverController: DashboardController,
    now: Instant,
    onStartTimer: (timer: String, minutes: Int) -> Unit,
    onCancelTimer: (timer: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CoverPanel(entities[Entities.KITCHEN_COVER], online, coverController, Modifier.width(236.dp).fillMaxHeight())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Section("Minuteurs", Modifier.weight(1f)) {
                Entities.KITCHEN_TIMERS.forEach { id ->
                    val t = entities[id]
                    val name = (t?.attributes?.get("friendly_name") as? JsonPrimitive)?.contentOrNull ?: id
                    val left = t?.let { timerRemaining(it, now) }
                    val enabled = online && t != null && !t.isUnavailable
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleLarge)
                                Text(left ?: "Prêt", fontSize = if (left != null) 28.sp else 16.sp, color = if (left != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (left != null) {
                                TextButton(onClick = { onCancelTimer(id) }, enabled = enabled) { Text("Annuler", style = MaterialTheme.typography.titleMedium) }
                            } else {
                                listOf(5, 10).forEach { m ->
                                    FilledTonalButton(onClick = { onStartTimer(id, m) }, enabled = enabled, modifier = Modifier.height(56.dp)) {
                                        Text("$m min", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                InfoTile(Icons.Filled.ShoppingCart, "Liste de courses", shoppingLabel(entities[Entities.SHOPPING_LIST]), Modifier.weight(1f))
                InfoTile(Icons.Filled.Power, "Prise tablette cuisine", plugLabel(entities[Entities.KITCHEN_TABLET_PLUG]), Modifier.weight(1f))
            }
        }
    }
}

private fun shoppingLabel(e: EntityState?): String = when (val n = e?.state?.toIntOrNull()) {
    null -> "–"
    0 -> "Aucun article"
    1 -> "1 article"
    else -> "$n articles"
}

private fun plugLabel(e: EntityState?): String = when (e?.state) {
    "on" -> "Allumée"
    "off" -> "Éteinte"
    else -> "–"
}

@Composable
private fun InfoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, modifier: Modifier) {
    Surface(modifier.height(112.dp), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
