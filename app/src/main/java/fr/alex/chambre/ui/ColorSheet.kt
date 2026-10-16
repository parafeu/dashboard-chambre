package fr.alex.chambre.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Hue in degrees (0..360) of a point relative to the wheel's [center], 0° = right, clockwise. */
fun hueAt(point: Offset, center: Offset): Float {
    val deg = Math.toDegrees(atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())).toFloat()
    return (deg + 360f) % 360f
}

private val HUES = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSheet(
    initial: Pair<Float, Float>,
    onColor: (hue: Float, saturation: Float, final: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var hue by remember { mutableFloatStateOf(initial.first) }
    var sat by remember { mutableFloatStateOf(initial.second) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, sheetMaxWidth = 640.dp) {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Couleur", style = MaterialTheme.typography.titleLarge)
            val current = Color.hsv(hue, sat / 100f, 1f)
            Canvas(
                Modifier.size(320.dp).pointerInput(Unit) {
                    awaitEachGesture {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val down = awaitFirstDown()
                        hue = hueAt(down.position, c)
                        onColor(hue, sat, false)
                        drag(down.id) { change ->
                            change.consume()
                            hue = hueAt(change.position, c)
                            onColor(hue, sat, false)
                        }
                        onColor(hue, sat, true)
                    }
                },
            ) {
                val ring = 44.dp.toPx()
                val radius = size.minDimension / 2f - ring / 2f
                drawCircle(Brush.sweepGradient(HUES, center), radius, center, style = Stroke(ring))
                drawCircle(current, radius - ring / 2f - 28.dp.toPx(), center)
                val a = Math.toRadians(hue.toDouble())
                val knob = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
                drawCircle(Color.hsv(hue, 1f, 1f), ring / 2f - 4.dp.toPx(), knob)
                drawCircle(Color.White, ring / 2f - 4.dp.toPx(), knob, style = Stroke(4.dp.toPx()))
            }
            Column(Modifier.widthIn(max = 480.dp)) {
                Text("Saturation ${sat.roundToInt()} %", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = sat,
                    onValueChange = { sat = it; onColor(hue, sat, false) },
                    onValueChangeFinished = { onColor(hue, sat, true) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = current, activeTrackColor = current),
                )
            }
        }
    }
}
