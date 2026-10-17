package fr.alex.chambre.ui.salon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.alex.chambre.announce.CalendarEvent
import fr.alex.chambre.ui.Section
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** « DANS 2 H 15 », « DANS 12 MIN », « MAINTENANT ». */
fun untilLabel(now: Instant, start: Instant): String {
    val min = Duration.between(now, start).toMinutes()
    return when {
        min < 1 -> "MAINTENANT"
        min < 60 -> "DANS $min MIN"
        min % 60 == 0L -> "DANS ${min / 60} H"
        else -> "DANS ${min / 60} H ${"%02d".format(min % 60)}"
    }
}

/** Today's calendar, next announcement first (the prototype's « E » calendar). */
@Composable
fun AgendaPanel(
    upcoming: List<CalendarEvent>,
    workCalendar: String,
    now: Instant,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val list = upcoming.filter { it.allDay || !it.start.isBefore(now) }
    val next = list.firstOrNull()
    Section("Prochaine annonce", modifier) {
        if (next == null) {
            Text(
                error ?: "Rien de prévu pour le reste de la journée.",
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        val isWork = next.calendar == workCalendar
        Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFF6A3B00), contentColor = Color(0xFFFFDCBE), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (isWork) "RÉUNION" else "ALERTE") + " · " + timeLabel(next, zone) + (if (next.allDay) "" else " · " + untilLabel(now, next.start)),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                )
                Text(next.title, fontSize = 34.sp, fontWeight = FontWeight.Light, lineHeight = 40.sp, color = Color(0xFFFFF3E6), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(if (isWork) "Travail · annoncée 5 min avant" else "Alerte · à confirmer", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (list.size > 1) {
            Text("Ensuite aujourd'hui", Modifier.padding(start = 8.dp, top = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.drop(1).take(4).forEach { e ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(timeLabel(e, zone), fontSize = 22.sp, fontWeight = FontWeight.Light)
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (e.calendar == workCalendar) "Travail" else "Alerte à confirmer",
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.calendar == workCalendar) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun timeLabel(e: CalendarEvent, zone: ZoneId): String =
    if (e.allDay) "Journée" else HM.format(e.start.atZone(zone))
