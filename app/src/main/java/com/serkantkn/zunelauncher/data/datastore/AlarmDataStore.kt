package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarms")

/**
 * Where the alarms live.
 *
 * One JSON array under one key: there are never many alarms, they are always read and written as a
 * whole list, and a row store would buy nothing. Every field added since is read with a default,
 * so a list written by an older build still loads.
 */
class AlarmDataStore(private val context: Context) {

    private val ALARMS_KEY = stringPreferencesKey("alarms_json")

    val alarmsFlow: Flow<List<Alarm>> = context.alarmDataStore.data.map { preferences ->
        val jsonString = preferences[ALARMS_KEY] ?: return@map emptyList()
        try {
            val array = JSONArray(jsonString)
            (0 until array.length()).map { index -> read(array.getJSONObject(index)) }
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the alarm list would not read", e)
            emptyList()
        }
    }

    suspend fun saveAlarms(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(write(it)) }
        val jsonString = array.toString()
        context.alarmDataStore.edit { preferences ->
            preferences[ALARMS_KEY] = jsonString
        }
    }

    private fun read(obj: JSONObject): Alarm {
        val daysArray = obj.optJSONArray("daysOfWeek")
        val days = buildSet {
            if (daysArray != null) for (j in 0 until daysArray.length()) add(daysArray.getInt(j))
        }
        return Alarm(
            id = obj.getString("id"),
            hour = obj.getInt("hour"),
            minute = obj.getInt("minute"),
            label = obj.optString("label", ""),
            isEnabled = obj.optBoolean("isEnabled", true),
            daysOfWeek = days,
            noteId = if (obj.isNull("noteId")) null else obj.optString("noteId", "").ifEmpty { null },
            exactTimeMillis = if (obj.isNull("exactTimeMillis")) null else obj.optLong("exactTimeMillis"),
            ringtoneUri = if (obj.isNull("ringtoneUri")) null else obj.optString("ringtoneUri", "").ifEmpty { null },
            vibrate = obj.optBoolean("vibrate", true),
            snoozeMinutes = obj.optInt("snoozeMinutes", Alarm.DEFAULT_SNOOZE_MINUTES),
            gradualVolume = obj.optBoolean("gradualVolume", true),
            autoSilenceMinutes = obj.optInt("autoSilenceMinutes", Alarm.DEFAULT_AUTO_SILENCE_MINUTES),
            snoozedUntilMillis = if (obj.isNull("snoozedUntilMillis")) null else obj.optLong("snoozedUntilMillis")
        )
    }

    private fun write(alarm: Alarm): JSONObject = JSONObject().apply {
        put("id", alarm.id)
        put("hour", alarm.hour)
        put("minute", alarm.minute)
        put("label", alarm.label)
        put("isEnabled", alarm.isEnabled)
        put("daysOfWeek", JSONArray().also { days -> alarm.daysOfWeek.forEach { days.put(it) } })
        put("noteId", alarm.noteId ?: JSONObject.NULL)
        put("exactTimeMillis", alarm.exactTimeMillis ?: JSONObject.NULL)
        put("ringtoneUri", alarm.ringtoneUri ?: JSONObject.NULL)
        put("vibrate", alarm.vibrate)
        put("snoozeMinutes", alarm.snoozeMinutes)
        put("gradualVolume", alarm.gradualVolume)
        put("autoSilenceMinutes", alarm.autoSilenceMinutes)
        put("snoozedUntilMillis", alarm.snoozedUntilMillis ?: JSONObject.NULL)
    }

    private companion object {
        const val TAG = "AlarmDataStore"
    }
}
