package fr.alex.chambre.announce

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId

/** A calendar synced on the tablet (Google account, DAVx5…). [id] is the Android calendar id. */
data class DeviceCalendar(val id: String, val name: String, val account: String)

sealed interface CalendarRead {
    data class Events(val events: List<CalendarEvent>) : CalendarRead
    data object NoPermission : CalendarRead
    data object NotFound : CalendarRead
}

/** The tablet's calendars. Blocking: call off the main thread. */
interface CalendarSource {
    /** Calendars on the tablet, or null without the calendar permission. */
    fun calendars(): List<DeviceCalendar>?

    /** Occurrences of [calendarId] (recurrences expanded) overlapping [from, to). */
    fun read(calendarId: String, from: Instant, to: Instant): CalendarRead
}

class AndroidCalendarSource(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : CalendarSource {
    private val allowed: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun calendars(): List<DeviceCalendar>? {
        if (!allowed) return null
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME),
                null, null,
                CalendarContract.Calendars.ACCOUNT_NAME + ", " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) add(DeviceCalendar(c.getLong(0).toString(), c.getString(1).orEmpty(), c.getString(2).orEmpty()))
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            null
        }
    }

    override fun read(calendarId: String, from: Instant, to: Instant): CalendarRead {
        if (!allowed) return CalendarRead.NoPermission
        val id = calendarId.toLongOrNull() ?: return CalendarRead.NotFound
        return try {
            val r = context.contentResolver
            val exists = r.query(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
                arrayOf(CalendarContract.Calendars._ID), null, null, null,
            )?.use { it.count > 0 } ?: false
            if (!exists) return CalendarRead.NotFound
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, from.toEpochMilli()); ContentUris.appendId(it, to.toEpochMilli()) }
                .build()
            val events = r.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                // STATUS is usually NULL: "!= ?" alone would drop those rows.
                "${CalendarContract.Instances.CALENDAR_ID} = ? AND " +
                    "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ?)",
                arrayOf(id.toString(), CalendarContract.Instances.STATUS_CANCELED.toString()),
                CalendarContract.Instances.BEGIN,
            )?.use { c ->
                val z = zone()
                buildList { while (c.moveToNext()) add(instanceEvent(calendarId, c.getString(0), c.getLong(1), c.getInt(2) != 0, z)) }
            }.orEmpty()
            CalendarRead.Events(events)
        } catch (e: SecurityException) {
            CalendarRead.NoPermission
        } catch (e: RuntimeException) {
            Log.w("Calendar", "Could not read calendar $calendarId", e)
            CalendarRead.NotFound
        }
    }
}
