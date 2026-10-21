package fr.alex.chambre.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/** The widget colours: a tile per device, amber-filled (or the wallpaper's accent) when on / open. */
data class WidgetPalette(
    val background: Color,
    val tile: Color,
    val tileOn: Color,
    val onTile: Color,
    val onTileOn: Color,
    val sub: Color,
    val subOn: Color,
    val iconBg: Color,
    val iconBgOn: Color,
    val icon: Color,
    val chipOn: Color,
)

/** The validated Ambre mockup; used where Android has no wallpaper colours (before Android 12: the tablets). */
val AMBRE = WidgetPalette(
    background = Color(0xFF19120C),
    tile = Color(0xFF2F2620),
    tileOn = Color(0xFFFFB86B),
    onTile = Color(0xFFF0DFD3),
    onTileOn = Color(0xFF3F2200),
    sub = Color(0xFFCBB8A9),
    subOn = Color(0xFF6A3B00),
    iconBg = Color(0xFF43372E),
    iconBgOn = Color(0x59FFFFFF),
    icon = Color(0xFFFFDCBE),
    chipOn = Color(0x2E000000),
)

/** Material You: the same roles taken from the wallpaper's scheme (as Google Home's widgets do). */
fun paletteFrom(s: ColorScheme) = WidgetPalette(
    background = s.surfaceContainerLow,
    tile = s.surfaceContainerHigh,
    tileOn = s.primaryContainer,
    onTile = s.onSurface,
    onTileOn = s.onPrimaryContainer,
    sub = s.onSurfaceVariant,
    subOn = s.onPrimaryContainer.copy(alpha = 0.8f),
    iconBg = s.surfaceContainerHighest,
    iconBgOn = s.onPrimaryContainer.copy(alpha = 0.14f),
    icon = s.primary,
    chipOn = s.onPrimaryContainer.copy(alpha = 0.12f),
)

/** Day and night palettes for this device: wallpaper colours on Android 12+, Ambre otherwise. */
fun widgetPalettes(context: Context): Pair<WidgetPalette, WidgetPalette> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        paletteFrom(dynamicLightColorScheme(context)) to paletteFrom(dynamicDarkColorScheme(context))
    } else {
        AMBRE to AMBRE
    }
