package fr.alex.chambre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.coverPosition
import fr.alex.chambre.ha.isUnavailable
import kotlin.math.roundToInt

/** The slider shows how far the shutter is closed: filled from the top = 100 - position. */
fun closedFraction(position: Int?): Float? = position?.let { (100 - it) / 100f }

fun positionFromClosedFraction(fraction: Float): Int = 100 - (fraction * 100).roundToInt()

@Composable
fun CoverPanel(cover: EntityState?, online: Boolean, controller: DashboardController, modifier: Modifier = Modifier) {
    val enabled = online && cover != null && !cover.isUnavailable
    val position = cover?.coverPosition
    var dragPos by remember { mutableStateOf<Int?>(null) }

    Section("Volet", modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            fr.alex.chambre.ui.components.VerticalSlider(
                value = closedFraction(position),
                onChange = { dragPos = positionFromClosedFraction(it) },
                onRelease = {
                    dragPos = null
                    controller.setCoverPosition(positionFromClosedFraction(it))
                },
                fillFromTop = true,
                fillColor = MaterialTheme.colorScheme.tertiary,
                iconTint = MaterialTheme.colorScheme.onTertiary,
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                CoverButton(Icons.Filled.ArrowUpward, "Ouvrir", enabled, controller::coverOpen)
                CoverButton(Icons.Filled.Stop, "Stop", enabled, controller::coverStop)
                CoverButton(Icons.Filled.ArrowDownward, "Fermer", enabled, controller::coverClose)
                CoverButton(Icons.Filled.VerticalAlignCenter, "Mi-hauteur", enabled, controller::coverHalf)
            }
        }
        Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(
                (dragPos ?: position)?.let { p ->
                    when (p) {
                        100 -> "Ouvert"
                        0 -> "Fermé"
                        else -> "Ouvert à $p %"
                    }
                } ?: cover?.state?.let { if (it == "opening") "Ouverture…" else if (it == "closing") "Fermeture…" else "–" } ?: "–",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun CoverButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.size(width = 80.dp, height = 72.dp),
    ) { Icon(icon, label, Modifier.size(32.dp)) }
}
