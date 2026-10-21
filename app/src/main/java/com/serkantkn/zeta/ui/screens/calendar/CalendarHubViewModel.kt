package com.serkantkn.zeta.ui.screens.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.CalendarEvent
import com.serkantkn.zeta.data.model.CalendarInfo
import com.serkantkn.zeta.data.model.EventEditScope
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.util.DAY_MS
import com.serkantkn.zeta.util.expandOccurrences
import com.serkantkn.zeta.util.matches
import com.serkantkn.zeta.util.resolveWeekStart
import com.serkantkn.zeta.util.shiftMonth
import com.serkantkn.zeta.util.startOfDay
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
import java.util.UUID

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

    /** The launcher's own records: a repeating event is one record here, its series. */
    private val localEvents: StateFlow<List<CalendarEvent>> = calendarDataStore.eventsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Everything the pages show: the phone's instances and the launcher's own events, the
     * repeating ones expanded over the same window the phone's are read for.
     */
    val events: StateFlow<List<CalendarEvent>> =
        combine(localEvents, _systemEvents, _currentMonth) { local, system, month ->
            val (from, to) = visibleRange(month, System.currentTimeMillis())
            val expanded = local.flatMap { expandOccurrences(it, from, to) }
            (expanded + system).sortedBy { it.startMillis }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Something the last write had to say: a string resource, or null. */
    private val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

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
        val (from, to) = visibleRange(_currentMonth.value, System.currentTimeMillis())
        val visible = visibleCalendars.value
        _systemEvents.value = withContext(Dispatchers.IO) {
            calendarRepository.events(from, to, visible)
        }
    }

    /**
     * Wide enough that the month grid, the agenda and a year of searching all come from one
     * query: a month either side of what is on screen, and a year ahead of today.
     */
    private fun visibleRange(month: Long, now: Long): Pair<Long, Long> =
        minOf(startOfDay(now), shiftMonth(month, -1)) to maxOf(now + 365 * DAY_MS, shiftMonth(month, 2))

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
    fun saveEvent(
        event: CalendarEvent,
        targetCalendarId: Long?,
        scope: EventEditScope = EventEditScope.THIS_EVENT
    ) {
        viewModelScope.launch {
            // Where it was is where it stays unless the calendar chip was actually changed: a
            // local event is local, a phone event is in its own calendar.
            val home = if (event.isLocal) CalendarInfo.LOCAL_ID else event.calendarId
            val destination = targetCalendarId ?: home ?: resolveDefaultCalendar()
            val moved = home != null && destination != home
            val toSystem = destination != null && destination != CalendarInfo.LOCAL_ID
            val canWrite = calendarRepository.canWrite()

            when {
                // ── Staying in a phone calendar ──
                toSystem && !moved && event.systemEventId != null -> {
                    val ok = withContext(Dispatchers.IO) {
                        if (event.isOccurrence && scope == EventEditScope.THIS_EVENT) {
                            calendarRepository.updateOccurrence(event)
                        } else {
                            calendarRepository.update(event)
                        }
                    }
                    if (ok) loadSystemEvents() else _notice.value = R.string.cal_save_failed
                }

                // ── Going into a phone calendar: new, or moved from the launcher or another calendar ──
                toSystem && canWrite -> {
                    val inserted = withContext(Dispatchers.IO) {
                        calendarRepository.insert(seriesOrOne(event, scope), destination!!)
                    }
                    if (inserted == null) {
                        // The phone would not take it: whatever it was before, it still is.
                        // A brand-new event is not lost either — it is kept by the launcher.
                        if (event.isLocal && event.systemEventId == null && !event.isOccurrence) {
                            saveLocal(event.copy(isLocal = true, systemEventId = null, calendarId = null))
                        }
                        _notice.value = R.string.cal_save_failed
                        return@launch
                    }
                    // Only now, with the new copy safely written, does the old one go.
                    withContext(Dispatchers.IO) { dropOldCopy(event, scope) }
                    loadSystemEvents()
                }

                // ── Kept by the launcher ──
                else -> {
                    if (toSystem && !canWrite) _notice.value = R.string.cal_save_failed
                    val ok = saveLocalScoped(event.copy(isLocal = true, systemEventId = null, calendarId = null), scope)
                    // A phone event moved into the launcher: its phone copy goes once ours is written.
                    if (ok && event.systemEventId != null && !event.isLocal) {
                        withContext(Dispatchers.IO) { dropOldCopy(event, scope) }
                        loadSystemEvents()
                    }
                }
            }
        }
    }

    fun deleteEvent(event: CalendarEvent, scope: EventEditScope = EventEditScope.THIS_EVENT) {
        viewModelScope.launch {
            if (!event.isLocal) {
                val ok = withContext(Dispatchers.IO) {
                    if (event.isOccurrence && scope == EventEditScope.THIS_EVENT) {
                        calendarRepository.deleteOccurrence(event)
                    } else {
                        calendarRepository.delete(event)
                    }
                }
                if (ok) loadSystemEvents()
                return@launch
            }
            val masterId = event.seriesId ?: event.id
            if (event.isOccurrence && scope == EventEditScope.THIS_EVENT) {
                excludeOccurrence(masterId, event.instanceStartMillis ?: event.startMillis)
            } else {
                reminderScheduler.cancel(masterId)
                removeLocal(masterId)
            }
        }
    }

    /** What goes into a phone calendar: one week of a series, or the series with the draft's changes. */
    private fun seriesOrOne(event: CalendarEvent, scope: EventEditScope): CalendarEvent =
        if (event.isOccurrence && scope == EventEditScope.THIS_EVENT) {
            event.copy(recurrence = null, seriesId = null, instanceStartMillis = null)
        } else {
            event
        }

    /** After a successful move: the copy where the event used to live. */
    private suspend fun dropOldCopy(event: CalendarEvent, scope: EventEditScope) {
        if (event.isLocal) {
            val masterId = event.seriesId ?: event.id
            if (event.isOccurrence && scope == EventEditScope.THIS_EVENT) {
                excludeOccurrence(masterId, event.instanceStartMillis ?: event.startMillis)
            } else {
                reminderScheduler.cancel(masterId)
                removeLocal(masterId)
            }
        } else if (event.systemEventId != null) {
            if (event.isOccurrence && scope == EventEditScope.THIS_EVENT) {
                calendarRepository.deleteOccurrence(event)
            } else {
                calendarRepository.delete(event)
            }
        }
    }

    /**
     * Writes to the launcher's own store, honouring the scope: one occurrence of a series becomes
     * a standalone event and a gap in the series; the whole series takes the draft's changes on
     * the series' own date.
     */
    private suspend fun saveLocalScoped(event: CalendarEvent, scope: EventEditScope): Boolean {
        if (!event.isOccurrence) {
            saveLocal(event)
            return true
        }
        val masterId = event.seriesId ?: return false
        val stored = calendarDataStore.eventsFlow.first()
        val master = stored.firstOrNull { it.id == masterId }
        if (master == null) {
            // The series is not ours (it came from the phone): keep this one as a plain event.
            saveLocal(event.copy(id = UUID.randomUUID().toString(), recurrence = null, seriesId = null, instanceStartMillis = null))
            return true
        }
        if (scope == EventEditScope.THIS_EVENT) {
            val original = event.instanceStartMillis ?: event.startMillis
            val standalone = event.copy(
                id = UUID.randomUUID().toString(),
                recurrence = null,
                seriesId = null,
                instanceStartMillis = null,
                excludedStarts = emptySet()
            )
            val updatedMaster = master.copy(excludedStarts = master.excludedStarts + original)
            calendarDataStore.saveEvents(
                stored.map { if (it.id == masterId) updatedMaster else it } + standalone
            )
            reminderScheduler.schedule(updatedMaster)
            reminderScheduler.schedule(standalone)
        } else {
            // The series keeps its own first date; the time of day and everything else is new.
            val draftTime = Calendar.getInstance().apply { timeInMillis = event.startMillis }
            val start = if (event.isAllDay) {
                startOfDay(master.startMillis)
            } else {
                Calendar.getInstance().apply {
                    timeInMillis = master.startMillis
                    set(Calendar.HOUR_OF_DAY, draftTime.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, draftTime.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            val updatedMaster = event.copy(
                id = masterId,
                startMillis = start,
                endMillis = start + event.durationMillis,
                seriesId = null,
                instanceStartMillis = null,
                excludedStarts = master.excludedStarts
            )
            calendarDataStore.saveEvents(stored.map { if (it.id == masterId) updatedMaster else it })
            reminderScheduler.schedule(updatedMaster)
        }
        return true
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

    /** Leaves a gap in one of our own series where one occurrence was taken out. */
    private suspend fun excludeOccurrence(masterId: String, originalStart: Long) {
        val stored = calendarDataStore.eventsFlow.first()
        val master = stored.firstOrNull { it.id == masterId } ?: return
        val updated = master.copy(excludedStarts = master.excludedStarts + originalStart)
        calendarDataStore.saveEvents(stored.map { if (it.id == masterId) updated else it })
        reminderScheduler.schedule(updated)
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
