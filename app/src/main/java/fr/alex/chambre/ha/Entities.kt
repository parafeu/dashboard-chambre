package fr.alex.chambre.ha

data class Scene(val entityId: String, val label: String)

sealed interface Swatch {
    /** ARGB color used to draw the swatch. */
    val displayColor: Long

    data class Kelvin(val kelvin: Int, override val displayColor: Long) : Swatch
    data class Rgb(val r: Int, val g: Int, val b: Int) : Swatch {
        override val displayColor: Long get() = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}

/** The bedroom's Home Assistant entities (hard-coded on purpose, see spec §3). */
object Entities {
    const val LIGHT = "light.chambre_lumieres"
    const val COVER = "cover.volet_chambre"
    const val HALF = "script.chambre_volet_mi_hauteur"
    const val WEATHER = "weather.forecast_maison"

    /** Same as the BILRESA remotes' long press (automations « Chambre Porte » / « Chambre Lit Gauche »). */
    const val DEFAULT_BRIGHTNESS_PCT = 70
    const val DEFAULT_KELVIN = 2700

    val SCENES = listOf(
        Scene("script.chambre_scene_coucher", "Coucher"),
        Scene("script.chambre_scene_lecture", "Lecture"),
        Scene("script.chambre_scene_reveil", "Réveil"),
        Scene("script.chambre_scene_nuit", "Nuit"),
    )

    val ALL: List<String> = listOf(LIGHT, COVER, HALF, WEATHER) + SCENES.map { it.entityId }

    val SWATCHES: List<Swatch> = listOf(
        Swatch.Kelvin(2200, 0xFFFFB46B),
        Swatch.Kelvin(2700, 0xFFFFD9A8),
        Swatch.Kelvin(4000, 0xFFFFF3E0),
        Swatch.Kelvin(6500, 0xFFDCEBFF),
        Swatch.Rgb(255, 60, 45),
        Swatch.Rgb(255, 140, 40),
        Swatch.Rgb(70, 220, 120),
        Swatch.Rgb(55, 140, 255),
        Swatch.Rgb(170, 90, 255),
    )
}
