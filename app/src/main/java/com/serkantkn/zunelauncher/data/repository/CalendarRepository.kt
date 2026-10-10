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
import com.serkantkn.zunelauncher.util.allDayLocalEnd
import com.serkantkn.zunelauncher.util.allDayLocalStart
import com.serkantkn.zunelauncher.util.allDayUtcEnd
import com.serkantkn.zunelauncher.util.allDayUtcStart
import java.util.Calendar
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
 * Changing or deleting one of those weeks is an *exception* row keyed by the week's original
 * start, never a rewrite of the series: that is how every calendar app does it, and it is the
 * difference between skipping one meeting and cancelling them all.
 *
 * All-day events are stored by the provider as UTC midnights with their zone set to UTC. They are
 * converted to local midnights on the way in and back on the way out, so a birthday is on its
 * day in Istanbul and in Los Angeles alike.
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

        // One reminder query per event, not per occurrence: a weekly meeting is one event.
        val reminders = HashMap<Long, Int?>()

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
                            val isAllDay = cursor.getInt(6) == 1
                            val rrule = cursor.getString(9)?.takeIf { it.isNotBlank() }
                            val start = if (isAllDay) allDayLocalStart(begin) else begin
                            val finish = when {
                                isAllDay -> allDayLocalEnd(if (end > begin) end else begin + DAY_MS)
                                end > begin -> end
                                else -> begin + CalendarEvent.DEFAULT_DURATION_MS
                            }
                            add(
                                CalendarEvent(
                                    // An instance is identified by its event and which occurrence
                                    // it is, so two weeks of the same meeting are two list entries.
                                    id = "$SYSTEM_PREFIX$eventId:$begin",
                                    title = cursor.getString(1).orEmpty(),
                                    description = cursor.getString(2).orEmpty(),
                                    location = cursor.getString(3).orEmpty(),
                                    startMillis = start,
                                    endMillis = finish,
                                    isAllDay = isAllDay,
                                    colorHex = colorToHex(cursor.getInt(7)),
                                    reminderMinutes = reminders.getOrPut(eventId) { reminderMinutesOf(eventId) },
                                    recurrence = rrule,
                                    calendarId = calendarId,
                                    systemEventId = eventId,
                                    isLocal = false,
                                    isEditable = calendarId in writableIds,
                                    seriesId = if (rrule != null) "$SYSTEM_PREFIX$eventId" else null,
                                    // The provider's own number for this occurrence, untouched:
                                    // it is what an exception row is keyed by.
                                    instanceStartMillis = if (rrule != null) begin else null
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
        }
        return runCatching {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id != null && event.reminderMinutes != null) setReminder(id, event.reminderMinutes)
            id
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not take the new event", it)
            null
        }
    }

    /**
     * Writes changes back to an event that is already in a phone calendar: a one-off event, or
     * a whole series.
     *
     * For a series the date is the master's own: what was opened was one week of it, and moving
     * the series to that week would shift every other week along. Its time of day, length and
     * everything else are taken from the draft.
     */
    fun update(event: CalendarEvent): Boolean {
        val eventId = event.systemEventId ?: return false
        if (!canWrite()) return false
        return runCatching {
            val values = if (event.isRepeating && event.isOccurrence) {
                seriesValuesOf(event, masterStartOf(eventId) ?: event.startMillis)
            } else {
                valuesOf(event)
            }
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = context.contentResolver.update(uri, values, null, null)
            // Reminders are only rewritten when the draft changed them: every rewrite is a
            // delete-and-insert that other calendar apps see as a change.
            if (reminderMinutesOf(eventId) != event.reminderMinutes) setReminder(eventId, event.reminderMinutes)
            rows > 0
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not change the event", it)
            false
        }
    }

    /**
     * Changes one occurrence of a repeating event and nothing else, by writing an exception row
     * against the series keyed by the occurrence's original start.
     */
    fun updateOccurrence(event: CalendarEvent): Boolean {
        val masterId = event.systemEventId ?: return false
        val original = event.instanceStartMillis ?: return false
        if (!canWrite()) return false
        val values = valuesOf(event.copy(recurrence = null)).apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, original)
            put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (event.isAllDay) 1 else 0)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }
        return runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, masterId)
            val id = context.contentResolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
            if (id != null && event.reminderMinutes != reminderMinutesOf(masterId)) {
                setReminder(id, event.reminderMinutes)
            }
            id != null
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not change the one occurrence", it)
            false
        }
    }

    /** Deletes a one-off event, or a whole series. */
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

    /** Takes one occurrence out of a series: a cancelled exception, the series untouched. */
    fun deleteOccurrence(event: CalendarEvent): Boolean {
        val masterId = event.systemEventId ?: return false
        val original = event.instanceStartMillis ?: return false
        if (!canWrite()) return false
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, original)
            put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (event.isAllDay) 1 else 0)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        return runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, masterId)
            context.contentResolver.insert(uri, values) != null
        }.getOrElse {
            ZuneLog.w(TAG, "the phone would not cancel the one occurrence", it)
            false
        }
    }

    private fun valuesOf(event: CalendarEvent): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DESCRIPTION, event.description)
        put(CalendarContract.Events.EVENT_LOCATION, event.location)
        put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
        val start: Long
        val end: Long
        if (event.isAllDay) {
            start = allDayUtcStart(event.startMillis)
            end = allDayUtcEnd(event.startMillis, event.endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            start = event.startMillis
            end = event.endMillis
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        put(CalendarContract.Events.DTSTART, start)
        if (event.recurrence.isNullOrBlank()) {
            put(CalendarContract.Events.DTEND, end)
            putNull(CalendarContract.Events.RRULE)
            putNull(CalendarContract.Events.DURATION)
        } else {
            // A repeating event is stored as a start plus a length, never as an end: an end date
            // would say when the first one finishes, not how long each of them runs.
            putNull(CalendarContract.Events.DTEND)
            put(CalendarContract.Events.RRULE, event.recurrence)
            put(CalendarContract.Events.DURATION, durationRule(event, start, end))
        }
    }

    /** The values for a whole series: the draft's everything, on the master's own date. */
    private fun seriesValuesOf(event: CalendarEvent, masterStart: Long): ContentValues {
        val start = if (event.isAllDay) {
            allDayLocalStart(masterStart)
        } else {
            val draft = Calendar.getInstance().apply { timeInMillis = event.startMillis }
            Calendar.getInstance().apply {
                timeInMillis = masterStart
                set(Calendar.HOUR_OF_DAY, draft.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, draft.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return valuesOf(event.copy(startMillis = start, endMillis = start + event.durationMillis))
    }

    /** When a series begins, as the provider has it (a UTC midnight for an all-day one). */
    private fun masterStartOf(eventId: Long): Long? = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.ALL_DAY),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val start = cursor.getLong(0)
            if (cursor.getInt(1) == 1) allDayLocalStart(start) else start
        }
    }.getOrNull()

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

    private fun durationRule(event: CalendarEvent, start: Long, end: Long): String =
        if (event.isAllDay) "P${((end - start) / DAY_MS).coerceAtLeast(1L)}D"
        else "P${((end - start) / 1000L).coerceAtLeast(1L)}S"

    private fun colorToHex(color: Int): String =
        if (color == 0) CalendarEvent.DEFAULT_COLOR else String.format("#%06X", 0xFFFFFF and color)

    private companion object {
        const val TAG = "CalendarRepository"
        const val SYSTEM_PREFIX = "sys:"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Whether an event id names a row in the phone's calendar rather than one of the launcher's own. */
fun String.isSystemEventId(): Boolean = startsWith("sys:")
