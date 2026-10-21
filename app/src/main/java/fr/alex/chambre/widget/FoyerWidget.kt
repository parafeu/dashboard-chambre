package fr.alex.chambre.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.alex.chambre.MainActivity
import fr.alex.chambre.R
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ui.phone.EXTRA_TAB

// Ambre palette (ui/theme/Theme.kt), as in the validated widget mockup.
private val BG = Color(0xFF19120C)
private val TILE = Color(0xFF2F2620)
private val TILE_ON = Color(0xFFFFB86B)
private val ON_TILE = Color(0xFFF0DFD3)
private val ON_TILE_ON = Color(0xFF3F2200)
private val SUB = Color(0xFFCBB8A9)
private val SUB_ON = Color(0xFF6A3B00)
private val ICON_BG = Color(0xFF43372E)
private val ICON_BG_ON = Color(0x59FFFFFF)
private val CHIP_ON = Color(0x2E000000)

/** One room's widget (H1 / SA / KA): title row with the open-app button, then a tile per device. */
abstract class FoyerWidget(private val room: WidgetRoom) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = widgetRepository(context).refresh(room).also { WidgetSnapshots.publish(room, it) }
        provideContent {
            val live by WidgetSnapshots.flow(room).collectAsState()
            WidgetContent(context, room, live ?: initial)
        }
    }
}

class ChambreWidget : FoyerWidget(WidgetRoom.CHAMBRE)
class SalonWidget : FoyerWidget(WidgetRoom.SALON)
class CuisineWidget : FoyerWidget(WidgetRoom.CUISINE)

class ChambreWidgetReceiver : FoyerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ChambreWidget() }
class SalonWidgetReceiver : FoyerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SalonWidget() }
class CuisineWidgetReceiver : FoyerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CuisineWidget() }

/** Keeps the periodic refresh running while at least one widget is placed. */
abstract class FoyerWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancelIfNoWidget(context)
    }
}

fun widgetFor(room: WidgetRoom): FoyerWidget = when (room) {
    WidgetRoom.CHAMBRE -> ChambreWidget()
    WidgetRoom.SALON -> SalonWidget()
    WidgetRoom.CUISINE -> CuisineWidget()
}

private fun openApp(context: Context, room: WidgetRoom): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_TAB, room.tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

private fun run(room: WidgetRoom, action: String): Action =
    actionRunCallback<WidgetActionCallback>(actionParametersOf(WidgetActionCallback.ROOM to room.name, WidgetActionCallback.ACTION to action))

@Composable
private fun WidgetContent(context: Context, room: WidgetRoom, snap: WidgetSnapshot) {
    // HA out of reach: the last known states, dimmed.
    fun c(color: Color) = ColorProvider(if (snap.stale) color.copy(alpha = color.alpha * 0.5f) else color)
    Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(28.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                room.title,
                GlanceModifier.defaultWeight().clickable(openApp(context, room)),
                style = TextStyle(color = c(ON_TILE), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            if (room == WidgetRoom.SALON) {
                val climate = climateText(snap.states[Entities.SALON_TEMPERATURE], snap.states[Entities.SALON_HUMIDITY])
                if (climate.isNotEmpty()) {
                    Text(climate, style = TextStyle(color = c(SUB), fontSize = 13.sp))
                    Spacer(GlanceModifier.width(8.dp))
                }
            }
            Box(
                GlanceModifier.size(28.dp).background(TILE).cornerRadius(14.dp).clickable(openApp(context, room)),
                contentAlignment = Alignment.Center,
            ) { Image(ImageProvider(R.drawable.ic_w_open), "Ouvrir Foyer", GlanceModifier.size(15.dp), colorFilter = ColorFilter.tint(c(ON_TILE))) }
        }
        if (!snap.configured) {
            Box(GlanceModifier.fillMaxSize().clickable(openApp(context, room)), contentAlignment = Alignment.Center) {
                Text("Ouvrir Foyer pour configurer", style = TextStyle(color = c(SUB), fontSize = 13.sp))
            }
            return@Column
        }
        room.light?.let { id ->
            val light = snap.states[id]
            Tile(
                on = lightIsOn(light), icon = R.drawable.ic_w_light, name = "Lumière", status = lightText(light), color = ::c,
                onTap = run(room, WidgetActionCallback.TOGGLE),
                buttons = listOf(R.drawable.ic_w_minus to run(room, WidgetActionCallback.MINUS), R.drawable.ic_w_plus to run(room, WidgetActionCallback.PLUS)),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.height(8.dp))
        }
        val cover = snap.states[room.cover]
        val coverButtons = buildList {
            add(R.drawable.ic_w_up to run(room, WidgetActionCallback.OPEN))
            if (room == WidgetRoom.CUISINE) add(R.drawable.ic_w_stop to run(room, WidgetActionCallback.STOP))
            add(R.drawable.ic_w_down to run(room, WidgetActionCallback.CLOSE))
        }
        Tile(
            on = coverIsOpen(cover), icon = R.drawable.ic_w_blinds, name = "Volet", status = coverText(cover), color = ::c,
            onTap = run(room, WidgetActionCallback.COVER_TAP), buttons = coverButtons,
            modifier = GlanceModifier.defaultWeight(),
            compact = room == WidgetRoom.CUISINE,
        )
    }
}

@Composable
private fun Tile(
    on: Boolean,
    icon: Int,
    name: String,
    status: String,
    color: (Color) -> ColorProvider,
    onTap: Action,
    buttons: List<Pair<Int, Action>>,
    modifier: GlanceModifier,
    compact: Boolean = false,
) {
    val fg = if (on) ON_TILE_ON else ON_TILE
    Row(
        modifier.fillMaxWidth().background(color(if (on) TILE_ON else TILE)).cornerRadius(22.dp)
            .padding(horizontal = if (compact) 12.dp else 14.dp).clickable(onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = if (compact) 34.dp else 40.dp
        Box(GlanceModifier.size(iconSize).background(if (on) ICON_BG_ON else ICON_BG).cornerRadius(iconSize / 2), contentAlignment = Alignment.Center) {
            Image(ImageProvider(icon), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(color(if (on) ON_TILE_ON else Color(0xFFFFDCBE))))
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(name, style = TextStyle(color = color(fg), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(status, style = TextStyle(color = color(if (on) SUB_ON else SUB), fontSize = 12.sp), maxLines = 1)
        }
        val size = if (compact) 32.dp else 36.dp
        buttons.forEachIndexed { i, (res, action) ->
            if (i > 0) Spacer(GlanceModifier.width(if (compact) 4.dp else 6.dp))
            Box(GlanceModifier.size(size).background(if (on) CHIP_ON else ICON_BG).cornerRadius(size / 2).clickable(action), contentAlignment = Alignment.Center) {
                Image(ImageProvider(res), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(color(fg)))
            }
        }
    }
}
