package fr.alex.chambre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.temperature
import fr.alex.chambre.presence.Presence

/** A titled rounded container on surfaceContainer. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxHeight(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            content()
        }
    }
}

@Composable
fun DashboardScreen(vm: DashboardViewModel, onOpenSettings: () -> Unit) {
    val entities by vm.ha.entities.collectAsStateWithLifecycle()
    val forecast by vm.ha.forecast.collectAsStateWithLifecycle()
    val forecastDaily by vm.ha.forecastDaily.collectAsStateWithLifecycle()
    val battery by vm.battery.state.collectAsStateWithLifecycle()
    val conn by vm.ha.conn.collectAsStateWithLifecycle()
    val lastScene by vm.controller.lastScene.collectAsStateWithLifecycle()
    val presence by vm.presence.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.ha.errors.collect { snackbar.showSnackbar(it) } }

    val online = conn == ConnState.CONNECTED
    val weather = entities[Entities.WEATHER]

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HeaderBar(
                    currentCondition = weather?.state?.takeUnless { it == "unavailable" || it == "unknown" },
                    currentTemp = weather?.temperature,
                    hourly = forecast,
                    daily = forecastDaily,
                    online = online,
                    battery = battery,
                    onOpenSettings = onOpenSettings,
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ScenesPanel(entities, lastScene, online, vm.controller::runScene, Modifier.weight(1.1f))
                    LightPanel(entities[Entities.LIGHT], online, vm.controller, Modifier.weight(1.4f), asleep = presence == Presence.ASLEEP)
                    CoverPanel(entities[Entities.COVER], online, vm.controller, Modifier.weight(1f))
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}
