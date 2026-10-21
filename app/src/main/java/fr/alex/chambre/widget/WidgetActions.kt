package fr.alex.chambre.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import fr.alex.chambre.ha.HaRestClient
import fr.alex.chambre.settings.SettingsStore
import fr.alex.chambre.settings.dataStore
import fr.alex.chambre.settings.normalizeUrl
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val widgetClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}

/** The widgets' Home Assistant access: the app's saved URL + token, over REST. */
fun widgetRepository(context: Context): WidgetRepository {
    val app = context.applicationContext
    return WidgetRepository(
        DataStoreWidgetStore(app.widgetDataStore),
        settings = { SettingsStore(app.dataStore).settings.first() },
        api = { s -> HaRestClient(normalizeUrl(s.url), s.token, widgetClient) },
    )
}

/** A tap on a widget button: call Home Assistant, then redraw (again a few seconds later for a moving shutter). */
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val room = parameters[ROOM]?.let { r -> WidgetRoom.entries.firstOrNull { it.name == r } } ?: return
        val action = when (parameters[ACTION]) {
            TOGGLE -> WidgetAction.ToggleLight
            MINUS -> WidgetAction.StepLight(-10)
            PLUS -> WidgetAction.StepLight(+10)
            COVER_TAP -> WidgetAction.CoverTapAction
            OPEN -> WidgetAction.CoverOpen
            STOP -> WidgetAction.CoverStop
            CLOSE -> WidgetAction.CoverClose
            else -> return
        }
        val repo = widgetRepository(context)
        WidgetSnapshots.publish(room, repo.act(room, action))
        val widget = widgetFor(room)
        widget.update(context, glanceId)
        // A shutter takes a while: show it moving, then where it stopped. Scheduled, never awaited here: the
        // callback runs in a broadcast and holds every later widget tap until it returns.
        if (parameters[ACTION] in COVER_ACTIONS) WidgetRefreshWorker.followUp(context, room)
    }

    companion object {
        val ROOM = ActionParameters.Key<String>("room")
        val ACTION = ActionParameters.Key<String>("action")
        const val TOGGLE = "toggle"
        const val MINUS = "minus"
        const val PLUS = "plus"
        const val COVER_TAP = "cover_tap"
        const val OPEN = "open"
        const val STOP = "stop"
        const val CLOSE = "close"
        private val COVER_ACTIONS = setOf(COVER_TAP, OPEN, STOP, CLOSE)
    }
}
