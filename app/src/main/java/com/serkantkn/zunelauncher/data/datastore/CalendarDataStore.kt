package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.calendarDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_events")

/**
 * The events the launcher keeps itself, and what it has been told about the phone's calendars.
 *
 * There are still events of our own even now that the hub reads the real calendar: a reminder
 * attached to a note is the launcher's business and has no place in somebody's work calendar, and
 * a phone whose calendar permission has been refused still has to be able to write something down.
 *
 * Events written by older builds carried a date in `timestamp` and a time in `hour`/`minute`; they
 * are read back by putting the two together, which is what they always meant.
 */
class CalendarDataStore(private val context: Context) {

    // ── The launcher's own events ───────────────────────────────────────────

    val eventsFlow: Flow<List<CalendarEvent>> = context.calendarDataStore.data.map { preferences ->
        val jsonString = preferences[EVENTS_KEY] ?: return@map emptyList()
        try {
            val array = JSONArray(jsonString)
            (0 until array.length()).map { read(array.getJSONObject(it)) }
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the event list would not read", e)
            emptyList()
        }
    }

    suspend fun saveEvents(events: List<CalendarEvent>) {
        val array = JSONArray()
        events.forEach { array.put(write(it)) }
        val jsonString = array.toString()
        context.calendarDataStore.edit { preferences -> preferences[EVENTS_KEY] = jsonString }
    }

    private fun read(obj: JSONObject): CalendarEvent {
        val start = when {
            obj.has("startMillis") -> obj.getLong("startMillis")
            // Written by an older build: a date in one field and a time in two others.
            else -> java.util.Calendar.getInstance().apply {
                timeInMillis = obj.optLong("timestamp", 0L)
                set(java.util.Calendar.HOUR_OF_DAY, obj.optInt("hour", 10))
                set(java.util.Calendar.MINUTE, obj.optInt("minute", 0))
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return CalendarEvent(
            id = obj.getString("id"),
            title = obj.getString("title"),
            description = obj.optString("description", ""),
            location = obj.optString("location", ""),
            startMillis = start,
            endMillis = obj.optLong("endMillis", start + CalendarEvent.DEFAULT_DURATION_MS),
            isAllDay = obj.optBoolean("isAllDay", false),
            colorHex = obj.optString("colorHex", CalendarEvent.DEFAULT_COLOR),
            reminderMinutes = if (obj.isNull("reminderMinutes")) null else obj.optInt("reminderMinutes"),
            recurrence = if (obj.isNull("recurrence")) null else obj.optString("recurrence", "").ifEmpty { null },
            linkedNoteId = if (obj.isNull("linkedNoteId")) null else obj.optString("linkedNoteId", "").ifEmpty { null },
            isLocal = true
        )
    }

    private fun write(event: CalendarEvent): JSONObject = JSONObject().apply {
        put("id", event.id)
        put("title", event.title)
        put("description", event.description)
        put("location", event.location)
        put("startMillis", event.startMillis)
        put("endMillis", event.endMillis)
        put("isAllDay", event.isAllDay)
        put("colorHex", event.colorHex)
        put("reminderMinutes", event.reminderMinutes ?: JSONObject.NULL)
        put("recurrence", event.recurrence ?: JSONObject.NULL)
        put("linkedNoteId", event.linkedNoteId ?: JSONObject.NULL)
    }

    // ── What to show from the phone's calendars ─────────────────────────────

    /**
     * Which phone calendars appear in the hub. Null means nobody has chosen, and everything the
     * phone marks as visible is shown; an empty set is a choice, and means none of them.
     */
    val visibleCalendars: Flow<Set<Long>?> = context.calendarDataStore.data.map { prefs ->
        val raw = prefs[VISIBLE_CALENDARS] ?: return@map null
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getLong(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun setVisibleCalendars(ids: Set<Long>) {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        context.calendarDataStore.edit { it[VISIBLE_CALENDARS] = array.toString() }
    }

    /** The calendar a new event is written to; -1 is the launcher's own store. */
    val defaultCalendarId: Flow<Long?> = context.calendarDataStore.data.map { prefs ->
        prefs[DEFAULT_CALENDAR]
    }

    suspend fun setDefaultCalendarId(id: Long) {
        context.calendarDataStore.edit { it[DEFAULT_CALENDAR] = id }
    }

    /** How long before a new event the reminder is set for; null means no reminder by default. */
    val defaultReminderMinutes: Flow<Int?> = context.calendarDataStore.data.map { prefs ->
        val stored = prefs[DEFAULT_REMINDER] ?: CalendarEvent.DEFAULT_REMINDER_MINUTES
        stored.takeIf { it >= 0 }
    }

    suspend fun setDefaultReminderMinutes(minutes: Int?) {
        context.calendarDataStore.edit { it[DEFAULT_REMINDER] = minutes ?: -1 }
    }

    /** Which day the month grid starts on: 0 follows the phone's language, else a Calendar day. */
    val weekStart: Flow<Int> = context.calendarDataStore.data.map { prefs ->
        prefs[WEEK_START] ?: 0
    }

    suspend fun setWeekStart(day: Int) {
        context.calendarDataStore.edit { it[WEEK_START] = day }
    }

    /** Whether the agenda keeps showing things that have already happened. */
    val showPastEvents: Flow<Boolean> = context.calendarDataStore.data.map { prefs ->
        prefs[SHOW_PAST] ?: false
    }

    suspend fun setShowPastEvents(enabled: Boolean) {
        context.calendarDataStore.edit { it[SHOW_PAST] = enabled }
    }

    private companion object {
        const val TAG = "CalendarDataStore"
        val EVENTS_KEY = stringPreferencesKey("calendar_events_json")
        val VISIBLE_CALENDARS = stringPreferencesKey("visible_calendars")
        val DEFAULT_CALENDAR = longPreferencesKey("default_calendar")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder_minutes")
        val WEEK_START = intPreferencesKey("week_start")
        val SHOW_PAST = booleanPreferencesKey("show_past_events")
    }
}
