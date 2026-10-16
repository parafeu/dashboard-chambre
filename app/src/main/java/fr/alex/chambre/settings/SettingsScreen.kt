package fr.alex.chambre.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.alex.chambre.ha.ConnState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    initial: Settings,
    conn: ConnState,
    onSave: (Settings) -> Unit,
    onClose: () -> Unit,
) {
    var url by remember(initial) { mutableStateOf(initial.url) }
    var token by remember(initial) { mutableStateOf(initial.token) }
    var delay by remember(initial) { mutableStateOf(initial.sleepDelaySec.toString()) }
    var sensitivity by remember(initial) { mutableFloatStateOf(initial.sensitivity) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Réglages", style = MaterialTheme.typography.displaySmall)
                Text(
                    when (conn) {
                        ConnState.CONNECTED -> "Connecté à Home Assistant"
                        ConnState.CONNECTING -> "Connexion…"
                        ConnState.OFFLINE -> if (initial.token.isBlank()) "Saisis l'URL et un jeton longue durée" else "Home Assistant injoignable"
                        ConnState.AUTH_FAILED -> "Jeton refusé"
                    },
                    color = if (conn == ConnState.AUTH_FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("URL Home Assistant") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, label = { Text("Jeton longue durée") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = delay, onValueChange = { v -> delay = v.filter(Char::isDigit) },
                    label = { Text("Mise en veille après (secondes)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Column {
                    Text("Sensibilité caméra : ${(sensitivity * 1000).roundToInt() / 10f} % de l'image")
                    Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.005f..0.1f)
                    Text(
                        "Plus la valeur est basse, plus la caméra réveille l'écran facilement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onClose) { Text("Fermer") }
                    Button(onClick = {
                        onSave(
                            Settings(
                                url = url,
                                token = token,
                                sleepDelaySec = delay.toIntOrNull()?.coerceAtLeast(5) ?: initial.sleepDelaySec,
                                sensitivity = sensitivity,
                            ),
                        )
                    }) { Text("Enregistrer et connecter") }
                }
            }
        }
    }
}
