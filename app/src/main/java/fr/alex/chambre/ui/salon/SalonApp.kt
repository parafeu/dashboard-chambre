package fr.alex.chambre.ui.salon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alex.chambre.announce.Announcement
import fr.alex.chambre.announce.Tick
import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.temperature
import fr.alex.chambre.presence.Presence
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.ui.ChambrePanels
import fr.alex.chambre.ui.CoverPanel
import fr.alex.chambre.ui.DashboardViewModel
import fr.alex.chambre.ui.HeaderBar
import fr.alex.chambre.ui.LightPanel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SalonTab { SALON, CUISINE, CHAMBRE }

private const val BACK_TO_SALON_MS = 120_000L

/** The living-room tablet: room rail + Salon / Cuisine / Chambre, and announcement overlays. */
@Composable
fun SalonApp(vm: DashboardViewModel, settings: Settings, onOpenSettings: () -> Unit) {
    val entities by vm.ha.entities.collectAsStateWithLifecycle()
    val forecast by vm.ha.forecast.collectAsStateWithLifecycle()
    val forecastDaily by vm.ha.forecastDaily.collectAsStateWithLifecycle()
    val conn by vm.ha.conn.collectAsStateWithLifecycle()
    val battery by vm.battery.state.collectAsStateWithLifecycle()
    val presence by vm.presence.state.collectAsStateWithLifecycle()
    val lastScene by vm.controller.lastScene.collectAsStateWithLifecycle()
    val tick by vm.announcements.state.collectAsStateWithLifecycle()
    val upcoming by vm.announcements.upcoming.collectAsStateWithLifecycle()
    val calendarError by vm.announcements.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.ha.errors.collect { snackbar.showSnackbar(it) } }

    var tab by remember { mutableStateOf(SalonTab.SALON) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(tab, touches) {
        if (tab != SalonTab.SALON) {
            delay(BACK_TO_SALON_MS)
            tab = SalonTab.SALON
        }
    }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(tab) {
        while (true) {
            now = Instant.now()
            delay(if (tab == SalonTab.CUISINE) 1_000 else 30_000)
        }
    }
    // An announcement on screen keeps the tablet awake until it is dismissed.
    LaunchedEffect(tick.alerts.isNotEmpty() || tick.banner != null) {
        vm.presence.hold = tick.alerts.isNotEmpty() || tick.banner != null
    }

    val online = conn == ConnState.CONNECTED
    val weather = entities[Entities.WEATHER]
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touches++
                }
            }
        },
    ) {
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
                RoomRail(
                    selected = tab,
                    onSelect = { tab = it },
                    summaries = mapOf(
                        SalonTab.SALON to roomSummary(entities[Entities.SALON_ROOM.light], entities[Entities.SALON_ROOM.cover], plural = true),
                        SalonTab.CUISINE to kitchenSummary(entities[Entities.KITCHEN_COVER], Entities.KITCHEN_TIMERS.map { entities[it] }),
                        SalonTab.CHAMBRE to roomSummary(entities[Entities.LIGHT], entities[Entities.COVER], plural = false),
                    ),
                    temperature = entities[Entities.SALON_TEMPERATURE]?.state,
                    humidity = entities[Entities.SALON_HUMIDITY]?.state,
                    modifier = Modifier.width(212.dp).fillMaxHeight(),
                )
                when (tab) {
                    SalonTab.SALON -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        AgendaPanel(upcoming, settings.workCalendar, now, calendarError, Modifier.weight(1f))
                        CoverPanel(entities[Entities.SALON_ROOM.cover], online, vm.salonController, Modifier.width(236.dp))
                        LightPanel(
                            entities[Entities.SALON_ROOM.light], online, vm.salonController, Modifier.width(236.dp),
                            colors = false, compact = true, title = "Lumières",
                        )
                    }
                    SalonTab.CUISINE -> CuisineScreen(
                        entities, online, vm.kitchenController, now,
                        onStartTimer = vm::startTimer, onCancelTimer = vm::cancelTimer, modifier = Modifier.weight(1f),
                    )
                    SalonTab.CHAMBRE -> ChambrePanels(entities, online, lastScene, vm.controller, presence == Presence.ASLEEP, Modifier.weight(1f), narrow = true)
                }
            }
        }
        AnnouncementOverlays(tick, onAcknowledge = vm.announcements::acknowledge)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
    }
    }
}

