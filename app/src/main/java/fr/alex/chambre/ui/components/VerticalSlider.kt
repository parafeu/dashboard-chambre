package fr.alex.chambre.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Maps a touch at [y] on a track of [height] to 0..1 (bottom-filled: top = 1). */
fun sliderFraction(y: Float, height: Float, fillFromTop: Boolean): Float {
    val f = (y / height).coerceIn(0f, 1f)
    return if (fillFromTop) f else 1f - f
}

/**
 * Thick vertical slider. While dragging it shows the finger's value; after release it keeps that
 * value for 1 s before following [value] again, so Home Assistant's echo doesn't make it jump.
 *
 * @param value 0..1, or null when the entity reports no value (no fill, no thumb).
 * @param track background brush; when set, no fill is drawn and only the thumb shows the value.
 */
@Composable
fun VerticalSlider(
    value: Float?,
    onChange: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fillFromTop: Boolean = false,
    track: Brush? = null,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
    topLabel: String? = null,
    topLabelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    var local by remember { mutableStateOf<Float?>(null) }
    var releases by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(releases) {
        if (releases > 0) {
            delay(1_000)
            if (!dragging) local = null
        }
    }
    val onChangeState by rememberUpdatedState(onChange)
    val onReleaseState by rememberUpdatedState(onRelease)

    val shown = local ?: value
    val animated by animateFloatAsState(shown ?: 0f, spring(stiffness = 2_000f), label = "slider")
    val display = if (dragging) shown ?: 0f else animated
    val shape = RoundedCornerShape(36.dp)
    val trackBg = MaterialTheme.colorScheme.surfaceContainerHighest

    BoxWithConstraints(
        modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .then(if (track != null) Modifier.background(track) else Modifier.background(trackBg))
            .pointerInput(enabled, fillFromTop) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    var v = sliderFraction(down.position.y, size.height.toFloat(), fillFromTop)
                    local = v
                    onChangeState(v)
                    drag(down.id) { change ->
                        change.consume()
                        v = sliderFraction(change.position.y, size.height.toFloat(), fillFromTop)
                        local = v
                        onChangeState(v)
                    }
                    dragging = false
                    onReleaseState(v)
                    releases++
                }
            },
    ) {
        val h = maxHeight
        if (shown != null && track == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(display.coerceIn(0f, 1f))
                    .align(if (fillFromTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .background(fillColor),
            )
        }
        if (shown != null) {
            // Thumb: a short pill across the edge of the fill.
            val pos = if (fillFromTop) display else 1f - display
            val y = (h * pos - 4.dp).coerceIn(10.dp, h - 18.dp)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y)
                    .width(56.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (track != null) Color.White else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)),
            )
        }
        if (topLabel != null) {
            Text(
                topLabel,
                Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                style = MaterialTheme.typography.titleLarge,
                color = topLabelColor,
            )
        }
        if (icon != null) {
            Icon(
                icon, null,
                Modifier.align(if (fillFromTop) Alignment.TopCenter else Alignment.BottomCenter).padding(24.dp).size(36.dp),
                tint = iconTint,
            )
        }
        Box(Modifier.fillMaxSize())
    }
}
