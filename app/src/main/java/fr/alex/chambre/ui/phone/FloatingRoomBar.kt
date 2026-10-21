package fr.alex.chambre.ui.phone

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.alex.chambre.ui.salon.SalonTab

private val ROOMS = listOf(
    Triple(SalonTab.SALON, "Salon", Icons.Filled.Chair),
    Triple(SalonTab.CUISINE, "Cuisine", Icons.Filled.Kitchen),
    Triple(SalonTab.CHAMBRE, "Chambre", Icons.Filled.Bed),
)

/** Floating pill at the bottom of the phone screen: the room icons; only the selected room shows its name. */
@Composable
fun FloatingRoomBar(selected: SalonTab, onSelect: (SalonTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ROOMS.forEach { (tab, label, icon) -> RoomPill(label, icon, tab == selected) { onSelect(tab) } }
        }
    }
}

@Composable
private fun RoomPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "pill-bg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "pill-fg")
    Surface(onClick = onClick, shape = RoundedCornerShape(26.dp), color = bg, contentColor = fg, modifier = Modifier.height(52.dp)) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 16.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, label, Modifier.size(24.dp).align(Alignment.CenterVertically))
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
