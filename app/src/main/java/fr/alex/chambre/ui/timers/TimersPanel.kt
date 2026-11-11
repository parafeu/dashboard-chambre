package fr.alex.chambre.ui.timers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alex.chambre.timers.SharedTimer
import fr.alex.chambre.timers.formatClock
import fr.alex.chambre.timers.isRinging
import fr.alex.chambre.timers.remainingSec
import fr.alex.chambre.ui.DashboardViewModel
import kotlinx.coroutines.launch
import java.time.Instant

/** The « Minuteries » panel of the kitchen screen (kitchen tablet, salon « Cuisine » tab). Mockup A. */
@Composable
fun TimersPanel(vm: DashboardViewModel, modifier: Modifier = Modifier) {
    val timers by vm.timers.timers.collectAsStateWithLifecycle()
    val now by vm.timers.now.collectAsStateWithLifecycle()
    val online by vm.timers.online.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    // A dialog is its own window, above the ringing overlay: close it so « Arrêter » is never hidden.
    val ringingHere by vm.ringingShown.collectAsStateWithLifecycle()
    LaunchedEffect(ringingHere.isNotEmpty()) { if (ringingHere.isNotEmpty()) dialog = false }
    val colors = MaterialTheme.colorScheme

    Surface(modifier, shape = RoundedCornerShape(32.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Minuteries", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!online) Text("Hors ligne", style = MaterialTheme.typography.labelLarge, color = colors.error)
            }
            Box(Modifier.weight(1f).fillMaxWidth().alpha(if (online) 1f else 0.5f)) {
                if (timers.isEmpty()) {
                    Text(
                        "Aucune minuterie",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(timers, key = { it.id }) { t ->
                            if (t.isRinging(now)) {
                                RingingCard(t, onStop = { vm.timers.stop(t.id) })
                            } else {
                                RunningCard(
                                    t, now, enabled = online,
                                    onAddMinute = { scope.launch { vm.timers.addMinute(t.id) } },
                                    onDelete = { scope.launch { vm.timers.delete(t.id) } },
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { dialog = true },
                enabled = online,
                border = BorderStroke(1.5.dp, colors.outline),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("+ Nouvelle minuterie", fontSize = 18.sp) }
        }
    }

    if (dialog) {
        NewTimerDialog(
            onDismiss = { dialog = false },
            onStart = { minutes, name ->
                dialog = false
                scope.launch { vm.timers.startFree(minutes, name) }
            },
        )
    }
}

@Composable
private fun RingingCard(t: SharedTimer, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = colors.primary, contentColor = colors.onPrimary) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(22.dp))
                Text(t.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("Terminée", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Arrêter", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RunningCard(t: SharedTimer, now: Instant, enabled: Boolean, onAddMinute: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val left = t.remainingSec(now)
    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.origin.isNotBlank()) {
                        Text(t.origin, fontSize = 14.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(formatClock(left), fontSize = 38.sp, color = colors.primary, style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"))
            }
            val total = t.durationSec.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (1f - left.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = colors.primary,
                trackColor = colors.outlineVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAddMinute, enabled = enabled, modifier = Modifier.weight(1f).height(44.dp)) { Text("+1 min", fontSize = 16.sp) }
                FilledTonalIconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Close, "Supprimer la minuterie ${t.name}")
                }
            }
        }
    }
}
