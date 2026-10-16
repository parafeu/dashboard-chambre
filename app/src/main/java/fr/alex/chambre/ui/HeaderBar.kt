package fr.alex.chambre.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.alex.chambre.ha.ForecastHour
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val HOUR = DateTimeFormatter.ofPattern("HH'h'")

/** Current local time, updated at each minute boundary. */
@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1_000L - now.nano / 1_000_000 + 50)
        }
    }
    return now
}

@Composable
fun HeaderBar(
    currentCondition: String?,
    currentTemp: Double?,
    forecast: List<ForecastHour>,
    online: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TIME.format(now),
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-3).sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    // null means the 3 s elapsed with the finger still down; a lift or a swipe
                    // away (cancellation) returns Unit and must not open the settings.
                    val endedEarly = withTimeoutOrNull(3_000) { waitForUpOrCancellation(); Unit }
                    if (endedEarly == null) onOpenSettings()
                }
            },
        )
        Spacer(Modifier.width(24.dp))
        Text(
            formatDate(now.toLocalDate()),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Icon(weatherIcon(currentCondition), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    currentTemp?.let { "${it.roundToInt()}°" } ?: "–",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(weatherLabel(currentCondition), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val upcoming = forecast.filter { it.time.isAfter(Instant.now()) }.take(4)
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.width(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                upcoming.forEach { h -> ForecastItem(h) }
            }
        }
    }
}

@Composable
private fun ForecastItem(h: ForecastHour) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(HOUR.format(h.time.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(weatherIcon(h.condition), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${h.temperature.roundToInt()}°", style = MaterialTheme.typography.titleMedium)
    }
}
