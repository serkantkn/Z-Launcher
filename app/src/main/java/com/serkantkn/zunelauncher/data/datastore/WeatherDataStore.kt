package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.data.model.WindUnit
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather")

/**
 * Saved places, unit preferences, the cached forecasts and the daily fetch counter of the weather
 * hub. The counter is what keeps the free tier inside its allowance; it resets on its own when the
 * calendar day changes.
 */
class WeatherDataStore(private val context: Context) {

    private val PLACES = stringPreferencesKey("places_json")
    private val SELECTED = stringPreferencesKey("selected_place")
    private val CACHE = stringPreferencesKey("cache_json")
    private val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
    private val WIND_UNIT = stringPreferencesKey("wind_unit")
    private val ANIMATED_SKY = booleanPreferencesKey("animated_sky")
    private val QUOTA_DAY = stringPreferencesKey("quota_day")
    private val QUOTA_USED = intPreferencesKey("quota_used")

    // --- Places -----------------------------------------------------------------------------------

    val places: Flow<List<WeatherPlace>> = context.weatherDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[PLACES], TAG, WeatherPlace::fromJson).filterNotNull()
    }

    /** Id of the place the hub opens on, or null before anything has been added. */
    val selectedPlaceId: Flow<String?> = context.weatherDataStore.data.map { it[SELECTED] }

    suspend fun putPlace(place: WeatherPlace) {
        val current = places.first().filterNot { it.id == place.id || (place.isHere && it.isHere) }
        savePlaces(listOf(place) + current)
    }

    suspend fun removePlace(id: String) {
        savePlaces(places.first().filterNot { it.id == id })
        if (selectedPlaceId.first() == id) {
            val next = places.first().firstOrNull()
            context.weatherDataStore.edit { preferences ->
                if (next == null) preferences.remove(SELECTED) else preferences[SELECTED] = next.id
            }
        }
        dropFromCache(id)
    }

    suspend fun selectPlace(id: String) {
        context.weatherDataStore.edit { it[SELECTED] = id }
    }

    private suspend fun savePlaces(list: List<WeatherPlace>) {
        context.weatherDataStore.edit { preferences ->
            preferences[PLACES] = list.toJsonArrayString { it.toJson() }
        }
    }

    // --- Units and look ---------------------------------------------------------------------------

    val temperatureUnit: Flow<TemperatureUnit> = context.weatherDataStore.data.map { preferences ->
        runCatching { TemperatureUnit.valueOf(preferences[TEMPERATURE_UNIT] ?: "") }
            .getOrDefault(TemperatureUnit.CELSIUS)
    }

    val windUnit: Flow<WindUnit> = context.weatherDataStore.data.map { preferences ->
        runCatching { WindUnit.valueOf(preferences[WIND_UNIT] ?: "") }.getOrDefault(WindUnit.KMH)
    }

    /** Whether the sky behind the hub moves. Off means a still gradient, which costs nothing. */
    val animatedSky: Flow<Boolean> = context.weatherDataStore.data.map { it[ANIMATED_SKY] ?: true }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.weatherDataStore.edit { it[TEMPERATURE_UNIT] = unit.name }
    }

    suspend fun setWindUnit(unit: WindUnit) {
        context.weatherDataStore.edit { it[WIND_UNIT] = unit.name }
    }

    suspend fun setAnimatedSky(enabled: Boolean) {
        context.weatherDataStore.edit { it[ANIMATED_SKY] = enabled }
    }

    // --- Cache ------------------------------------------------------------------------------------

    /** Last forecast fetched for each place, so the hub has something to show before the network. */
    val cache: Flow<Map<String, WeatherSnapshot>> = context.weatherDataStore.data.map { preferences ->
        readCache(preferences[CACHE])
    }

    suspend fun putSnapshot(snapshot: WeatherSnapshot) {
        context.weatherDataStore.edit { preferences ->
            val current = readCache(preferences[CACHE]).toMutableMap()
            current[snapshot.place.id] = snapshot
            // Only the most recently fetched places are worth keeping around.
            val trimmed = current.entries
                .sortedByDescending { it.value.fetchedAt }
                .take(MAX_CACHED_PLACES)
            preferences[CACHE] = JSONObject().apply {
                trimmed.forEach { put(it.key, it.value.toJson()) }
            }.toString()
        }
    }

    private suspend fun dropFromCache(id: String) {
        context.weatherDataStore.edit { preferences ->
            val current = readCache(preferences[CACHE]).toMutableMap()
            current.remove(id)
            preferences[CACHE] = JSONObject().apply {
                current.forEach { put(it.key, it.value.toJson()) }
            }.toString()
        }
    }

    private fun readCache(raw: String?): Map<String, WeatherSnapshot> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    json.optJSONObject(key)?.let { WeatherSnapshot.fromJson(it) }?.let { put(key, it) }
                }
            }
        } catch (e: JSONException) {
            ZuneLog.w(TAG, "unreadable weather cache, starting over", e)
            emptyMap()
        }
    }

    // --- Daily allowance --------------------------------------------------------------------------

    /** Fetches spent today. Reads as 0 once the day rolls over. */
    val fetchesToday: Flow<Int> = context.weatherDataStore.data.map { preferences ->
        if (preferences[QUOTA_DAY] == today()) preferences[QUOTA_USED] ?: 0 else 0
    }

    /** Books one fetch against today's allowance. */
    suspend fun countFetch() {
        context.weatherDataStore.edit { preferences ->
            val day = today()
            val used = if (preferences[QUOTA_DAY] == day) (preferences[QUOTA_USED] ?: 0) else 0
            preferences[QUOTA_DAY] = day
            preferences[QUOTA_USED] = used + 1
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        const val TAG = "WeatherDataStore"
        const val MAX_CACHED_PLACES = 12
    }
}
