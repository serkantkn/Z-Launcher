package com.serkantkn.zunelauncher.ui.screens.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.CalendarInfo
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.DAY_MS
import com.serkantkn.zunelauncher.util.matches
import com.serkantkn.zunelauncher.util.resolveWeekStart
import com.serkantkn.zunelauncher.util.shiftMonth
import com.serkantkn.zunelauncher.util.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * The Calendar hub's state.
 *
 * Two sources, one list. The phone's own calendars are read through the provider and the
 * launcher's own events come out of its store, and everything above this point treats them the
 * same — the only place the difference matters is when something is written, because an event that
 * came out of somebody's work calendar has to go back into it.
 */
class CalendarHubViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val calendarDataStore = container.calendarDataStore
    private val calendarRepository = container.calendarRepository
    private val reminderScheduler = container.eventReminderScheduler

    // ── Where we are looking ────────────────────────────────────────────────

    private val _selectedDate = MutableStateFlow(startOfDay(System.currentTimeMillis()))
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(monthOf(System.currentTimeMillis()))
    val currentMonth: StateFlow<Long> = _currentMonth.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── The phone's calendars ───────────────────────────────────────────────

    private val _hasPermission = MutableStateFlow(calendarRepository.canRead())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _systemEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())

    val visibleCalendars: StateFlow<Set<Long>?> = calendarDataStore.visibleCalendars
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val defaultCalendarId: StateFlow<Long?> = calendarDataStore.defaultCalendarId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Settings ────────────────────────────────────────────────────────────

    val defaultReminderMinutes: StateFlow<Int?> = calendarDataStore.defaultReminderMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarEvent.DEFAULT_REMINDER_MINUTES)

    val weekStart: StateFlow<Int> = calendarDataStore.weekStart
        .map { resolveWeekStart(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolveWeekStart(0))

    val showPastEvents: StateFlow<Boolean> = calendarDataStore.showPastEvents
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Everything, from both sources ───────────────────────────────────────

    private val localEvents: StateFlow<List<CalendarEvent>> = calendarDataStore.eventsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val events: StateFlow<List<CalendarEvent>> =
        combine(localEvents, _systemEvents) { local, system ->
            (local + system).sortedBy { it.startMillis }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What the search page shows: everything the typed words match. */
    val searchResults: StateFlow<List<CalendarEvent>> =
        combine(events, _searchQuery) { all, query ->
            if (query.isBlank()) all else all.filter { it.matches(query) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        // Re-read the provider whenever the choice of calendars or the month in view changes.
        viewModelScope.launch {
            combine(visibleCalendars, _currentMonth) { visible, month -> visible to month }
                .collect { loadSystemEvents() }
        }
        // The launcher's own events carry their own reminders, which nothing else would set.
        viewModelScope.launch {
            localEvents.collect { reminderScheduler.rescheduleAll(it) }
        }
    }

    /** Asks the phone again — after the permission screen, or on coming back to the hub. */
    fun refresh() {
        _hasPermission.value = calendarRepository.canRead()
        viewModelScope.launch {
            _calendars.value = withContext(Dispatchers.IO) { calendarRepository.calendars() }
            loadSystemEvents()
        }
    }

    private suspend fun loadSystemEvents() {
        if (!calendarRepository.canRead()) {
            _systemEvents.value = emptyList()
            return
        }
        val month = _currentMonth.value
        val now = System.currentTimeMillis()
        // Wide enough that the month grid, the agenda and a year of searching all come from one
        // query: a month either side of what is on screen, and a year ahead of today.
        val from = minOf(startOfDay(now), shiftMonth(month, -1))
        val to = maxOf(now + 365 * DAY_MS, shiftMonth(month, 2))
        val visible = visibleCalendars.value
        _systemEvents.value = withContext(Dispatchers.IO) {
            calendarRepository.events(from, to, visible)
        }
    }

    // ── Moving about ────────────────────────────────────────────────────────

    fun selectDate(dayMillis: Long) {
        _selectedDate.value = startOfDay(dayMillis)
        if (monthOf(dayMillis) != _currentMonth.value) _currentMonth.value = monthOf(dayMillis)
    }

    fun showMonth(monthMillis: Long) {
        _currentMonth.value = monthOf(monthMillis)
    }

    fun nextMonth() {
        _currentMonth.value = shiftMonth(_currentMonth.value, 1)
    }

    fun previousMonth() {
        _currentMonth.value = shiftMonth(_currentMonth.value, -1)
    }

    fun goToToday() {
        val now = System.currentTimeMillis()
        _selectedDate.value = startOfDay(now)
        _currentMonth.value = monthOf(now)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ── Writing ─────────────────────────────────────────────────────────────

    /**
     * Saves an event where it belongs.
     *
     * An event that came out of a phone calendar goes back into the same one. A new event goes to
     * whichever calendar has been chosen — and to the launcher's own store when that is what was
     * chosen, or when there is no calendar it may write to.
     */
    fun saveEvent(event: CalendarEvent, targetCalendarId: Long?) {
        viewModelScope.launch {
            val destination = targetCalendarId ?: event.calendarId ?: resolveDefaultCalendar()
            val writeToSystem = destination != null &&
                destination != CalendarInfo.LOCAL_ID &&
                calendarRepository.canWrite()

            if (writeToSystem) {
                val ok = withContext(Dispatchers.IO) {
                    if (event.systemEventId != null && event.calendarId == destination) {
                        calendarRepository.update(event)
                    } else {
                        // Moved between calendars, or new: write it there and drop the old copy.
                        val inserted = calendarRepository.insert(event, destination!!)
                        if (inserted != null && event.systemEventId != null) {
                            calendarRepository.delete(event)
                        }
                        inserted != null
                    }
                }
                if (ok) {
                    // An event that used to be ours no longer is.
                    if (event.isLocal) removeLocal(event.id)
                    loadSystemEvents()
                    return@launch
                }
                // Writing failed; rather than lose it, keep it ourselves.
            }
            saveLocal(event.copy(isLocal = true, systemEventId = null, calendarId = null))
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch {
            if (!event.isLocal) {
                val ok = withContext(Dispatchers.IO) { calendarRepository.delete(event) }
                if (ok) loadSystemEvents()
                return@launch
            }
            reminderScheduler.cancel(event.id)
            removeLocal(event.id)
        }
    }

    private suspend fun saveLocal(event: CalendarEvent) {
        val stored = calendarDataStore.eventsFlow.first()
        val updated = if (stored.any { it.id == event.id }) {
            stored.map { if (it.id == event.id) event else it }
        } else {
            stored + event
        }
        calendarDataStore.saveEvents(updated)
        reminderScheduler.schedule(event)
    }

    private suspend fun removeLocal(id: String) {
        val stored = calendarDataStore.eventsFlow.first()
        calendarDataStore.saveEvents(stored.filterNot { it.id == id })
    }

    /** The calendar a new event goes to when nobody has said. */
    private fun resolveDefaultCalendar(): Long? =
        defaultCalendarId.value ?: calendarRepository.defaultCalendar()?.id

    /** The calendars a new event may be written to, with the launcher's own store first. */
    fun writableCalendars(): List<CalendarInfo> = _calendars.value.filter { it.isWritable }

    // ── Settings ────────────────────────────────────────────────────────────

    fun setCalendarVisible(id: Long, visible: Boolean) {
        viewModelScope.launch {
            val current = visibleCalendars.value ?: _calendars.value.map { it.id }.toSet()
            val updated = if (visible) current + id else current - id
            calendarDataStore.setVisibleCalendars(updated)
        }
    }

    fun setDefaultCalendar(id: Long) {
        viewModelScope.launch { calendarDataStore.setDefaultCalendarId(id) }
    }

    fun setDefaultReminder(minutes: Int?) {
        viewModelScope.launch { calendarDataStore.setDefaultReminderMinutes(minutes) }
    }

    fun setWeekStart(day: Int) {
        viewModelScope.launch { calendarDataStore.setWeekStart(day) }
    }

    fun setShowPastEvents(enabled: Boolean) {
        viewModelScope.launch { calendarDataStore.setShowPastEvents(enabled) }
    }

    private fun monthOf(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}
