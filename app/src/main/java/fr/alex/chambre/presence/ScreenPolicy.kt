package fr.alex.chambre.presence

/** What the window asks of the screen: stay on, and be as dark as possible. */
data class ScreenFlags(val keepOn: Boolean, val dark: Boolean)

/**
 * Asleep, the window goes black at the lowest backlight. With [screenOff] (LCD tablets, where the lowest backlight
 * still glows) it also stops keeping the screen on, so Android turns it off after its own screen timeout; the camera
 * then runs in [CameraService] and a motion turns the screen back on ([Waker]).
 */
fun screenFlags(presence: Presence, screenOff: Boolean): ScreenFlags = when (presence) {
    Presence.AWAKE -> ScreenFlags(keepOn = true, dark = false)
    Presence.ASLEEP -> ScreenFlags(keepOn = !screenOff, dark = true)
}
