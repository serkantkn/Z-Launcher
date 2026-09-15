package com.serkantkn.zunelauncher.ui.screens.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.ClockDataStore
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.model.WorldCity
import com.serkantkn.zunelauncher.data.service.TimerNotifier
import com.serkantkn.zunelauncher.data.service.TimerReceiver
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Clock hub's state.
 *
 * Nothing here counts. The stopwatch and the timer are each a moment written down — when the watch
 * would have started, when the countdown runs out — so the reading is worked out from the current
 * time whenever anybody looks. That is what makes them survive the launcher being killed, and it
 * is why the timer's end is booked with the system rather than being a loop in here that stops the
 * moment this object does.
 */
class ClockHubViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val alarmDataStore = container.alarmDataStore
    private val alarmScheduler = container.alarmScheduler
    private val clockDataStore = container.clockDataStore
    private val timerScheduler = container.timerScheduler
    private val settingsDataStore = container.settingsDataStore

    // ── Alarms ──────────────────────────────────────────────────────────────

    val alarms: StateFlow<List<Alarm>> = alarmDataStore.alarmsFlow
        // Note reminders belong to the note that made them, not to the alarm list.
        .map { list -> list.filter { it.noteId == null }.sortedWith(compareBy({ it.hour }, { it.minute })) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether the phone will let the launcher name an exact minute for an alarm. */
    private val _canScheduleExact = MutableStateFlow(true)
    val canScheduleExact: StateFlow<Boolean> = _canScheduleExact.asStateFlow()

    fun checkExactAlarmPermission() {
        _canScheduleExact.value = alarmScheduler.canScheduleExact()
    }

    /** The defaults a newly created alarm starts from, as set in Settings. */
    val defaultSnoozeMinutes: StateFlow<Int> = settingsDataStore.alarmSnoozeMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, Alarm.DEFAULT_SNOOZE_MINUTES)

    val defaultAutoSilenceMinutes: StateFlow<Int> = settingsDataStore.alarmAutoSilenceMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, Alarm.DEFAULT_AUTO_SILENCE_MINUTES)

    val showSeconds: StateFlow<Boolean> = settingsDataStore.clockShowSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun saveAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val stored = alarmDataStore.alarmsFlow.first()
            val updated = if (stored.any { it.id == alarm.id }) {
                stored.map { if (it.id == alarm.id) alarm else it }
            } else {
                stored + alarm
            }
            alarmDataStore.saveAlarms(updated)
            alarmScheduler.schedule(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        // Turning one back on clears a snooze that was left over from the last time it rang.
        saveAlarm(alarm.copy(isEnabled = !alarm.isEnabled, snoozedUntilMillis = null))
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val stored = alarmDataStore.alarmsFlow.first()
            alarmDataStore.saveAlarms(stored.filterNot { it.id == alarm.id })
            alarmScheduler.cancel(alarm)
        }
    }

    /** Drops a pending snooze and puts the alarm back on its own schedule. */
    fun cancelSnooze(alarm: Alarm) {
        saveAlarm(alarm.copy(snoozedUntilMillis = null))
    }

    fun duplicateAlarm(alarm: Alarm) {
        saveAlarm(alarm.copy(id = java.util.UUID.randomUUID().toString(), snoozedUntilMillis = null))
    }

    // ── World clock ─────────────────────────────────────────────────────────

    val cities: StateFlow<List<WorldCity>> = clockDataStore.cities
        .map { it ?: WorldCity.DEFAULTS }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorldCity.DEFAULTS)

    fun addCity(city: WorldCity) {
        viewModelScope.launch {
            val current = cities.value
            if (current.none { it.timeZoneId == city.timeZoneId }) {
                clockDataStore.saveCities(current + city)
            }
        }
    }

    fun removeCity(city: WorldCity) {
        viewModelScope.launch {
            clockDataStore.saveCities(cities.value.filterNot { it.timeZoneId == city.timeZoneId })
        }
    }

    /** Moves a city one place up or down the wall. */
    fun moveCity(city: WorldCity, by: Int) {
        viewModelScope.launch {
            val current = cities.value.toMutableList()
            val from = current.indexOfFirst { it.timeZoneId == city.timeZoneId }
            val to = from + by
            if (from < 0 || to !in current.indices) return@launch
            current.add(to, current.removeAt(from))
            clockDataStore.saveCities(current)
        }
    }

    // ── Stopwatch ───────────────────────────────────────────────────────────

    val stopwatch: StateFlow<ClockDataStore.StopwatchState> = clockDataStore.stopwatch
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClockDataStore.StopwatchState())

    fun toggleStopwatch() {
        val state = stopwatch.value
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            clockDataStore.saveStopwatch(
                if (state.isRunning) {
                    state.copy(isRunning = false, elapsedMillis = state.readingAt(now))
                } else {
                    state.copy(isRunning = true, baseMillis = now - state.elapsedMillis)
                }
            )
        }
    }

    fun resetStopwatch() {
        viewModelScope.launch {
            clockDataStore.saveStopwatch(ClockDataStore.StopwatchState())
        }
    }

    fun addLap() {
        val state = stopwatch.value
        val reading = state.readingAt(System.currentTimeMillis())
        if (reading <= 0L) return
        viewModelScope.launch {
            clockDataStore.saveStopwatch(state.copy(laps = listOf(reading) + state.laps))
        }
    }

    fun clearLaps() {
        viewModelScope.launch {
            clockDataStore.saveStopwatch(stopwatch.value.copy(laps = emptyList()))
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────────

    val timer: StateFlow<ClockDataStore.TimerState> = clockDataStore.timer
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClockDataStore.TimerState())

    val timerPresets: StateFlow<List<Long>> = clockDataStore.timerPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Sets what the dial reads, without starting it. */
    fun setTimerDuration(durationMillis: Long, label: String = timer.value.label) {
        if (durationMillis <= 0L) return
        viewModelScope.launch {
            timerScheduler.cancel()
            TimerNotifier.clear(getApplication())
            clockDataStore.saveTimer(
                ClockDataStore.TimerState(
                    isRunning = false,
                    endsAtMillis = 0L,
                    remainingMillis = durationMillis,
                    totalMillis = durationMillis,
                    label = label
                )
            )
        }
    }

    fun toggleTimer() {
        val state = timer.value
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (state.isRunning) {
                val remaining = state.remainingAt(System.currentTimeMillis())
                timerScheduler.cancel()
                TimerNotifier.clear(context)
                clockDataStore.saveTimer(
                    state.copy(isRunning = false, endsAtMillis = 0L, remainingMillis = remaining)
                )
            } else {
                // Restarting a finished countdown starts it over rather than ending immediately.
                val duration = state.remainingMillis.takeIf { it > 0L } ?: state.totalMillis
                if (duration <= 0L) return@launch
                val endsAt = System.currentTimeMillis() + duration
                timerScheduler.schedule(endsAt)
                TimerNotifier.showRunning(context, endsAt, state.label)
                clockDataStore.saveTimer(
                    state.copy(isRunning = true, endsAtMillis = endsAt, remainingMillis = duration)
                )
                clockDataStore.rememberPreset(state.totalMillis)
            }
        }
    }

    fun resetTimer() {
        val state = timer.value
        viewModelScope.launch {
            timerScheduler.cancel()
            TimerNotifier.clear(getApplication())
            clockDataStore.saveTimer(
                state.copy(isRunning = false, endsAtMillis = 0L, remainingMillis = state.totalMillis)
            )
        }
    }

    /** Stretches a running countdown by a minute without stopping it. */
    fun addTimerMinute() {
        val state = timer.value
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (state.isRunning) {
                val endsAt = state.endsAtMillis + TimerReceiver.ONE_MINUTE_MS
                timerScheduler.schedule(endsAt)
                TimerNotifier.showRunning(context, endsAt, state.label)
                clockDataStore.saveTimer(
                    state.copy(
                        endsAtMillis = endsAt,
                        totalMillis = state.totalMillis + TimerReceiver.ONE_MINUTE_MS
                    )
                )
            } else {
                setTimerDuration(state.remainingMillis + TimerReceiver.ONE_MINUTE_MS)
            }
        }
    }

    fun setTimerLabel(label: String) {
        viewModelScope.launch {
            clockDataStore.saveTimer(timer.value.copy(label = label))
        }
    }
}
