package com.serkantkn.zunelauncher.ui.screens.clock

import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.Alarm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.TimeZone

data class WorldCity(
    val id: String = java.util.UUID.randomUUID().toString(),
    val cityName: String,
    val countryName: String,
    val timeZoneId: String
)

class ClockHubViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmDataStore = application.appContainer.alarmDataStore
    private val alarmScheduler = application.appContainer.alarmScheduler

    // --- ALARMS ---
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    // --- WORLD CLOCK ---
    private val _worldCities = MutableStateFlow<List<WorldCity>>(
        listOf(
            WorldCity(cityName = "İstanbul", countryName = "Türkiye", timeZoneId = "Europe/Istanbul"),
            WorldCity(cityName = "Londra", countryName = "Birleşik Krallık", timeZoneId = "Europe/London"),
            WorldCity(cityName = "New York", countryName = "ABD", timeZoneId = "America/New_York"),
            WorldCity(cityName = "Tokyo", countryName = "Japonya", timeZoneId = "Asia/Tokyo"),
            WorldCity(cityName = "Paris", countryName = "Fransa", timeZoneId = "Europe/Paris"),
            WorldCity(cityName = "Sidney", countryName = "Avustralya", timeZoneId = "Australia/Sydney")
        )
    )
    val worldCities: StateFlow<List<WorldCity>> = _worldCities.asStateFlow()

    // --- STOPWATCH ---
    private val _stopwatchTimeMs = MutableStateFlow(0L)
    val stopwatchTimeMs: StateFlow<Long> = _stopwatchTimeMs.asStateFlow()

    private val _isStopwatchRunning = MutableStateFlow(false)
    val isStopwatchRunning: StateFlow<Boolean> = _isStopwatchRunning.asStateFlow()

    private val _stopwatchLaps = MutableStateFlow<List<Long>>(emptyList())
    val stopwatchLaps: StateFlow<List<Long>> = _stopwatchLaps.asStateFlow()

    private var stopwatchJob: Job? = null

    // --- TIMER ---
    private val _timerTotalMs = MutableStateFlow(5 * 60 * 1000L) // Default 5 minutes
    val timerTotalMs: StateFlow<Long> = _timerTotalMs.asStateFlow()

    private val _timerRemainingMs = MutableStateFlow(5 * 60 * 1000L)
    val timerRemainingMs: StateFlow<Long> = _timerRemainingMs.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null

    init {
        alarmDataStore.alarmsFlow.onEach {
            _alarms.value = it
        }.launchIn(viewModelScope)
    }

    // --- ALARM ACTIONS ---
    fun addAlarm(alarm: Alarm) {
        val currentList = _alarms.value.toMutableList()
        currentList.add(alarm)
        viewModelScope.launch {
            alarmDataStore.saveAlarms(currentList)
            alarmScheduler.schedule(alarm)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        val currentList = _alarms.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            currentList[index] = alarm
            viewModelScope.launch {
                alarmDataStore.saveAlarms(currentList)
                alarmScheduler.schedule(alarm)
            }
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        val updatedAlarm = alarm.copy(isEnabled = !alarm.isEnabled)
        updateAlarm(updatedAlarm)
    }

    fun deleteAlarm(alarm: Alarm) {
        val currentList = _alarms.value.toMutableList()
        currentList.removeIf { it.id == alarm.id }
        viewModelScope.launch {
            alarmDataStore.saveAlarms(currentList)
            alarmScheduler.cancel(alarm)
        }
    }

    // --- WORLD CLOCK ACTIONS ---
    fun addWorldCity(city: WorldCity) {
        if (_worldCities.value.none { it.cityName == city.cityName }) {
            _worldCities.value = _worldCities.value + city
        }
    }

    fun removeWorldCity(city: WorldCity) {
        _worldCities.value = _worldCities.value.filterNot { it.id == city.id }
    }

    // --- STOPWATCH ACTIONS ---
    fun toggleStopwatch() {
        if (_isStopwatchRunning.value) {
            pauseStopwatch()
        } else {
            startStopwatch()
        }
    }

    private fun startStopwatch() {
        _isStopwatchRunning.value = true
        stopwatchJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - _stopwatchTimeMs.value
            while (_isStopwatchRunning.value) {
                _stopwatchTimeMs.value = System.currentTimeMillis() - startTime
                delay(10)
            }
        }
    }

    fun pauseStopwatch() {
        _isStopwatchRunning.value = false
        stopwatchJob?.cancel()
    }

    fun resetStopwatch() {
        pauseStopwatch()
        _stopwatchTimeMs.value = 0L
        _stopwatchLaps.value = emptyList()
    }

    fun addStopwatchLap() {
        if (_stopwatchTimeMs.value > 0L) {
            _stopwatchLaps.value = listOf(_stopwatchTimeMs.value) + _stopwatchLaps.value
        }
    }

    // --- TIMER ACTIONS ---
    fun setTimerDuration(hours: Int, minutes: Int, seconds: Int) {
        val totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000L
        _timerTotalMs.value = totalMs
        _timerRemainingMs.value = totalMs
        pauseTimer()
    }

    fun toggleTimer() {
        if (_isTimerRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (_timerRemainingMs.value <= 0L) {
            _timerRemainingMs.value = _timerTotalMs.value
        }
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + _timerRemainingMs.value
            while (_isTimerRunning.value && _timerRemainingMs.value > 0L) {
                val rem = endTime - System.currentTimeMillis()
                if (rem <= 0L) {
                    _timerRemainingMs.value = 0L
                    _isTimerRunning.value = false
                } else {
                    _timerRemainingMs.value = rem
                    delay(50)
                }
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        _timerRemainingMs.value = _timerTotalMs.value
    }
}
