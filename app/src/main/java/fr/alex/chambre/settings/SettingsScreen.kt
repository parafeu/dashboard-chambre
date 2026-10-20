package fr.alex.chambre.settings

import fr.alex.chambre.boot.bootStart
import fr.alex.chambre.boot.BootStart
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Switch
import android.provider.Settings as AndroidSettings
import android.os.Build
import android.net.Uri
import android.content.Intent
import fr.alex.chambre.announce.DeviceCalendar
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.alex.chambre.battery.BatteryState
import fr.alex.chambre.battery.ReportStatus
import fr.alex.chambre.ha.ConnState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sleep delay choices, in seconds. */
val SLEEP_STEPS = listOf(10, 20, 30, 45, 60, 90, 120, 180, 300, 600)

fun nearestStep(seconds: Int): Int = SLEEP_STEPS.indices.minBy { abs(SLEEP_STEPS[it] - seconds) }

fun formatDelay(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60}"
}

@Composable
fun SettingsScreen(
    initial: Settings,
    conn: ConnState,
    battery: BatteryState?,
    report: ReportStatus,
    preview: Bitmap?,
    onSave: (Settings) -> Unit,
    onClose: () -> Unit,
    calendarError: String? = null,
    onTestVoice: () -> Unit = {},
    calendars: List<DeviceCalendar>? = emptyList(),
    onAllowCalendars: () -> Unit = {},
) {
    var url by remember(initial) { mutableStateOf(initial.url) }
    var token by remember(initial) { mutableStateOf(initial.token) }
    var delayIdx by remember(initial) { mutableIntStateOf(nearestStep(initial.sleepDelaySec)) }
    var minArea by remember(initial) { mutableFloatStateOf(initial.sensitivity) }
    var cameraApi by remember(initial) { mutableStateOf(initial.cameraApi) }
    var autoStart by remember(initial) { mutableStateOf(initial.autoStart) }
    val context = LocalContext.current
    var canOverlay by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    // Coming back from Android's permission screen: check again.
    LifecycleResumeEffect(Unit) {
        canOverlay = AndroidSettings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    var room by remember(initial) { mutableStateOf(initial.room) }
    val deviceSlug = if (initial.room == Room.SALON) "salon" else "chambre"
    var workCal by remember(initial) { mutableStateOf(initial.workCalendar) }
    var alertsCal by remember(initial) { mutableStateOf(initial.alertsCalendar) }
    val canClose = initial.token.isNotBlank()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (canClose) {
                    FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                }
                Text("Réglages", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                ConnChip(conn, initial.token.isBlank())
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Card("Connexion", Icons.Filled.Link) {
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
                    }
                    Card("Pièce de cette tablette", Icons.Filled.Home) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Room.entries.forEachIndexed { i, r ->
                                SegmentedButton(
                                    selected = room == r,
                                    onClick = { room = r },
                                    shape = SegmentedButtonDefaults.itemShape(i, Room.entries.size),
                                ) { Text(if (r == Room.CHAMBRE) "Chambre" else "Salon") }
                            }
                        }
                        if (room == Room.SALON) {
                            Text(
                                "Annonces vocales : réunions du calendrier Travail 5 min avant, alertes du calendrier Alertes à confirmer, fin des minuteurs de cuisine.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (calendars == null) {
                                Text(
                                    "L'app lit les agendas synchronisés sur la tablette (compte Google ou DAVx5).",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = onAllowCalendars) { Text("Autoriser l'accès aux agendas") }
                            } else {
                                CalendarPicker("Agenda Travail", workCal, calendars) { workCal = it }
                                CalendarPicker("Agenda Alertes", alertsCal, calendars) { alertsCal = it }
                                if (calendars.isEmpty()) {
                                    Text(
                                        "Aucun agenda sur la tablette : ajouter un compte Google ou DAVx5 dans les paramètres Android.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (calendarError != null) {
                                Text(calendarError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            FilledTonalButton(onClick = onTestVoice) { Text("Tester la voix") }
                        }
                    }
                    Card("Démarrage", Icons.Filled.PowerSettingsNew) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Démarrage automatique", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Ouvre le dashboard quand la tablette démarre ou après une mise à jour, sans être le lanceur.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                        }
                        if (bootStart(autoStart, Build.VERSION.SDK_INT, canOverlay) == BootStart.NEEDS_OVERLAY) {
                            Text(
                                "Android demande l'autorisation « Afficher par-dessus d'autres applications » pour ouvrir l'app au démarrage.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                            FilledTonalButton(onClick = {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)),
                                )
                            }) { Text("Autoriser") }
                        }
                    }
                    Card("Batterie → Home Assistant", Icons.Filled.BatteryChargingFull) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Tile("Niveau", battery?.let { "${it.levelPct} %" } ?: "–", Modifier.weight(1f))
                            Tile("État", battery?.let { if (it.charging) "En charge" else "Sur batterie" } ?: "–", Modifier.weight(1f))
                            Tile("Température", battery?.tempC?.let { "%.1f °C".format(it).replace('.', ',') } ?: "–", Modifier.weight(1f))
                        }
                        Text(
                            "sensor.tablette_$deviceSlug" + "_batterie\nbinary_sensor.tablette_$deviceSlug" + "_en_charge\nsensor.tablette_$deviceSlug" + "_temperature_batterie",
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReportLine(report)
                    }
                }
                Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card("Veille et détection de mouvement", Icons.Filled.Sensors, Modifier.weight(1f), fillHeight = true) {
                        CameraPreview(preview, Modifier.weight(1f).fillMaxWidth())
                        LabeledValue("Mise en veille après", formatDelay(SLEEP_STEPS[delayIdx]))
                        Slider(
                            value = delayIdx.toFloat(),
                            onValueChange = { delayIdx = it.roundToInt() },
                            valueRange = 0f..(SLEEP_STEPS.size - 1).toFloat(),
                            steps = SLEEP_STEPS.size - 2,
                        )
                        LabeledValue("Taille minimale d'un mouvement", "${"%.1f".format(minArea * 100).replace('.', ',')} % de l'image")
                        Slider(value = minArea, onValueChange = { minArea = it }, valueRange = 0.005f..0.1f)
                        Text(
                            "Plus petit = réveil plus facile. Les changements de lumière (plus de 60 % de l'image d'un coup) sont ignorés.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("API caméra", style = MaterialTheme.typography.titleMedium)
                            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                                CameraApi.entries.forEachIndexed { i, a ->
                                    SegmentedButton(
                                        selected = cameraApi == a,
                                        onClick = { cameraApi = a },
                                        shape = SegmentedButtonDefaults.itemShape(i, CameraApi.entries.size),
                                    ) { Text(if (a == CameraApi.CAMERAX) "CameraX" else "Camera1") }
                                }
                            }
                        }
                        Text(
                            "Camera1 : pour les tablettes dont l'aperçu reste indisponible avec CameraX.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (canClose) TextButton(onClick = onClose) { Text("Annuler") }
                        Button(onClick = {
                            onSave(
                                Settings(
                                    url = url, token = token, sleepDelaySec = SLEEP_STEPS[delayIdx], sensitivity = minArea, cameraApi = cameraApi, autoStart = autoStart,
                                    room = room, workCalendar = workCal, alertsCalendar = alertsCal,
                                ),
                            )
                        }) { Text("Enregistrer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            (if (fillHeight) Modifier.fillMaxSize() else Modifier).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ConnChip(conn: ConnState, noToken: Boolean) {
    val (text, icon, bg, fg) = when {
        noToken -> Quad("Saisis l'URL et un jeton", Icons.Filled.Warning, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        conn == ConnState.CONNECTED -> Quad("Connecté à Home Assistant", Icons.Filled.CheckCircle, Color(0xFF1F3D23), Color(0xFFB6F2B9))
        conn == ConnState.CONNECTING -> Quad("Connexion…", Icons.Filled.Sync, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        conn == ConnState.AUTH_FAILED -> Quad("Jeton refusé", Icons.Filled.Warning, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Quad("Home Assistant injoignable", Icons.Filled.CloudOff, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

private data class Quad(val text: String, val icon: ImageVector, val bg: Color, val fg: Color)

@Composable
private fun ReportLine(report: ReportStatus) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val text = when (report) {
        ReportStatus.Idle -> "Pas encore envoyé à Home Assistant"
        is ReportStatus.Sent -> {
            val min = ((now - report.atMs) / 60_000).coerceAtLeast(0)
            "Appareil « Tablette chambre » dans HA · dernier envoi " + if (min == 0L) "à l'instant" else "il y a $min min"
        }
        is ReportStatus.Error -> report.message
    }
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = if (report is ReportStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CameraPreview(preview: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
    Box(
        Modifier.aspectRatio(4f / 3f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(
                preview.asImageBitmap(), "Aperçu caméra",
                // Mirrored like a selfie camera so moving left shows on the left.
                Modifier.fillMaxSize().graphicsLayer(scaleX = -1f),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
            )
        } else {
            Text("Aperçu caméra indisponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5449)))
            Text("Aperçu · vert = mouvement", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
    }
}

/** Drop-down of the tablet's calendars; [selected] is a calendar id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPicker(label: String, selected: String, calendars: List<DeviceCalendar>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = calendars.firstOrNull { it.id == selected }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current?.let { "${it.name} · ${it.account}" } ?: if (selected.isBlank()) "À choisir" else "Agenda introuvable",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            calendars.forEach { c ->
                DropdownMenuItem(
                    text = { Column { Text(c.name); Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    onClick = { onSelect(c.id); open = false },
                )
            }
        }
    }
}
