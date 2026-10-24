package com.serkantkn.zeta.ui.screens.clock

import com.serkantkn.zeta.ui.animation.pageHingeAway
import com.serkantkn.zeta.ui.animation.pageHingeIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.data.model.WorldCity
import com.serkantkn.zeta.data.repository.ClockBridge
import com.serkantkn.zeta.data.repository.SettingsBridge
import com.serkantkn.zeta.ui.animation.HingeAnimation
import com.serkantkn.zeta.ui.components.WpBarAction
import com.serkantkn.zeta.ui.components.WpBarMenuItem
import com.serkantkn.zeta.ui.components.ZuneHub
import com.serkantkn.zeta.ui.components.ZuneHubBar
import com.serkantkn.zeta.ui.components.rememberLoopingPagerState
import com.serkantkn.zeta.ui.screens.messaging.MessagingSheet
import com.serkantkn.zeta.ui.screens.messaging.SheetAction
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.serkantkn.zeta.ui.animation.rememberHingeSpec

/**
 * The Clock hub.
 *
 * Four pages, each about one enormous number. Nothing on any of them counts by itself: the
 * stopwatch and the timer are moments written down, and this screen simply keeps asking what time
 * it is — fast while a stopwatch is running, once a second the rest of the time, and not at all
 * while the hub is away.
 *
 * Everything that needs more than a tap — setting an alarm, choosing a city, dialling a duration —
 * opens as a page on the hinge rather than as a dialog, which is what Windows Phone did and what
 * the rest of the launcher already does.
 */
