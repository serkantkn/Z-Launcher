package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.WorldCity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.clockDataStore: DataStore<Preferences> by preferencesDataStore(name = "clock")

/**
 * What the Clock hub has to remember between one look and the next.
 *
 * The stopwatch and the timer are not stored as a number that counts down — a stored number would
 * stop counting the moment the launcher was killed and be wrong by however long it was away.
 * What is stored is the moment they end or began: from that, the reading can always be worked out
 * again, and a timer that was running while the phone was asleep is still running when it wakes.
 */
class ClockDataStore(private val context: Context) {

    // ── World clock ─────────────────────────────────────────────────────────

    /** The cities on the wall. Null has never been chosen and means "ship the defaults". */
    val cities: Flow<List<WorldCity>?> = context.clockDataStore.data.map { prefs ->
        val raw = prefs[CITIES] ?: return@map null
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { WorldCity(array.getString(it)) }
        }.getOrDefault(emptyList())
    }

    suspend fun saveCities(cities: List<WorldCity>) {
        val array = JSONArray()
        cities.forEach { array.put(it.timeZoneId) }
        context.clockDataStore.edit { it[CITIES] = array.toString() }
    }

    // ── Stopwatch ───────────────────────────────────────────────────────────

    /**
     * A stopwatch reading that survives the launcher being killed.
     *
     * [baseMillis] is the wall-clock moment the watch would have been started at to read what it
     * reads now, so the reading is always `now - baseMillis` while it runs. [elapsedMillis] holds
     * the frozen reading while it is stopped.
     */
    data class StopwatchState(
        val isRunning: Boolean = false,
        val baseMillis: Long = 0L,
        val elapsedMillis: Long = 0L,
        val laps: List<Long> = emptyList()
    ) {
        fun readingAt(now: Long): Long =
            if (isRunning) (now - baseMillis).coerceAtLeast(0L) else elapsedMillis
    }

    val stopwatch: Flow<StopwatchState> = context.clockDataStore.data.map { prefs ->
        StopwatchState(
            isRunning = prefs[SW_RUNNING] ?: false,
            baseMillis = prefs[SW_BASE] ?: 0L,
            elapsedMillis = prefs[SW_ELAPSED] ?: 0L,
            laps = parseLongs(prefs[SW_LAPS])
        )
    }

    suspend fun saveStopwatch(state: StopwatchState) {
        val array = JSONArray()
        state.laps.forEach { array.put(it) }
        context.clockDataStore.edit { prefs ->
            prefs[SW_RUNNING] = state.isRunning
            prefs[SW_BASE] = state.baseMillis
            prefs[SW_ELAPSED] = state.elapsedMillis
            prefs[SW_LAPS] = array.toString()
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────────

    /**
     * A countdown that survives the same way: [endsAtMillis] is the wall-clock moment it runs out,
     * [remainingMillis] the frozen remainder while it is paused, and [totalMillis] what it was set
     * to, which is what the progress line is measured against and what "reset" goes back to.
     */
    data class TimerState(
        val isRunning: Boolean = false,
        val endsAtMillis: Long = 0L,
        val remainingMillis: Long = DEFAULT_TIMER_MS,
        val totalMillis: Long = DEFAULT_TIMER_MS,
        val label: String = ""
    ) {
        fun remainingAt(now: Long): Long =
            if (isRunning) (endsAtMillis - now).coerceAtLeast(0L) else remainingMillis
    }

    val timer: Flow<TimerState> = context.clockDataStore.data.map { prefs ->
        TimerState(
            isRunning = prefs[TIMER_RUNNING] ?: false,
            endsAtMillis = prefs[TIMER_ENDS_AT] ?: 0L,
            remainingMillis = prefs[TIMER_REMAINING] ?: DEFAULT_TIMER_MS,
            totalMillis = prefs[TIMER_TOTAL] ?: DEFAULT_TIMER_MS,
            label = prefs[TIMER_LABEL] ?: ""
        )
    }

    suspend fun saveTimer(state: TimerState) {
        context.clockDataStore.edit { prefs ->
            prefs[TIMER_RUNNING] = state.isRunning
            prefs[TIMER_ENDS_AT] = state.endsAtMillis
            prefs[TIMER_REMAINING] = state.remainingMillis
            prefs[TIMER_TOTAL] = state.totalMillis
            prefs[TIMER_LABEL] = state.label
        }
    }

    /** The durations offered as one tap, most recently used first. */
    val timerPresets: Flow<List<Long>> = context.clockDataStore.data.map { prefs ->
        val stored = parseLongs(prefs[TIMER_PRESETS])
        if (stored.isEmpty()) DEFAULT_PRESETS else stored
    }

    /** Remembers a duration that was actually used, at the front, keeping the list short. */
    suspend fun rememberPreset(durationMillis: Long) {
        if (durationMillis <= 0L) return
        context.clockDataStore.edit { prefs ->
            val current = parseLongs(prefs[TIMER_PRESETS]).ifEmpty { DEFAULT_PRESETS }
            val updated = (listOf(durationMillis) + current.filter { it != durationMillis }).take(PRESET_COUNT)
            val array = JSONArray()
            updated.forEach { array.put(it) }
            prefs[TIMER_PRESETS] = array.toString()
        }
    }

    private fun parseLongs(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getLong(it) }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val DEFAULT_TIMER_MS = 5 * 60 * 1000L
        private const val PRESET_COUNT = 5
        private val DEFAULT_PRESETS = listOf(
            60_000L, 3 * 60_000L, 5 * 60_000L, 10 * 60_000L, 30 * 60_000L
        )

        private val CITIES = stringPreferencesKey("world_cities")
        private val SW_RUNNING = booleanPreferencesKey("stopwatch_running")
        private val SW_BASE = longPreferencesKey("stopwatch_base")
        private val SW_ELAPSED = longPreferencesKey("stopwatch_elapsed")
        private val SW_LAPS = stringPreferencesKey("stopwatch_laps")
        private val TIMER_RUNNING = booleanPreferencesKey("timer_running")
        private val TIMER_ENDS_AT = longPreferencesKey("timer_ends_at")
        private val TIMER_REMAINING = longPreferencesKey("timer_remaining")
        private val TIMER_TOTAL = longPreferencesKey("timer_total")
        private val TIMER_LABEL = stringPreferencesKey("timer_label")
        private val TIMER_PRESETS = stringPreferencesKey("timer_presets")
    }
}