@Composable
private fun RoomRail(
    selected: SalonTab,
    onSelect: (SalonTab) -> Unit,
    summaries: Map<SalonTab, String>,
    temperature: String?,
    humidity: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(SalonTab.SALON, "Salon", Icons.Filled.Chair),
            Triple(SalonTab.CUISINE, "Cuisine", Icons.Filled.Kitchen),
            Triple(SalonTab.CHAMBRE, "Chambre", Icons.Filled.Bed),
        ).forEach { (t, label, icon) -> RailItem(label, summaries[t].orEmpty(), icon, t == selected) { onSelect(t) } }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Salon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(temperature?.toDoubleOrNull()?.let { "%.1f °C".format(it).replace('.', ',') } ?: "–", fontSize = 26.sp)
                Text(humidity?.toDoubleOrNull()?.let { "Humidité ${it.toInt()} %" } ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RailItem(label: String, summary: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 32.dp else 22.dp, label = "rail-corner")
    val bg by animateColorAsState(if (selected) colors.primary else colors.surfaceContainer, label = "rail-bg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "rail-fg")
    Surface(onClick = onClick, shape = RoundedCornerShape(corner), color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(30.dp))
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
        }
    }
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun AnnouncementOverlays(tick: Tick, onAcknowledge: (String) -> Unit) {
    val banner = tick.banner
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = banner != null && tick.alerts.isEmpty(),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
        ) {
            if (banner != null) Banner(banner, onAcknowledge)
        }
        val alert = tick.alerts.firstOrNull()
        AnimatedVisibility(visible = alert != null, enter = fadeIn(), exit = fadeOut()) {
            if (alert != null) AlertScreen(alert, tick.alerts.size, onAcknowledge)
        }
    }
}

@Composable
private fun Banner(a: Announcement, onAcknowledge: (String) -> Unit) {
    val (label, title, icon) = when (a) {
        is Announcement.Meeting -> Triple("RÉUNION DANS 5 MIN · " + HM.format(a.start.atZone(ZoneId.systemDefault())), a.title, Icons.AutoMirrored.Filled.VolumeUp)
        is Announcement.TimerDone -> Triple("MINUTEUR", a.name + " terminé", Icons.Filled.Timer)
        is Announcement.Alert -> Triple("ALERTE", a.title, Icons.Filled.NotificationsActive)
    }
    Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFF6A3B00), contentColor = Color(0xFFFFDCBE), shadowElevation = 12.dp, modifier = Modifier.width(780.dp)) {
        Row(Modifier.padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(60.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFF3E6))
            }
            if (a is Announcement.TimerDone) {
                Button(onClick = { onAcknowledge(a.key) }, modifier = Modifier.height(56.dp)) { Text("OK", style = MaterialTheme.typography.titleMedium) }
            } else {
                FilledTonalIconButton(
                    onClick = { onAcknowledge(a.key) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x26FFDCBE), contentColor = Color(0xFFFFDCBE)),
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Filled.Close, "Fermer") }
            }
        }
    }
}

@Composable
private fun AlertScreen(a: Announcement.Alert, pending: Int, onAcknowledge: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xDB0C0804))
            // The backdrop swallows taps: the light and shutter controls underneath must not react.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(40.dp), color = Color(0xFF2E251D), shadowElevation = 16.dp, modifier = Modifier.width(640.dp)) {
            Column(Modifier.padding(horizontal = 48.dp, vertical = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(104.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.NotificationsActive, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Text("ALERTE · " + HM.format(a.at.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(a.title, fontSize = 64.sp, fontWeight = FontWeight.Light, color = Color(0xFFFFF3E6), textAlign = TextAlign.Center, lineHeight = 68.sp)
                Text(
                    if (pending > 1) "$pending alertes en attente" else "Répétée toutes les 5 minutes jusqu'à confirmation.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { onAcknowledge(a.key) },
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth().height(88.dp).padding(top = 10.dp),
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("C'est fait", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

