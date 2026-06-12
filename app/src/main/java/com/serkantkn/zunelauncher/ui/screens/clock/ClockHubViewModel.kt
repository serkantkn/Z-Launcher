package com.serkantkn.zunelauncher.ui.screens.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.AlarmDataStore
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.service.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ClockHubViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmDataStore = AlarmDataStore(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    init {
        alarmDataStore.alarmsFlow.onEach {
            _alarms.value = it
        }.launchIn(viewModelScope)
    }

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
                alarmScheduler.schedule(alarm) // Update scheduling
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
}
