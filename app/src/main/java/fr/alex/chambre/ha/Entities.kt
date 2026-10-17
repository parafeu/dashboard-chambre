package fr.alex.chambre.ha

import fr.alex.chambre.settings.Room

data class Scene(val entityId: String, val label: String)

/** The light and shutter a panel drives; [halfScript] null = half-height via set_cover_position 50. */
data class RoomEntities(
    val light: String,
    val cover: String,
    val halfScript: String?,
    val colors: Boolean,
    val defaultPct: Int = 70,
    val defaultKelvin: Int = 2700,
)

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

    val CHAMBRE_ROOM = RoomEntities(LIGHT, COVER, HALF, colors = true)
    val SALON_ROOM = RoomEntities("light.lumieres_salon", "cover.volet_salon", null, colors = false)
    /** The kitchen has no light on its panel; only the shutter part is used. */
    val KITCHEN_ROOM = RoomEntities("", "cover.volet_cuisine", null, colors = false)

    const val SALON_TEMPERATURE = "sensor.salon_temperature"
    const val SALON_HUMIDITY = "sensor.salon_humidity"

    const val KITCHEN_COVER = "cover.volet_cuisine"
    val KITCHEN_TIMERS = listOf("timer.minuteur_1", "timer.minuteur_2", "timer.minuteur_3")
    /** script.demarrer_minuteur{n}_{5min|10min} start timer n for that duration. */
    fun timerStartScript(timer: String, minutes: Int): String =
        "script.demarrer_" + timer.removePrefix("timer.").replace("_", "") + "_" + minutes + "min"
    const val SHOPPING_LIST = "todo.liste_dachats"
    const val KITCHEN_TABLET_PLUG = "switch.grillplats_plug"

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

/**
 * Entities the tablet subscribes to, depending on its room. Calendars are not HA entities here (the tablet reads its own),
 * and one malformed id in subscribe_entities would make HA reject the whole subscription.
 */
fun entitiesFor(room: Room): List<String> = when (room) {
    Room.CHAMBRE -> Entities.ALL
    Room.SALON -> (
        Entities.ALL +
            listOf(Entities.SALON_ROOM.light, Entities.SALON_ROOM.cover, Entities.SALON_TEMPERATURE, Entities.SALON_HUMIDITY) +
            listOf(Entities.KITCHEN_COVER, Entities.SHOPPING_LIST, Entities.KITCHEN_TABLET_PLUG) + Entities.KITCHEN_TIMERS +
            Entities.KITCHEN_TIMERS.flatMap { t -> listOf(5, 10).map { Entities.timerStartScript(t, it) } }
        ).distinct()
}
