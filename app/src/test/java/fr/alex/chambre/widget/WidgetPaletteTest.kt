package fr.alex.chambre.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPaletteTest {
    @Test
    fun `Material You tiles follow the wallpaper scheme`() {
        val scheme = darkColorScheme(primaryContainer = Color(0xFF123456), onPrimaryContainer = Color(0xFFABCDEF), surfaceContainerHigh = Color(0xFF222222))
        val p = paletteFrom(scheme)
        assertEquals(Color(0xFF123456), p.tileOn)
        assertEquals(Color(0xFFABCDEF), p.onTileOn)
        assertEquals(Color(0xFF222222), p.tile)
    }

    @Test
    fun `without Material You the widgets keep the Ambre look`() {
        assertEquals(Color(0xFFFFB86B), AMBRE.tileOn)
        assertEquals(Color(0xFF19120C), AMBRE.background)
    }
}
