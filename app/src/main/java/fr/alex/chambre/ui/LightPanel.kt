package fr.alex.chambre.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState
import fr.alex.chambre.ha.brightnessPct
import fr.alex.chambre.ha.colorTempK
import fr.alex.chambre.ha.hsColor
import fr.alex.chambre.ha.isUnavailable
import fr.alex.chambre.ha.maxK
import fr.alex.chambre.ha.minK
import fr.alex.chambre.ui.components.VerticalSlider
import kotlin.math.roundToInt

private val WARM = Color(0xFFFFB46B)
private val NEUTRAL = Color(0xFFFFF3E0)
private val COLD = Color(0xFFDCEBFF)
private val RAINBOW = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
)

@Composable
fun LightPanel(light: EntityState?, online: Boolean, controller: DashboardController, modifier: Modifier = Modifier) {
    val enabled = online && light != null && !light.isUnavailable
    val isOn = light?.state == "on"
    val pct = if (isOn) light?.brightnessPct ?: 0 else 0
    val minK = light?.minK ?: 2000
    val maxK = light?.maxK ?: 6500
    var dragPct by remember { mutableStateOf<Int?>(null) }
    var dragK by remember { mutableStateOf<Int?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    Section("Lumière", modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VerticalSlider(
                    value = if (light == null) null else pct / 100f,
                    onChange = { v -> (v * 100).roundToInt().also { dragPct = it; controller.setBrightness(it, final = false) } },
                    onRelease = { v -> dragPct = null; controller.setBrightness((v * 100).roundToInt(), final = true) },
                    enabled = enabled,
                    icon = Icons.Filled.LightMode,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = controller::toggleLight,
                        enabled = enabled,
                        colors = if (isOn) IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) else IconButtonDefaults.filledTonalIconButtonColors(),
                    ) { Icon(Icons.Filled.PowerSettingsNew, "Allumer / éteindre") }
                    Text(
                        (dragPct ?: pct).let { if (it == 0 && dragPct == null) "Éteinte" else "$it %" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val k = light?.colorTempK
                VerticalSlider(
                    value = k?.let { ((it - minK).toFloat() / (maxK - minK)).coerceIn(0f, 1f) },
                    onChange = { v -> (minK + v * (maxK - minK)).roundToInt().also { dragK = it; controller.setKelvin(it, final = false) } },
                    onRelease = { v -> dragK = null; controller.setKelvin((minK + v * (maxK - minK)).roundToInt(), final = true) },
                    track = Brush.verticalGradient(listOf(COLD, NEUTRAL, WARM)),
                    enabled = enabled && isOn,
                    icon = Icons.Filled.Thermostat,
                    iconTint = Color(0xFF4A2800),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    Text((dragK ?: k)?.let { "$it K" } ?: "Couleur", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Entities.SWATCHES.forEach { sw ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Color(sw.displayColor))
                        .clickable(enabled = enabled) { controller.applySwatch(sw) },
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(RAINBOW)
                    .border(3.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    .clickable(enabled = enabled) { sheetOpen = true },
            )
        }
    }

    if (sheetOpen) {
        ColorSheet(
            initial = light?.hsColor ?: (30f to 70f),
            onColor = { h, s, final -> controller.setHs(h, s, final) },
            onDismiss = { sheetOpen = false },
        )
    }
}
