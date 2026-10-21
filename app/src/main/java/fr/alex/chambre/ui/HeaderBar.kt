package fr.alex.chambre.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.alex.chambre.battery.BatteryIconKind
import fr.alex.chambre.battery.BatteryState
import fr.alex.chambre.battery.batteryIconKind
import fr.alex.chambre.ha.ForecastEntry
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val HOUR = DateTimeFormatter.ofPattern("HH'h'")
private val RAIN = Color(0xFFA3C9FF)

private enum class WeatherMode { HOURS, WEEK }

/** Current local time, updated at each minute boundary. */
@Composable
internal fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1_000L - now.nano / 1_000_000 + 50)
        }
    }
    return now
}

private fun batteryIcon(kind: BatteryIconKind): ImageVector = when (kind) {
    BatteryIconKind.CHARGING -> Icons.Filled.BatteryChargingFull
    BatteryIconKind.FULL -> Icons.Filled.BatteryFull
    BatteryIconKind.BAR_6 -> Icons.Filled.Battery6Bar
    BatteryIconKind.BAR_5 -> Icons.Filled.Battery5Bar
    BatteryIconKind.BAR_4 -> Icons.Filled.Battery4Bar
    BatteryIconKind.BAR_3 -> Icons.Filled.Battery3Bar
    BatteryIconKind.BAR_2 -> Icons.Filled.Battery2Bar
    BatteryIconKind.BAR_1 -> Icons.Filled.Battery1Bar
    BatteryIconKind.ALERT -> Icons.Filled.BatteryAlert
}

/** Lays a rotated child out with swapped width/height (for vertical labels). */
private fun Modifier.vertical() = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
}

@Composable
fun HeaderBar(
    currentCondition: String?,
    currentTemp: Double?,
    hourly: List<ForecastEntry>,
    daily: List<ForecastEntry>,
    online: Boolean,
    battery: BatteryState?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            TIME.format(now),
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-3).sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDate(now.toLocalDate()), style = MaterialTheme.typography.headlineSmall, color = muted)
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenSettings).padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (battery != null) {
                    Icon(batteryIcon(batteryIconKind(battery)), null, Modifier.size(18.dp), tint = muted)
                    Text("${battery.levelPct} %", style = MaterialTheme.typography.labelLarge, color = muted)
                    Spacer(Modifier.width(14.dp))
                }
                Icon(Icons.Filled.Settings, "Réglages", Modifier.size(20.dp), tint = muted)
            }
        }
        if (!online) {
            Spacer(Modifier.width(20.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(6.dp))
                    Text("Hors ligne", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (currentCondition != null) {
            WeatherBlock(currentCondition, currentTemp, hourly, daily, now.toLocalDate())
        }
    }
}

@Composable
internal fun WeatherBlock(
    condition: String,
    temp: Double?,
    hourly: List<ForecastEntry>,
    daily: List<ForecastEntry>,
    today: LocalDate,
    /** How many hours / days fit beside the current weather (fewer on a phone). */
    hours: Int = 7,
    days: Int = 6,
) {
    var mode by remember { mutableStateOf(WeatherMode.HOURS) }
    LaunchedEffect(mode) {
        if (mode == WeatherMode.WEEK) {
            delay(30_000)
            mode = WeatherMode.HOURS
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                mode = if (mode == WeatherMode.HOURS) WeatherMode.WEEK else WeatherMode.HOURS
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.vertical().rotate(-90f), verticalAlignment = Alignment.CenterVertically) {
            Dot(mode == WeatherMode.HOURS)
            Text(
                if (mode == WeatherMode.HOURS) "HEURES" else "SEMAINE",
                Modifier.padding(horizontal = 6.dp),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = muted,
            )
            Dot(mode == WeatherMode.WEEK)
        }
        Icon(weatherIcon(condition), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(temp?.let { "${it.roundToInt()}°" } ?: "–", fontSize = 40.sp)
            Text(weatherLabel(condition), style = MaterialTheme.typography.labelLarge, color = muted)
        }
        AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "weather") { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (m == WeatherMode.HOURS) {
                    hourly.filter { it.time.isAfter(Instant.now()) }.take(hours).forEach { HourItem(it) }
                } else {
                    dailyByDate(daily).take(days).forEach { (date, e) -> DayItem(dayLabel(date, today), e) }
                }
            }
        }
    }
}

@Composable
private fun Dot(on: Boolean) {
    Box(
        Modifier.size(5.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun dailyByDate(daily: List<ForecastEntry>): List<Pair<LocalDate, ForecastEntry>> =
    daily.map { it.time.atZone(ZoneId.systemDefault()).toLocalDate() to it }.distinctBy { it.first }

@Composable
private fun HourItem(h: ForecastEntry) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(HOUR.format(h.time.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(weatherIcon(h.condition), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${h.temperature.roundToInt()}°", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DayItem(label: String, d: ForecastEntry) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(weatherIcon(d.condition), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${d.temperature.roundToInt()}°", style = MaterialTheme.typography.titleMedium)
        d.templow?.let { Text("${it.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        d.precipitation?.takeIf { it > 0.5 }?.let { Text("%.1f mm".format(it).replace('.', ','), style = MaterialTheme.typography.labelSmall, color = RAIN) }
    }
}
