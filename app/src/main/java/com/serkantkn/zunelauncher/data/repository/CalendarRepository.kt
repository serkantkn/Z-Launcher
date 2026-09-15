package com.serkantkn.zunelauncher.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.CalendarInfo
import com.serkantkn.zunelauncher.util.ZuneLog
import java.util.TimeZone

/**
 * The phone's own calendar.
 *
 * Until now the hub had none of this: it kept a private list and a meeting somebody had put in the
 * user's actual calendar simply did not exist as far as the launcher was concerned. Everything
 * here goes through Android's calendar provider, which is the same store the phone's calendar app
 * and whatever syncs it all read and write.
 *
 * Repeating events are read through [CalendarContract.Instances] rather than the event rows, so a
 * weekly meeting appears once on every week it actually happens instead of once on the day it was
 * first created — the provider works the repeat rule out, which is the only sane way to do it.
 *
 * Every call is allowed to fail. A phone with the permission refused, a calendar that has gone
 * away, an OEM provider that refuses a column: none of those should be more than an empty list.
 */
class CalendarRepository(private val context: Context) {

    // ── Permission ──────────────────────────────────────────────────────────

    fun canRead(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun canWrite(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ── Which calendars exist ───────────────────────────────────────────────

    fun calendars(): List<CalendarInfo> {
        if (!canRead()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val access = cursor.getInt(4)
                        add(
                            CalendarInfo(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty(),
                                accountName = cursor.getString(2).orEmpty(),
                                colorHex = colorToHex(cursor.getInt(3)),
                                // Contributor and above may add events; a subscribed feed may not.
                                isWritable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                                isPrimary = cursor.getInt(5) == 1
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not list its calendars", it)
            emptyList()
        }
    }

    /** Where a new event goes when nobody has said otherwise. */
    fun defaultCalendar(): CalendarInfo? =
        calendars().filter { it.isWritable }.let { writable ->
            writable.firstOrNull { it.isPrimary } ?: writable.firstOrNull()
        }

    // ── Reading events ──────────────────────────────────────────────────────

    /**
     * Everything happening between two moments, one entry per actual occurrence.
     *
     * [visibleCalendarIds] null means every calendar; an empty set means none, which is a real
     * choice somebody can make and not the same thing as "not chosen yet".
     */
    fun events(
        fromMillis: Long,
        toMillis: Long,
        visibleCalendarIds: Set<Long>? = null
    ): List<CalendarEvent> {
        if (!canRead()) return emptyList()
        if (visibleCalendarIds != null && visibleCalendarIds.isEmpty()) return emptyList()

        val writableIds = calendars().filter { it.isWritable }.map { it.id }.toSet()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.RRULE
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(fromMillis.toString())
            .appendPath(toMillis.toString())
            .build()

        return runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val calendarId = cursor.getLong(8)
                            if (visibleCalendarIds != null && calendarId !in visibleCalendarIds) continue
                            val eventId = cursor.getLong(0)
                            val begin = cursor.getLong(4)
                            val end = cursor.getLong(5)
                            add(
                                CalendarEvent(
                                    // An instance is identified by its event and which occurrence
                                    // it is, so two weeks of the same meeting are two list entries.
                                    id = "$SYSTEM_PREFIX$eventId:$begin",
                                    title = cursor.getString(1).orEmpty(),
                                    description = cursor.getString(2).orEmpty(),
                                    location = cursor.getString(3).orEmpty(),
                                    startMillis = begin,
                                    endMillis = if (end > begin) end else begin + CalendarEvent.DEFAULT_DURATION_MS,
                                    isAllDay = cursor.getInt(6) == 1,
                                    colorHex = colorToHex(cursor.getInt(7)),
                                    recurrence = cursor.getString(9),
                                    calendarId = calendarId,
                                    systemEventId = eventId,
                                    isLocal = false,
                                    isEditable = calendarId in writableIds
                                )
                            )
                        }
                    }
                }.orEmpty()
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not read out its events", it)
            emptyList()
        }
    }

    // ── Writing events ──────────────────────────────────────────────────────

    /** Adds an event to a phone calendar. Returns its new row, or null if it would not take. */
    fun insert(event: CalendarEvent, calendarId: Long): Long? {
        if (!canWrite()) return null
        val values = valuesOf(event).apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return runCatching {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id != null) setReminder(id, event.reminderMinutes)
            id
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not take the new event", it)
            null
        }
    }

    /** Writes changes back to an event that is already in a phone calendar. */
    fun update(event: CalendarEvent): Boolean {
        val eventId = event.systemEventId ?: return false
        if (!canWrite()) return false
        return runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = context.contentResolver.update(uri, valuesOf(event), null, null)
            setReminder(eventId, event.reminderMinutes)
            rows > 0
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not change the event", it)
            false
        }
    }

    fun delete(event: CalendarEvent): Boolean {
        val eventId = event.systemEventId ?: return false
        if (!canWrite()) return false
        return runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not delete the event", it)
            false
        }
    }

    private fun valuesOf(event: CalendarEvent): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DESCRIPTION, event.description)
        put(CalendarContract.Events.EVENT_LOCATION, event.location)
        put(CalendarContract.Events.DTSTART, event.startMillis)
        put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
        if (event.recurrence.isNullOrBlank()) {
            put(CalendarContract.Events.DTEND, event.endMillis)
            putNull(CalendarContract.Events.RRULE)
            putNull(CalendarContract.Events.DURATION)
        } else {
            // A repeating event is stored as a start plus a length, never as an end: an end date
            // would say when the first one finishes, not how long each of them runs.
            putNull(CalendarContract.Events.DTEND)
            put(CalendarContract.Events.RRULE, event.recurrence)
            put(CalendarContract.Events.DURATION, durationRule(event.durationMillis))
        }
    }

    /**
     * Puts the warning on the event itself rather than keeping a private alarm for it.
     *
     * A reminder written here belongs to the event: it survives the launcher being uninstalled,
     * it follows the event onto the user's other devices, and whatever they already use to be
     * reminded is what reminds them — which is better than a second, launcher-shaped notification
     * arriving beside the one their calendar already sent.
     */
    private fun setReminder(eventId: Long, minutes: Int?) {
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString())
            )
            if (minutes == null) return
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
            )
        }.onFailure { ZuneLog.w(TAG, "the phone would not set the reminder", it) }
    }

    /** How long before an event a phone calendar is already set to warn, if at all. */
    fun reminderMinutesOf(eventId: Long): Int? {
        if (!canRead()) return null
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES),
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                null
            )?.use { if (it.moveToFirst()) it.getInt(0) else null }
        }.getOrNull()
    }

    private fun durationRule(millis: Long): String = "P${(millis / 1000L).coerceAtLeast(1L)}S"

    private fun colorToHex(color: Int): String =
        if (color == 0) CalendarEvent.DEFAULT_COLOR else String.format("#%06X", 0xFFFFFF and color)

    private companion object {
        const val TAG = "CalendarRepository"
        const val SYSTEM_PREFIX = "sys:"
    }
}

/** Whether an event id names a row in the phone's calendar rather than one of the launcher's own. */
fun String.isSystemEventId(): Boolean = startsWith("sys:")
