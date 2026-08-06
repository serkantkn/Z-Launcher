package com.serkantkn.zunelauncher.ui.screens.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.CalendarDataStore
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarHubViewModel(application: Application) : AndroidViewModel(application) {

    private val calendarDataStore = CalendarDataStore(application)

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _selectedDate = MutableStateFlow<Calendar>(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow<Calendar>(Calendar.getInstance())
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()

    init {
        calendarDataStore.eventsFlow.onEach {
            _events.value = it
        }.launchIn(viewModelScope)
    }

    fun addEvent(event: CalendarEvent) {
        val newList = _events.value + event
        viewModelScope.launch {
            calendarDataStore.saveEvents(newList)
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        val newList = _events.value.filterNot { it.id == event.id }
        viewModelScope.launch {
            calendarDataStore.saveEvents(newList)
        }
    }

    fun selectDate(date: Calendar) {
        _selectedDate.value = date
    }

    fun nextMonth() {
        val cal = _currentMonth.value.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        _currentMonth.value = cal
    }

    fun previousMonth() {
        val cal = _currentMonth.value.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        _currentMonth.value = cal
    }

    fun goToToday() {
        val today = Calendar.getInstance()
        _selectedDate.value = today
        _currentMonth.value = today
    }
}
