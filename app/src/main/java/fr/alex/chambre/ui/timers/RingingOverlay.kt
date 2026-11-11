package fr.alex.chambre.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alex.chambre.mealie.timerLabel
import fr.alex.chambre.timers.SharedTimer
import fr.alex.chambre.timers.formatClock
import fr.alex.chambre.ui.DashboardViewModel
import java.time.Duration
import java.time.Instant

/** Full screen while a shared timer rings on this tablet (any room, any screen). Mockup « Sonnerie ». */
@Composable
fun RingingOverlay(vm: DashboardViewModel) {
    val ringing by vm.ringingShown.collectAsStateWithLifecycle()
    val now by vm.timers.now.collectAsStateWithLifecycle()
    if (ringing.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Sibling backdrop swallowing taps, so the controls underneath never react (like the salon alert screen).
        Box(
            Modifier.fillMaxSize().background(Color(0xDB0D0906)).pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                }
            },
        )
        Column(Modifier.widthIn(max = 760.dp).padding(40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val first = ringing.first()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(40.dp), tint = colors.primary)
                Text(
                    "Minuterie terminée" + first.origin.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    fontSize = 22.sp, fontWeight = FontWeight.Medium, color = colors.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(shape = RoundedCornerShape(32.dp), color = colors.primary, contentColor = colors.onPrimary) {
                Row(Modifier.padding(horizontal = 32.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(first.name, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(details(first, now), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { vm.timers.stop(first.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.onPrimaryContainer),
                        modifier = Modifier.height(96.dp),
                    ) { Text("Arrêter", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
            }
            ringing.drop(1).forEach { t ->
                Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 30.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(details(t, now), fontSize = 16.sp, color = colors.onSurfaceVariant)
                        }
                        Button(onClick = { vm.timers.stop(t.id) }, modifier = Modifier.height(72.dp)) {
                            Text("Arrêter", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
            Text(
                "Arrêter ici l'arrête sur toutes les tablettes.",
                fontSize = 16.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

/** « 10 min · sonne depuis 0:23 » */
private fun details(t: SharedTimer, now: Instant): String {
    val since = Duration.between(t.end, now).seconds.coerceAtLeast(0)
    return timerLabel(t.durationSec) + " · sonne depuis " + formatClock(since)
}
