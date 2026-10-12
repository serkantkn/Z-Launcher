package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.CalendarInfo
import com.serkantkn.zunelauncher.data.model.EventEditScope
import com.serkantkn.zunelauncher.data.repository.SettingsBridge
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.MetroNotice
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHub
import com.serkantkn.zunelauncher.ui.components.ZuneHubBar
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet
import com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.startOfDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

/**
 * The Calendar hub.
 *
 * It reads the phone's own calendars now, so what it shows is what is actually in the diary rather
 * than a private list nothing else knew about. The launcher keeps a store of its own alongside
 * them for the events that are its business — a note's reminder — and for a phone where the
 * calendar permission has been refused.
 *
 * Five pages: what is coming, the month, the week at a glance, one day hour by hour, and a search.
 */
@Composable
fun CalendarHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()

    val events by viewModel.events.collectAsState()
    val calendars by viewModel.calendars.collectAsState()
    val visibleCalendars by viewModel.visibleCalendars.collectAsState()
    val selectedDay by viewModel.selectedDate.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val showPast by viewModel.showPastEvents.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val defaultReminder by viewModel.defaultReminderMinutes.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val tabs = listOf(
        stringResource(R.string.cal_tab_agenda),
        stringResource(R.string.cal_tab_month),
        stringResource(R.string.cal_tab_week),
        stringResource(R.string.cal_tab_day),
        stringResource(R.string.cal_tab_search)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentTab = pager.currentPage

    // The one thing that ticks: "today" and the now line have to stay true past midnight.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // ── Sub-screens ────────────────────────────────────────────────────────
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var isNewEvent by remember { mutableStateOf(false) }
    val hingeSpec = rememberHingeSpec()
    val editorHinge = remember { Animatable(0f) }
    var menuTarget by remember { mutableStateOf<CalendarEvent?>(null) }
    // A change to one week of a repeating event: this week, or every week?
    var scopeQuestion by remember { mutableStateOf<ScopeQuestion?>(null) }
    val notice by viewModel.notice.collectAsState()

    fun openEditor(event: CalendarEvent, isNew: Boolean) {
        editingEvent = event
        isNewEvent = isNew
        coroutineScope.launch {
            editorHinge.animateTo(1f, hingeSpec)
        }
    }

    fun closeEditor() {
        coroutineScope.launch {
            editorHinge.animateTo(0f, hingeSpec)
            editingEvent = null
        }
    }

    fun newEvent(atMillis: Long = defaultStart(selectedDay, now)) {
        openEditor(
            CalendarEvent(
                title = "",
                startMillis = atMillis,
                endMillis = atMillis + CalendarEvent.DEFAULT_DURATION_MS,
                reminderMinutes = defaultReminder
            ),
            isNew = true
        )
    }

    /** Saves straight away, or asks first when the event is one week of a series. */
    fun saveOrAsk(saved: CalendarEvent, calendarId: Long) {
        if (saved.isOccurrence && saved.isRepeating) {
            scopeQuestion = ScopeQuestion(saved, calendarId, isDelete = false)
        } else {
            viewModel.saveEvent(saved, calendarId)
            closeEditor()
        }
    }

    fun deleteOrAsk(event: CalendarEvent, fromEditor: Boolean) {
        if (event.isOccurrence && event.isRepeating) {
            scopeQuestion = ScopeQuestion(event, null, isDelete = true, closeEditorAfter = fromEditor)
        } else {
            viewModel.deleteEvent(event)
            if (fromEditor) closeEditor()
        }
    }

    fun answerScope(question: ScopeQuestion, scope: EventEditScope) {
        scopeQuestion = null
        if (question.isDelete) {
            viewModel.deleteEvent(question.event, scope)
        } else {
            viewModel.saveEvent(question.event, question.calendarId, scope)
        }
        if (question.closeEditorAfter) closeEditor()
    }

    BackHandler(enabled = editingEvent != null || menuTarget != null || scopeQuestion != null) {
        when {
            scopeQuestion != null -> scopeQuestion = null
            menuTarget != null -> menuTarget = null
            else -> closeEditor()
        }
    }

    val listBottomPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
    val pageContentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding)

    @Composable
    fun Page(index: Int) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!hasPermission) {
                MetroNotice(
                    message = stringResource(R.string.cal_permission_message),
                    actionLabel = stringResource(R.string.cal_permission_allow),
                    onAction = {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            notice?.let { res ->
                MetroNotice(
                    message = stringResource(res),
                    actionLabel = stringResource(R.string.common_ok),
                    onAction = { viewModel.clearNotice() },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            when (index) {
                PAGE_AGENDA -> AgendaPage(
                    events = events,
                    now = now,
                    showPast = showPast,
                    onOpen = { openEditor(it, isNew = false) },
                    onLongPress = { menuTarget = it },
                    onAdd = { newEvent() },
                    contentPadding = pageContentPadding
                )

                PAGE_MONTH -> MonthPage(
                    monthMillis = currentMonth,
                    selectedDayMillis = selectedDay,
                    events = events,
                    weekStart = weekStart,
                    now = now,
                    onSelectDay = { viewModel.selectDate(it) },
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onOpen = { openEditor(it, isNew = false) },
                    onLongPress = { menuTarget = it },
                    contentPadding = pageContentPadding
                )

                PAGE_WEEK -> WeekPage(
                    selectedDayMillis = selectedDay,
                    events = events,
                    weekStart = weekStart,
                    now = now,
                    onSelectDay = { viewModel.selectDate(it) },
                    onOpen = { openEditor(it, isNew = false) },
                    onLongPress = { menuTarget = it },
                    contentPadding = pageContentPadding
                )

                PAGE_DAY -> DayPage(
                    dayMillis = selectedDay,
                    events = events,
                    now = now,
                    onOpen = { openEditor(it, isNew = false) },
                    onLongPress = { menuTarget = it },
                    onAddAt = { newEvent(it) },
                    contentPadding = pageContentPadding
                )

                PAGE_SEARCH -> CalendarSearchPage(
                    query = searchQuery,
                    results = searchResults,
                    now = now,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onOpen = { openEditor(it, isNew = false) },
                    onLongPress = { menuTarget = it },
                    contentPadding = pageContentPadding
                )
            }
        }
    }

    val barActions = buildList {
        add(WpBarAction(Icons.Default.Add, stringResource(R.string.cal_add_event)) { newEvent() })
        add(WpBarAction(Icons.Default.DateRange, stringResource(R.string.common_today)) { viewModel.goToToday() })
        if (currentTab != PAGE_SEARCH) {
            add(
                WpBarAction(Icons.Default.Search, stringResource(R.string.cal_tab_search)) {
                    coroutineScope.launch { pager.animateScrollToPage(PAGE_SEARCH) }
                }
            )
        }
    }

    val barMenu = listOf(
        WpBarMenuItem(stringResource(R.string.settings_calendar_settings)) {
            SettingsBridge.open(SETTINGS_TAB_HUBS)
        }
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = editorHinge.value
                    rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                    alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            ZuneHub(
                title = stringResource(R.string.hub_calendar),
                tabs = tabs,
                pager = pager,
                bar = ZuneHubBar(barActions, barMenu)
            ) { index -> Page(index) }
        }

        // The editor, on the hinge
        val editing = editingEvent
        if (editing != null || editorHinge.value > 0f) {
            if (editing != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = editorHinge.value
                            rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                            alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                        }
                ) {
                    EventEditScreen(
                        event = editing,
                        isNew = isNewEvent,
                        calendars = calendars.filter { it.isWritable },
                        // An event stays where it lives unless the chip is changed: the
                        // launcher's own events open on "local", a phone event on its calendar.
                        selectedCalendarId = when {
                            !isNewEvent && editing.isLocal -> CalendarInfo.LOCAL_ID
                            else -> editing.calendarId
                                ?: defaultCalendarId
                                ?: calendars.firstOrNull { it.isWritable && it.isPrimary }?.id
                                ?: calendars.firstOrNull { it.isWritable }?.id
                                ?: CalendarInfo.LOCAL_ID
                        },
                        weekStart = weekStart,
                        onSave = { saved, calendarId -> saveOrAsk(saved, calendarId) },
                        onDelete = { deleteOrAsk(it, fromEditor = true) },
                        onClose = { closeEditor() }
                    )
                }
            }
        }

        // Long press on one event
        menuTarget?.let { event ->
            MessagingSheet(
                title = event.title.ifBlank { stringResource(R.string.cal_untitled_event) },
                onDismiss = { menuTarget = null }
            ) {
                SheetAction(label = stringResource(R.string.cal_edit_event)) {
                    val target = event
                    menuTarget = null
                    openEditor(target, isNew = false)
                }
                if (event.linkedNoteId != null) {
                    SheetAction(label = stringResource(R.string.cal_open_note)) {
                        com.serkantkn.zunelauncher.data.repository.NotesBridge.open(event.linkedNoteId)
                        menuTarget = null
                    }
                }
                if (event.isEditable) {
                    SheetAction(
                        label = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        menuTarget = null
                        deleteOrAsk(event, fromEditor = false)
                    }
                }
            }
        }

        // One week of a series, or all of them
        scopeQuestion?.let { question ->
            ZuneFlipDialog(
                onDismissRequest = { scopeQuestion = null },
                title = stringResource(R.string.cal_scope_title),
                confirmButton = {
                    ZuneDialogButton(
                        text = stringResource(R.string.cal_scope_this_event),
                        onClick = { dismissWithAnim { answerScope(question, EventEditScope.THIS_EVENT) } }
                    )
                },
                dismissButton = {
                    ZuneDialogButton(
                        text = stringResource(R.string.cal_scope_whole_series),
                        onClick = { dismissWithAnim { answerScope(question, EventEditScope.WHOLE_SERIES) } }
                    )
                }
            ) {
                Text(
                    text = stringResource(
                        if (question.isDelete) R.string.cal_scope_delete_message else R.string.cal_scope_edit_message
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

/** A pending "this event or the whole series" answer, with what it was asked for. */
private data class ScopeQuestion(
    val event: CalendarEvent,
    val calendarId: Long?,
    val isDelete: Boolean,
    val closeEditorAfter: Boolean = true
)

/**
 * Where a new event starts: the next whole hour on the day being looked at, or on today the next
 * whole hour from now — nobody adds an event that began this morning.
 */
private fun defaultStart(selectedDayMillis: Long, now: Long): Long {
    val base = if (startOfDay(now) == selectedDayMillis) now else selectedDayMillis + 9 * 60 * 60 * 1000L
    return Calendar.getInstance().apply {
        timeInMillis = base
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private const val PAGE_AGENDA = 0
private const val PAGE_MONTH = 1
private const val PAGE_WEEK = 2
private const val PAGE_DAY = 3
private const val PAGE_SEARCH = 4

/** The settings tab the hub's own settings live on. */
private const val SETTINGS_TAB_HUBS = "HUBS"
