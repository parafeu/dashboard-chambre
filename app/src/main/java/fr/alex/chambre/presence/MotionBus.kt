package fr.alex.chambre.presence

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Links [CameraService]'s camera to the screen (presence, settings preview) within the app's process. */
object MotionBus {
    /** One event per motion the service's camera sees. */
    val motion = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Screen woke up or went to sleep: the camera ignores the light change for a moment. */
    val screenTransitions = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Set while the settings screen is open: the service then publishes annotated frames on [preview]. */
    val previewWanted = MutableStateFlow(false)
    val preview = MutableStateFlow<Bitmap?>(null)
}
