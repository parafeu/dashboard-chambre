package fr.alex.chambre.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Amber {
    val Primary = Color(0xFFFFB86B)
    val OnPrimary = Color(0xFF4A2800)
    val PrimaryContainer = Color(0xFF6A3B00)
    val OnPrimaryContainer = Color(0xFFFFDCBE)
    val Background = Color(0xFF19120C)
    val SurfaceContainer = Color(0xFF261E17)
    val SurfaceContainerHigh = Color(0xFF3A3028)
    val OnSurface = Color(0xFFF0DFD3)
    val OnSurfaceVariant = Color(0xFFD6C3B5)
    val Outline = Color(0xFF52443A)
    val Tertiary = Color(0xFFC5CA8F)
    val OnTertiary = Color(0xFF2E3305)
}

private val AmberDark = darkColorScheme(
    primary = Amber.Primary,
    onPrimary = Amber.OnPrimary,
    primaryContainer = Amber.PrimaryContainer,
    onPrimaryContainer = Amber.OnPrimaryContainer,
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFEDCBE),
    tertiary = Amber.Tertiary,
    onTertiary = Amber.OnTertiary,
    tertiaryContainer = Color(0xFF454A1A),
    onTertiaryContainer = Color(0xFFE1E6A8),
    background = Amber.Background,
    onBackground = Amber.OnSurface,
    surface = Amber.Background,
    onSurface = Amber.OnSurface,
    surfaceVariant = Amber.Outline,
    onSurfaceVariant = Amber.OnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF130D07),
    surfaceContainerLow = Color(0xFF211A13),
    surfaceContainer = Amber.SurfaceContainer,
    surfaceContainerHigh = Amber.SurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF453A31),
    outline = Color(0xFF9F8D80),
    outlineVariant = Amber.Outline,
    inverseSurface = Amber.OnSurface,
    inverseOnSurface = Color(0xFF382F27),
    inversePrimary = Color(0xFF8A5100),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun ChambreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmberDark, content = content)
}
