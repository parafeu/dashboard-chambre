package fr.alex.chambre.ui

import androidx.compose.foundation.background
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun LightPanel(
    light: EntityState?,
    online: Boolean,
    controller: DashboardController,
    modifier: Modifier = Modifier,
    asleep: Boolean = false,
    /** Swatches + color wheel (bedroom bulbs); false for white-only lights. */
    colors: Boolean = true,
    /** Narrow column: smaller labels, buttons stacked. */
    compact: Boolean = false,
    title: String = "Lumière",
) {
    val enabled = online && light != null && !light.isUnavailable
    val isOn = light?.state == "on"
    val pct = if (isOn) light?.brightnessPct ?: 0 else 0
    val minK = light?.minK ?: 2000
    val maxK = light?.maxK ?: 6500
    var dragPct by remember { mutableStateOf<Int?>(null) }
    var dragK by remember { mutableStateOf<Int?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    // The sheet lives in its own window above the sleep overlay: close it when the screen sleeps.
    LaunchedEffect(asleep) { if (asleep) sheetOpen = false }

    Section(title, modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
            val shownPct = dragPct ?: pct
            VerticalSlider(
                value = if (light == null) null else pct / 100f,
                onChange = { v -> (v * 100).roundToInt().also { dragPct = it; controller.setBrightness(it, final = false) } },
                onRelease = { v -> dragPct = null; controller.setBrightness((v * 100).roundToInt(), final = true) },
                enabled = enabled,
                icon = Icons.Filled.LightMode,
                topLabel = if (shownPct == 0 && dragPct == null) (if (compact) "Off" else "Éteinte") else "$shownPct %",
                compactLabel = compact,
                topLabelColor = if (shownPct >= 90) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val k = light?.colorTempK
            VerticalSlider(
                value = k?.let { ((it - minK).toFloat() / (maxK - minK)).coerceIn(0f, 1f) },
                onChange = { v -> (minK + v * (maxK - minK)).roundToInt().also { dragK = it; controller.setKelvin(it, final = false) } },
                onRelease = { v -> dragK = null; controller.setKelvin((minK + v * (maxK - minK)).roundToInt(), final = true) },
                track = Brush.verticalGradient(listOf(COLD, NEUTRAL, WARM)),
                enabled = enabled && isOn,
                icon = Icons.Filled.Thermostat,
                iconTint = Color(0xFF4A2800),
                topLabel = (dragK ?: k)?.let { "$it K" } ?: if (compact) "–" else "Couleur",
                compactLabel = compact,
                topLabelColor = Color(0xFF4A2800),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (compact) {
            LightButtonsStacked(isOn = isOn, enabled = enabled, onToggle = controller::toggleLight, onDefault = controller::resetDefault)
        } else {
            LightButtonGroup(isOn = isOn, enabled = enabled, onToggle = controller::toggleLight, onDefault = controller::resetDefault)
        }
        if (colors) Row(
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

    if (colors && sheetOpen) {
        ColorSheet(
            initial = light?.hsColor ?: (30f to 70f),
            onColor = { h, s, final -> controller.setHs(h, s, final) },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** M3 Expressive connected button group: power toggle + « Par défaut » (70 % · 2700 K). */
@Composable
private fun LightButtonGroup(isOn: Boolean, enabled: Boolean, onToggle: () -> Unit, onDefault: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onToggle,
            enabled = enabled,
            shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOn) colors.primary else colors.surfaceContainerHighest,
                contentColor = if (isOn) colors.onPrimary else colors.onSurface,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
        ) {
            Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isOn) "Allumée" else "Éteinte", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        FilledTonalButton(
            onClick = onDefault,
            enabled = enabled,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 32.dp, bottomEnd = 32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
        ) {
            Icon(Icons.Filled.RestartAlt, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Par défaut", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/** Narrow-column version: the same two actions, stacked. */
@Composable
private fun LightButtonsStacked(isOn: Boolean, enabled: Boolean, onToggle: () -> Unit, onDefault: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onToggle,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOn) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (isOn) colors.onPrimary else colors.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(if (isOn) "Allumées" else "Éteintes", style = MaterialTheme.typography.titleMedium, maxLines = 1) }
    FilledTonalButton(onClick = onDefault, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Par défaut", style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}