@Composable
fun ClockHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClockHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()

    val alarms by viewModel.alarms.collectAsState()
    val cities by viewModel.cities.collectAsState()
    val stopwatch by viewModel.stopwatch.collectAsState()
    val timer by viewModel.timer.collectAsState()
    val presets by viewModel.timerPresets.collectAsState()
    val canScheduleExact by viewModel.canScheduleExact.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val defaultSnooze by viewModel.defaultSnoozeMinutes.collectAsState()
    val defaultAutoSilence by viewModel.defaultAutoSilenceMinutes.collectAsState()

    val tabs = listOf(
        stringResource(R.string.clock_tab_world),
        stringResource(R.string.clock_tab_alarms),
        stringResource(R.string.clock_tab_stopwatch),
        stringResource(R.string.clock_tab_timer)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentTab = pager.currentPage

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkExactAlarmPermission()
    }

    // ── The only things on this screen that tick ────────────────────────────
    // Two clocks, and neither is read up here: a state read at this level would recompose the
    // whole hub — pivot, bar, every page — thirty times a second while a stopwatch runs. Each
    // page reads the clock it needs inside its own scope, so the fast tick reaches the stopwatch
    // figure and nothing else.
    val slowNow = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            slowNow.longValue = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val fastNow = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val fastRealtime = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val fastTick = when {
        stopwatch.isRunning -> 33L
        timer.isRunning -> 250L
        else -> 0L
    }
    LaunchedEffect(fastTick) {
        do {
            fastNow.longValue = System.currentTimeMillis()
            fastRealtime.longValue = SystemClock.elapsedRealtime()
            if (fastTick > 0L) delay(fastTick)
        } while (fastTick > 0L)
    }

    // ── Sub-screens ────────────────────────────────────────────────────────
    var editingAlarm by remember { mutableStateOf<Alarm?>(null) }
    var isNewAlarm by remember { mutableStateOf(false) }
    var isCityPickerOpen by remember { mutableStateOf(false) }
    var isTimerSetupOpen by remember { mutableStateOf(false) }
    val hingeSpec = rememberHingeSpec()
    val alarmHinge = remember { Animatable(0f) }
    val cityHinge = remember { Animatable(0f) }
    val timerHinge = remember { Animatable(0f) }

    fun openHinge(anim: Animatable<Float, *>) {
        coroutineScope.launch {
            anim.animateTo(1f, hingeSpec)
        }
    }

    fun closeHinge(anim: Animatable<Float, *>, onClosed: () -> Unit) {
        coroutineScope.launch {
            anim.animateTo(0f, hingeSpec)
            onClosed()
        }
    }

    fun editAlarm(alarm: Alarm, isNew: Boolean) {
        editingAlarm = alarm
        isNewAlarm = isNew
        openHinge(alarmHinge)
    }

    fun newAlarm() {
        val calendar = java.util.Calendar.getInstance()
        editAlarm(
            Alarm(
                hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
                minute = calendar.get(java.util.Calendar.MINUTE),
                snoozeMinutes = defaultSnooze,
                autoSilenceMinutes = defaultAutoSilence
            ),
            isNew = true
        )
    }

    // ── Long press sheets ──────────────────────────────────────────────────
    var alarmMenuTarget by remember { mutableStateOf<Alarm?>(null) }
    var cityMenuTarget by remember { mutableStateOf<WorldCity?>(null) }

    // ── A page asked for from outside (the status bar, a timer notification) ─
    val pendingPage by ClockBridge.pendingPage.collectAsState()
    LaunchedEffect(pendingPage) {
        val page = ClockBridge.consume() ?: return@LaunchedEffect
        pager.animateScrollToPage(page.ordinal)
    }

    BackHandler(enabled = editingAlarm != null || isCityPickerOpen || isTimerSetupOpen ||
        alarmMenuTarget != null || cityMenuTarget != null) {
        when {
            alarmMenuTarget != null -> alarmMenuTarget = null
            cityMenuTarget != null -> cityMenuTarget = null
            editingAlarm != null -> closeHinge(alarmHinge) { editingAlarm = null }
            isCityPickerOpen -> closeHinge(cityHinge) { isCityPickerOpen = false }
            isTimerSetupOpen -> closeHinge(timerHinge) { isTimerSetupOpen = false }
        }
    }

    val listBottomPadding =
        com.serkantkn.zeta.ui.components.navigationBarBottom() + 96.dp
    val pageContentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding)

    // ── The four pages ─────────────────────────────────────────────────────
    @Composable
    fun Page(index: Int) {
        when (index) {
            PAGE_WORLD -> WorldClockPage(
                cities = cities,
                now = slowNow.longValue,
                showSeconds = showSeconds,
                onAddCity = { isCityPickerOpen = true; openHinge(cityHinge) },
                onCityLongPress = { cityMenuTarget = it },
                contentPadding = pageContentPadding
            )

            PAGE_ALARMS -> AlarmsPage(
                alarms = alarms,
                now = slowNow.longValue,
                canScheduleExact = canScheduleExact,
                onAdd = { newAlarm() },
                onEdit = { editAlarm(it, isNew = false) },
                onToggle = { viewModel.toggleAlarm(it) },
                onLongPress = { alarmMenuTarget = it },
                onFixExactPermission = {
                    // Only worth sending anyone to Settings when the phone really says no.
                    if (openExactAlarmSettings(context)) Unit else viewModel.checkExactAlarmPermission()
                },
                contentPadding = pageContentPadding
            )

            PAGE_STOPWATCH -> StopwatchPage(
                readingMillis = { stopwatch.readingAt(fastNow.longValue, fastRealtime.longValue) },
                laps = stopwatch.laps,
                onClearLaps = { viewModel.clearLaps() },
                contentPadding = pageContentPadding
            )

            PAGE_TIMER -> TimerPage(
                state = timer,
                remainingMillis = { timer.remainingAt(fastNow.longValue) },
                presets = presets,
                onSetDuration = { viewModel.setTimerDuration(it) },
                onOpenSetup = { isTimerSetupOpen = true; openHinge(timerHinge) },
                contentPadding = pageContentPadding
            )
        }
    }

    // ── The bar ────────────────────────────────────────────────────────────
    val barActions = when (currentTab) {
        PAGE_WORLD -> listOf(
            WpBarAction(Icons.Default.Add, stringResource(R.string.clock_add_city)) {
                isCityPickerOpen = true
                openHinge(cityHinge)
            }
        )

        PAGE_ALARMS -> listOf(
            WpBarAction(Icons.Default.Add, stringResource(R.string.clock_add_alarm)) { newAlarm() }
        )

        PAGE_STOPWATCH -> listOf(
            WpBarAction(
                if (stopwatch.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (stopwatch.isRunning) R.string.common_pause else R.string.common_start)
            ) { viewModel.toggleStopwatch() },
            WpBarAction(Icons.Default.Flag, stringResource(R.string.clock_lap)) { viewModel.addLap() },
            WpBarAction(Icons.Default.Refresh, stringResource(R.string.common_reset)) { viewModel.resetStopwatch() }
        )

        else -> listOf(
            WpBarAction(
                if (timer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (timer.isRunning) R.string.common_pause else R.string.common_start)
            ) { viewModel.toggleTimer() },
            WpBarAction(Icons.Default.Timer, stringResource(R.string.clock_set)) {
                isTimerSetupOpen = true
                openHinge(timerHinge)
            },
            WpBarAction(Icons.Default.Refresh, stringResource(R.string.common_reset)) { viewModel.resetTimer() }
        )
    }

    val barMenu = buildList {
        if (currentTab == PAGE_TIMER) {
            add(WpBarMenuItem(stringResource(R.string.clock_timer_add_minute)) { viewModel.addTimerMinute() })
        }
        add(WpBarMenuItem(stringResource(R.string.settings_clock_settings)) { SettingsBridge.open(SETTINGS_TAB_HUBS) })
    }

    val subScreenHinge = maxOf(alarmHinge.value, cityHinge.value, timerHinge.value)

    Box(modifier = modifier.fillMaxSize()) {
        // 1. The hub, which folds away when a sub-screen opens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pageHingeAway { subScreenHinge }
        ) {
            ZuneHub(
                title = stringResource(R.string.hub_clock),
                tabs = tabs,
                pager = pager,
                zuneTitleKey = "CLOCK",
                bar = ZuneHubBar(barActions, barMenu)
            ) { index -> Page(index) }
        }

        // 2. The pages that open over it
        HingeSubScreen(progress = alarmHinge.value, isOpen = editingAlarm != null, density = density) {
            val current = editingAlarm
            if (current != null) {
                AlarmEditScreen(
                    alarm = current,
                    isNew = isNewAlarm,
                    onSave = {
                        viewModel.saveAlarm(it)
                        closeHinge(alarmHinge) { editingAlarm = null }
                    },
                    onDelete = {
                        viewModel.deleteAlarm(it)
                        closeHinge(alarmHinge) { editingAlarm = null }
                    },
                    onClose = { closeHinge(alarmHinge) { editingAlarm = null } }
                )
            }
        }

        HingeSubScreen(progress = cityHinge.value, isOpen = isCityPickerOpen, density = density) {
            CityPickerScreen(
                existing = cities,
                now = slowNow.longValue,
                onPick = {
                    viewModel.addCity(it)
                    closeHinge(cityHinge) { isCityPickerOpen = false }
                },
                onClose = { closeHinge(cityHinge) { isCityPickerOpen = false } }
            )
        }

        HingeSubScreen(progress = timerHinge.value, isOpen = isTimerSetupOpen, density = density) {
            TimerSetupScreen(
                initialMillis = timer.totalMillis,
                initialLabel = timer.label,
                onCancel = { closeHinge(timerHinge) { isTimerSetupOpen = false } },
                onConfirm = { duration, label ->
                    viewModel.setTimerDuration(duration, label)
                    closeHinge(timerHinge) { isTimerSetupOpen = false }
                }
            )
        }

        // 3. Long press
        alarmMenuTarget?.let { alarm ->
            MessagingSheet(title = alarm.timeString, onDismiss = { alarmMenuTarget = null }) {
                SheetAction(label = stringResource(R.string.clock_edit_alarm)) {
                    alarmMenuTarget = null
                    editAlarm(alarm, isNew = false)
                }
                SheetAction(label = stringResource(R.string.clock_duplicate_alarm)) {
                    viewModel.duplicateAlarm(alarm)
                    alarmMenuTarget = null
                }
                if (alarm.isSnoozedAt(System.currentTimeMillis())) {
                    SheetAction(label = stringResource(R.string.clock_cancel_snooze)) {
                        viewModel.cancelSnooze(alarm)
                        alarmMenuTarget = null
                    }
                }
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error
                ) {
                    viewModel.deleteAlarm(alarm)
                    alarmMenuTarget = null
                }
            }
        }

        cityMenuTarget?.let { city ->
            MessagingSheet(title = city.cityName(context), onDismiss = { cityMenuTarget = null }) {
                SheetAction(label = stringResource(R.string.clock_move_up)) {
                    viewModel.moveCity(city, -1)
                    cityMenuTarget = null
                }
                SheetAction(label = stringResource(R.string.clock_move_down)) {
                    viewModel.moveCity(city, 1)
                    cityMenuTarget = null
                }
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error
                ) {
                    viewModel.removeCity(city)
                    cityMenuTarget = null
                }
            }
        }
    }
}

/** A page swinging in from the right on the same hinge the rest of the launcher uses. */
@Composable
private fun HingeSubScreen(
    progress: Float,
    isOpen: Boolean,
    density: androidx.compose.ui.unit.Density,
    content: @Composable () -> Unit
) {
    if (!isOpen && progress <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageHingeIn { progress }
    ) {
        content()
    }
}

/**
 * Where Android hides the switch that lets an app name an exact minute — opened only when the
 * phone actually says no. Returns false when there was nothing to fix, so the caller can simply
 * re-read the permission instead.
 */
private fun openExactAlarmSettings(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
    if (alarmManager == null || alarmManager.canScheduleExactAlarms()) return false
    return runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

private const val PAGE_WORLD = 0
private const val PAGE_ALARMS = 1
private const val PAGE_STOPWATCH = 2
private const val PAGE_TIMER = 3

/** The settings tab the hub's own settings live on. */
private const val SETTINGS_TAB_HUBS = "HUBS"
