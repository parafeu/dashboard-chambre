package fr.alex.chambre.ui.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** « Nouvelle minuterie »: a free duration in whole minutes and an optional name. */
@Composable
fun NewTimerDialog(onDismiss: () -> Unit, onStart: (minutes: Int, name: String) -> Unit) {
    var state by remember { mutableStateOf(NewTimerState()) }
    var name by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(32.dp), color = colors.surfaceContainer, modifier = Modifier.width(720.dp)) {
            Column(Modifier.padding(horizontal = 36.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Nouvelle minuterie", style = MaterialTheme.typography.headlineSmall)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = { state = state.minus() }, modifier = Modifier.size(84.dp)) {
                        Icon(Icons.Filled.Remove, "Moins une minute", Modifier.size(36.dp))
                    }
                    Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.minutes}:00", fontSize = 96.sp, fontWeight = FontWeight.Light, color = colors.onPrimaryContainer)
                        Text("minutes", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { state = state.plus() }, modifier = Modifier.size(84.dp)) {
                        Icon(Icons.Filled.Add, "Plus une minute", Modifier.size(36.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NEW_TIMER_PRESETS.forEach { m ->
                        FilterChip(
                            selected = state.minutes == m,
                            onClick = { state = state.preset(m) },
                            label = { Text("$m min", fontSize = 18.sp, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f).height(56.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nom (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(60.dp)) { Text("Annuler", fontSize = 18.sp) }
                    Button(onClick = { onStart(state.minutes, name) }, modifier = Modifier.height(60.dp)) {
                        Text("Lancer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
