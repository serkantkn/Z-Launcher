package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarms")

class AlarmDataStore(private val context: Context) {

    private val ALARMS_KEY = stringPreferencesKey("alarms_json")

    val alarmsFlow: Flow<List<Alarm>> = context.alarmDataStore.data.map { preferences ->
        val jsonString = preferences[ALARMS_KEY]
        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<Alarm>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val daysArray = obj.optJSONArray("daysOfWeek")
                    val daysSet = mutableSetOf<Int>()
                    if (daysArray != null) {
                        for (j in 0 until daysArray.length()) {
                            daysSet.add(daysArray.getInt(j))
                        }
                    }
                    list.add(
                        Alarm(
                            id = obj.getString("id"),
                            hour = obj.getInt("hour"),
                            minute = obj.getInt("minute"),
                            label = obj.optString("label", ""),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            daysOfWeek = daysSet
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun saveAlarms(alarms: List<Alarm>) {
        val array = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject()
            obj.put("id", alarm.id)
            obj.put("hour", alarm.hour)
            obj.put("minute", alarm.minute)
            obj.put("label", alarm.label)
            obj.put("isEnabled", alarm.isEnabled)
            val daysArray = JSONArray()
            for (day in alarm.daysOfWeek) {
                daysArray.put(day)
            }
            obj.put("daysOfWeek", daysArray)
            array.put(obj)
        }
        val jsonString = array.toString()
        context.alarmDataStore.edit { preferences ->
            preferences[ALARMS_KEY] = jsonString
        }
    }
}
