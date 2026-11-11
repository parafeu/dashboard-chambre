package fr.alex.chambre.settings

import fr.alex.chambre.ha.entitiesFor

/** A wall tablet (one room, kiosk) or a phone (the global dashboard, a normal app). */
enum class FormFactor { TABLET, PHONE }

/** Android's usual phone / tablet threshold, on the device's smallest width (not the window's). */
fun formFactorFor(smallestWidthDp: Int): FormFactor = if (smallestWidthDp < 600) FormFactor.PHONE else FormFactor.TABLET

/**
 * What runs on this device and depends on its settings. Form-factor-only choices (kiosk, camera, sleep overlay,
 * battery reporting: tablets only) are read from [FormFactor] directly.
 */
data class DeviceFeatures(
    /** Home Assistant entities to subscribe to. */
    val entities: List<String>,
    val announcements: Boolean,
    val mealie: Boolean,
    val bootStart: Boolean,
    /** Follows the shared kitchen timers (rings when one ends, if the setting allows). */
    val timers: Boolean,
    /** Shows the « Minuteries » panel (the rooms with a kitchen screen). */
    val timerPanel: Boolean,
)

/** The phone shows the living room's global dashboard and none of the wall-tablet features, whatever room is stored. */
fun deviceFeatures(settings: Settings, formFactor: FormFactor): DeviceFeatures = when (formFactor) {
    FormFactor.PHONE -> DeviceFeatures(
        entities = entitiesFor(Room.SALON),
        announcements = false,
        mealie = true,
        bootStart = false,
        timers = false,
        timerPanel = false,
    )
    FormFactor.TABLET -> DeviceFeatures(
        entities = entitiesFor(settings.room),
        announcements = settings.room == Room.SALON,
        mealie = settings.room == Room.CUISINE || settings.room == Room.SALON,
        bootStart = settings.autoStart,
        timers = true,
        timerPanel = settings.room == Room.CUISINE || settings.room == Room.SALON,
    )
}
