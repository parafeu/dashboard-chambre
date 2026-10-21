package fr.alex.chambre.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.temperature
import fr.alex.chambre.ui.CoverPanel
import fr.alex.chambre.ui.DashboardViewModel
import fr.alex.chambre.ui.LightPanel
import fr.alex.chambre.ui.ScenesPanel
import fr.alex.chambre.ui.WeatherBlock
import fr.alex.chambre.ui.cuisine.CuisineRecipeOverlay
import fr.alex.chambre.ui.cuisine.RecipesPanel
import fr.alex.chambre.ui.formatDate
import fr.alex.chambre.ui.rememberNow
import fr.alex.chambre.ui.salon.SalonTab
import fr.alex.chambre.mealie.todaysBanner

/** Intent extra a widget sets to open the app on its room: "salon", "cuisine" or "chambre". */
const val EXTRA_TAB = "fr.alex.chambre.TAB"

fun tabFromExtra(value: String?): SalonTab = when (value) {
    "cuisine" -> SalonTab.CUISINE
    "chambre" -> SalonTab.CHAMBRE
    else -> SalonTab.SALON
}

/** A room asked for by a widget; [seq] makes a second request for the same room a new one. */
data class TabRequest(val tab: SalonTab, val seq: Int) {
    fun next(extra: String?) = TabRequest(tabFromExtra(extra), seq + 1)

    companion object {
        fun from(extra: String?) = TabRequest(tabFromExtra(extra), 0)
    }
}

// Panel heights: the tablet panels fill their column; stacked on a phone they get a fixed height.
private val LIGHT_COLORS = 480.dp
private val LIGHT_PLAIN = 440.dp
private val COVER = 450.dp
private val SCENES = 400.dp

/** The phone: the living-room tablet's rooms, their tablet panels stacked, and a floating room bar. */
@Composable
fun PhoneApp(vm: DashboardViewModel, request: TabRequest, onOpenSettings: () -> Unit) {
    val entities by vm.ha.entities.collectAsStateWithLifecycle()
    val conn by vm.ha.conn.collectAsStateWithLifecycle()
    val forecast by vm.ha.forecast.collectAsStateWithLifecycle()
    val forecastDaily by vm.ha.forecastDaily.collectAsStateWithLifecycle()
    val lastScene by vm.controller.lastScene.collectAsStateWithLifecycle()
    val recipes by vm.cuisine.recipes.collectAsStateWithLifecycle()
    val today by vm.cuisine.today.collectAsStateWithLifecycle()
    val recipeError by vm.cuisine.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.ha.errors.collect { snackbar.showSnackbar(it) } }

    var tab by rememberSaveable(request) { mutableStateOf(request.tab) }
    val online = conn == ConnState.CONNECTED
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (!CuisineRecipeOverlay(vm, snackbar, stacked = true)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PhoneHeader(vm, online, onOpenSettings, forecast, forecastDaily)
                    RoomHeading(tab, entities[Entities.SALON_TEMPERATURE]?.state, entities[Entities.SALON_HUMIDITY]?.state)
                    when (tab) {
                        SalonTab.SALON -> {
                            LightPanel(
                                entities[Entities.SALON_ROOM.light], online, vm.salonController,
                                Modifier.fillMaxWidth().height(LIGHT_PLAIN), colors = false, title = "Lumières",
                            )
                            CoverPanel(entities[Entities.SALON_ROOM.cover], online, vm.salonController, Modifier.fillMaxWidth().height(COVER))
                        }
                        SalonTab.CUISINE -> {
                            CoverPanel(entities[Entities.KITCHEN_COVER], online, vm.kitchenController, Modifier.fillMaxWidth().height(COVER))
                            RecipesPanel(
                                recipes = recipes,
                                banner = todaysBanner(today, recipes),
                                error = recipeError,
                                onOpen = vm.cuisine::open,
                                onRetry = vm.cuisine::refresh,
                                modifier = Modifier.fillMaxWidth(),
                                columns = 2,
                                lazy = false,
                            )
                        }
                        SalonTab.CHAMBRE -> {
                            LightPanel(entities[Entities.LIGHT], online, vm.controller, Modifier.fillMaxWidth().height(LIGHT_COLORS))
                            CoverPanel(entities[Entities.COVER], online, vm.controller, Modifier.fillMaxWidth().height(COVER))
                            ScenesPanel(entities, lastScene, online, vm.controller::runScene, Modifier.fillMaxWidth().height(SCENES))
                        }
                    }
                }
                FloatingRoomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }
}

@Composable
private fun PhoneHeader(
    vm: DashboardViewModel,
    online: Boolean,
    onOpenSettings: () -> Unit,
    forecast: List<fr.alex.chambre.ha.ForecastEntry>,
    forecastDaily: List<fr.alex.chambre.ha.ForecastEntry>,
) {
    val entities by vm.ha.entities.collectAsStateWithLifecycle()
    val now = rememberNow()
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatDate(now.toLocalDate()), style = MaterialTheme.typography.headlineSmall, color = colors.onPrimaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) colors.tertiary else colors.error))
                Spacer(Modifier.width(6.dp))
                Text(if (online) "En ligne" else "Hors ligne", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        FilledTonalIconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Réglages") }
    }
    val weather = entities[Entities.WEATHER]
    val condition = weather?.state?.takeUnless { it == "unavailable" || it == "unknown" }
    if (condition != null) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                WeatherBlock(condition, weather.temperature, forecast, forecastDaily, now.toLocalDate(), hours = 4, days = 4)
            }
        }
    }
}

@Composable
private fun RoomHeading(tab: SalonTab, temperature: String?, humidity: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val name = when (tab) {
            SalonTab.SALON -> "Salon"
            SalonTab.CUISINE -> "Cuisine"
            SalonTab.CHAMBRE -> "Chambre"
        }
        Text(name, style = MaterialTheme.typography.titleMedium, color = muted, modifier = Modifier.weight(1f))
        if (tab == SalonTab.SALON) {
            val t = temperature?.toDoubleOrNull()?.let { "%.1f °C".format(it).replace('.', ',') }
            val h = humidity?.toDoubleOrNull()?.let { "${it.toInt()} %" }
            Text(listOfNotNull(t, h).joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = muted)
        }
    }
}
