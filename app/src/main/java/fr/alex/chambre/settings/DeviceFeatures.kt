package fr.alex.chambre.settings

import fr.alex.chambre.ha.entitiesFor

/** A wall tablet (one room, kiosk) or a phone (the global dashboard, a normal app). */
enum class FormFactor { TABLET, PHONE }

/** Android's usual phone / tablet threshold, on the device's smallest width (not the window's). */
fun formFactorFor(smallestWidthDp: Int): FormFactor = if (smallestWidthDp < 600) FormFactor.PHONE else FormFactor.TABLET

/** What runs on this device. */
data class DeviceFeatures(
    /** Home Assistant entities to subscribe to. */
    val entities: List<String>,
    /** HA timer.finished events (living-room announcements). */
    val timerEvents: Boolean,
    val announcements: Boolean,
    val batteryReport: Boolean,
    val camera: Boolean,
    /** Screen sleeps after the delay without motion or touch. */
    val presence: Boolean,
    val mealie: Boolean,
    val bootStart: Boolean,
    /** Immersive mode, screen kept on, back button blocked. */
    val kiosk: Boolean,
)

/** The phone shows the living room's global dashboard and none of the wall-tablet features, whatever room is stored. */
fun deviceFeatures(settings: Settings, formFactor: FormFactor): DeviceFeatures = when (formFactor) {
    FormFactor.PHONE -> DeviceFeatures(
        entities = entitiesFor(Room.SALON),
        timerEvents = false,
        announcements = false,
        batteryReport = false,
        camera = false,
        presence = false,
        mealie = true,
        bootStart = false,
        kiosk = false,
    )
    FormFactor.TABLET -> DeviceFeatures(
        entities = entitiesFor(settings.room),
        timerEvents = settings.room == Room.SALON,
        announcements = settings.room == Room.SALON,
        batteryReport = true,
        camera = true,
        presence = true,
        mealie = settings.room == Room.CUISINE || settings.room == Room.SALON,
        bootStart = settings.autoStart,
        kiosk = true,
    )
}
