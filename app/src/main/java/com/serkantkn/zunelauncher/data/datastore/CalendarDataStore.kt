package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.calendarDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_events")

class CalendarDataStore(private val context: Context) {

    private val EVENTS_KEY = stringPreferencesKey("calendar_events_json")

    val eventsFlow: Flow<List<CalendarEvent>> = context.calendarDataStore.data.map { preferences ->
        val jsonString = preferences[EVENTS_KEY]
        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<CalendarEvent>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CalendarEvent(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            timestamp = obj.getLong("timestamp"),
                            hour = obj.optInt("hour", 10),
                            minute = obj.optInt("minute", 0),
                            category = obj.optString("category", "Genel"),
                            colorHex = obj.optString("colorHex", "#E0007A")
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

    suspend fun saveEvents(events: List<CalendarEvent>) {
        val array = JSONArray()
        for (event in events) {
            val obj = JSONObject()
            obj.put("id", event.id)
            obj.put("title", event.title)
            obj.put("description", event.description)
            obj.put("timestamp", event.timestamp)
            obj.put("hour", event.hour)
            obj.put("minute", event.minute)
            obj.put("category", event.category)
            obj.put("colorHex", event.colorHex)
            array.put(obj)
        }
        context.calendarDataStore.edit { preferences ->
            preferences[EVENTS_KEY] = array.toString()
        }
    }
}
