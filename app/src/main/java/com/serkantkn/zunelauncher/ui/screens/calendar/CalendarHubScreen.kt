package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.CalendarInfo
import com.serkantkn.zunelauncher.data.repository.SettingsBridge
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.MetroNotice
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet
import com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.DAY_MS
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

    BackHandler(enabled = editingEvent != null || menuTarget != null) {
        when {
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
            ZuneHubEntranceLayout { bottomBarModifier ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ZuneWideHubTitle(text = stringResource(R.string.hub_calendar))
                            ZuneWidePanorama(
                                tabs = tabs,
                                fillPageHeight = true,
                                modifier = Modifier.weight(1f)
                            ) { index -> Page(index) }
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.hub_calendar),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = if (zuneColors.isDark) {
                                    Color.White.copy(alpha = 0.9f)
                                } else {
                                    Color.Black.copy(alpha = 0.85f)
                                },
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(
                                    top = 48.dp,
                                    bottom = 4.dp,
                                    start = ZuneDimens.ScreenPaddingHorizontal
                                )
                            )

                            ZunePivotTabs(tabs = tabs, state = pager)

                            ZuneLoopingPager(
                                state = pager,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) { page ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                ) {
                                    Page(page)
                                }
                            }

                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }

                    WindowsPhoneBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                        actions = barActions,
                        menuItems = barMenu
                    )
                }
            }
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
                        selectedCalendarId = editing.calendarId
                            ?: defaultCalendarId
                            ?: calendars.firstOrNull { it.isWritable && it.isPrimary }?.id
                            ?: calendars.firstOrNull { it.isWritable }?.id
                            ?: CalendarInfo.LOCAL_ID,
                        weekStart = weekStart,
                        onSave = { saved, calendarId ->
                            viewModel.saveEvent(saved, calendarId)
                            closeEditor()
                        },
                        onDelete = {
                            viewModel.deleteEvent(it)
                            closeEditor()
                        },
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
                        viewModel.deleteEvent(event)
                        menuTarget = null
                    }
                }
            }
        }
    }
}

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
