package com.serkantkn.zunelauncher.ui.screens.clock

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClockHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current

    val tabs = listOf(stringResource(R.string.clock_tab_world), stringResource(R.string.clock_tab_alarms), stringResource(R.string.clock_tab_stopwatch), stringResource(R.string.clock_tab_timer))
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    val currentTab = pager.currentPage

    var showAddAlarmDialog by remember { mutableStateOf(false) }
    var showAddCityDialog by remember { mutableStateOf(false) }
    var showSetTimerDialog by remember { mutableStateOf(false) }

    val alarms by viewModel.alarms.collectAsState()
    val worldCities by viewModel.worldCities.collectAsState()
    val stopwatchTimeMs by viewModel.stopwatchTimeMs.collectAsState()
    val isStopwatchRunning by viewModel.isStopwatchRunning.collectAsState()
    val stopwatchLaps by viewModel.stopwatchLaps.collectAsState()
    val timerRemainingMs by viewModel.timerRemainingMs.collectAsState()
    val timerTotalMs by viewModel.timerTotalMs.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidthDp.toPx() }
    val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
    val overflowYPx = with(density) { (-24).dp.toPx() }

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP HEADER WITH PARALLAX ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.hub_clock),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(
                        top = 28.dp,
                        bottom = 4.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal
                    )
                )

                ZunePivotTabs(
                    tabs = tabs,
                    state = pager,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // --- PAGER CONTENT ---
            ZuneLoopingPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WorldClockTabContent(
                        worldCities = worldCities,
                        onRemoveCity = { viewModel.removeWorldCity(it) }
                    )
                    1 -> AlarmsTabContent(
                        alarms = alarms,
                        onToggle = { viewModel.toggleAlarm(it) },
                        onDelete = { viewModel.deleteAlarm(it) }
                    )
                    2 -> StopwatchTabContent(
                        timeMs = stopwatchTimeMs,
                        laps = stopwatchLaps
                    )
                    3 -> TimerTabContent(
                        remainingMs = timerRemainingMs,
                        totalMs = timerTotalMs,
                        isRunning = isTimerRunning,
                        onOpenSetDialog = { showSetTimerDialog = true }
                    )
                }
            }

            // Space for Bottom Bar
            Spacer(modifier = Modifier.height(72.dp))
        }

        // --- WINDOWS PHONE STYLE BOTTOM MENU BAR ---
        WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = when (currentTab) {
                0 -> listOf(
                    WpBarAction(Icons.Default.Add, stringResource(R.string.clock_add_city)) { showAddCityDialog = true }
                )
                1 -> listOf(
                    WpBarAction(Icons.Default.Add, stringResource(R.string.clock_add_alarm)) { showAddAlarmDialog = true }
                )
                2 -> listOf(
                    WpBarAction(
                        if (isStopwatchRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isStopwatchRunning) stringResource(R.string.common_stop) else stringResource(R.string.common_start)
                    ) { viewModel.toggleStopwatch() },
                    WpBarAction(Icons.Default.Flag, stringResource(R.string.clock_lap)) { viewModel.addStopwatchLap() },
                    WpBarAction(Icons.Default.Refresh, stringResource(R.string.common_reset)) { viewModel.resetStopwatch() }
                )
                3 -> listOf(
                    WpBarAction(
                        if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isTimerRunning) stringResource(R.string.common_stop) else stringResource(R.string.common_start)
                    ) { viewModel.toggleTimer() },
                    WpBarAction(Icons.Default.Timer, stringResource(R.string.clock_set)) { showSetTimerDialog = true },
                    WpBarAction(Icons.Default.Refresh, stringResource(R.string.common_reset)) { viewModel.resetTimer() }
                )
                else -> emptyList()
            }
        )
    }

    // --- DIALOGS ---
    if (showAddAlarmDialog) {
        AddAlarmDialog(
            onDismiss = { showAddAlarmDialog = false },
            onConfirm = { hour, minute, label ->
                viewModel.addAlarm(Alarm(hour = hour, minute = minute, label = label))
                showAddAlarmDialog = false
            }
        )
    }

    if (showAddCityDialog) {
        AddCityDialog(
            onDismiss = { showAddCityDialog = false },
            onConfirm = { city ->
                viewModel.addWorldCity(city)
                showAddCityDialog = false
            }
        )
    }

    if (showSetTimerDialog) {
        SetTimerDialog(
            initialMs = timerTotalMs,
            onDismiss = { showSetTimerDialog = false },
            onConfirm = { h, m, s ->
                viewModel.setTimerDuration(h, m, s)
                showSetTimerDialog = false
            }
        )
    }
    }
}

// ════════════════════════════════════════════════════════════
// PIVOT CONTENT 0: DÜNYA SAATİ
// ════════════════════════════════════════════════════════════
@Composable
fun WorldClockTabContent(
    worldCities: List<WorldCity>,
    onRemoveCity: (WorldCity) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val localTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val localDateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        contentPadding = PaddingValues(bottom = ZuneDimens.SpacingXl)
    ) {
        // Current Local Time Display
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ZuneDimens.SpacingLg)
            ) {
                Text(
                    text = stringResource(R.string.clock_local_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor
                )
                Text(
                    text = localTimeFormat.format(currentTime),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 64.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )
                Text(
                    text = localDateFormat.format(currentTime),
                    style = MaterialTheme.typography.bodyLarge,
                    color = zuneColors.textMuted
                )
            }
            HorizontalDivider(
                color = zuneColors.textMuted.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = ZuneDimens.SpacingMd)
            )
        }

        // World Cities List
        items(worldCities, key = { it.id }) { city ->
            WorldCityItem(
                city = city,
                currentTime = currentTime,
                onDelete = { onRemoveCity(city) }
            )
        }
    }
}

@Composable
fun WorldCityItem(
    city: WorldCity,
    currentTime: Date,
    onDelete: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var isDeleting by remember { mutableStateOf(false) }

    val cityCal = Calendar.getInstance(TimeZone.getTimeZone(city.timeZoneId))
    cityCal.time = currentTime

    val cityTimeStr = String.format("%02d:%02d", cityCal.get(Calendar.HOUR_OF_DAY), cityCal.get(Calendar.MINUTE))
    
    // Time diff
    val localCal = Calendar.getInstance()
    val localOffset = localCal.timeZone.getOffset(currentTime.time)
    val cityOffset = TimeZone.getTimeZone(city.timeZoneId).getOffset(currentTime.time)
    val diffHours = (cityOffset - localOffset) / (1000 * 60 * 60)

    val diffString = when {
        diffHours == 0 -> stringResource(R.string.clock_same_as_local)
        diffHours > 0 -> stringResource(R.string.clock_hours_ahead, diffHours)
        else -> stringResource(R.string.clock_hours_diff, diffHours)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZuneDimens.SpacingSm)
            .clickable { isDeleting = !isDeleting },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(city.cityRes),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
            Text(
                text = "${stringResource(city.countryRes)} • $diffString",
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )
        }

        if (isDeleting) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.files_delete_cap),
                    tint = zuneColors.accentColor
                )
            }
        } else {
            Text(
                text = cityTimeStr,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// PIVOT CONTENT 1: ALARMLAR
// ════════════════════════════════════════════════════════════
@Composable
fun AlarmsTabContent(
    alarms: List<Alarm>,
    onToggle: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    val zuneColors = LocalZuneColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        contentPadding = PaddingValues(bottom = ZuneDimens.SpacingXl)
    ) {
        items(alarms, key = { it.id }) { alarm ->
            AlarmItem(
                alarm = alarm,
                onToggle = { onToggle(alarm) },
                onDelete = { onDelete(alarm) }
            )
        }

        if (alarms.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.clock_no_alarms),
                    style = MaterialTheme.typography.bodyLarge,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
                )
            }
        }
    }
}

@Composable
fun AlarmItem(
    alarm: Alarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var isDeleting by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZuneDimens.SpacingSm)
            .clickable { isDeleting = !isDeleting },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarm.timeString,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
                color = if (alarm.isEnabled) MaterialTheme.colorScheme.onBackground else zuneColors.textMuted
            )
            if (alarm.label.isNotEmpty()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted
                )
            }
        }

        if (isDeleting) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = zuneColors.accentColor
                )
            }
        } else {
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = zuneColors.accentColor,
                    uncheckedThumbColor = zuneColors.textMuted,
                    uncheckedTrackColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlarmDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, String) -> Unit
) {
    val timePickerState = rememberTimePickerState()
    val zuneColors = LocalZuneColors.current

    com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.clock_add_alarm_cap),
        confirmButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_save_cap),
                onClick = {
                    dismissWithAnim {
                        onConfirm(timePickerState.hour, timePickerState.minute, "Alarm")
                    }
                },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim() },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

// ════════════════════════════════════════════════════════════
// PIVOT CONTENT 2: KRONOMETRE
// ════════════════════════════════════════════════════════════
@Composable
fun StopwatchTabContent(
    timeMs: Long,
    laps: List<Long>
) {
    val zuneColors = LocalZuneColors.current

    val minutes = (timeMs / 1000) / 60
    val seconds = (timeMs / 1000) % 60
    val hundredths = (timeMs % 1000) / 10

    val timeFormatted = String.format("%02d:%02d.%02d", minutes, seconds, hundredths)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        // Display Time
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ZuneDimens.SpacingXl),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = 68.sp,
                    letterSpacing = (-2).sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
        }

        HorizontalDivider(color = zuneColors.textMuted.copy(alpha = 0.2f))

        // Laps List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = ZuneDimens.SpacingMd)
        ) {
            itemsIndexed(laps) { index, lapMs ->
                val lapMin = (lapMs / 1000) / 60
                val lapSec = (lapMs / 1000) % 60
                val lapHundred = (lapMs % 1000) / 10
                val lapStr = String.format("%02d:%02d.%02d", lapMin, lapSec, lapHundred)
                val lapNum = laps.size - index

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ZuneDimens.SpacingSm),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.clock_lap_n, lapNum),
                        style = MaterialTheme.typography.titleMedium,
                        color = zuneColors.textMuted
                    )
                    Text(
                        text = lapStr,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (zuneColors.isDark) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// PIVOT CONTENT 3: SAYAÇ
// ════════════════════════════════════════════════════════════
@Composable
fun TimerTabContent(
    remainingMs: Long,
    totalMs: Long,
    isRunning: Boolean,
    onOpenSetDialog: () -> Unit
) {
    val zuneColors = LocalZuneColors.current

    val totalSec = (remainingMs / 1000)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60

    val formattedTime = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    val progress = if (totalMs > 0) (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .clickable { if (!isRunning) onOpenSetDialog() }
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = zuneColors.accentColor,
                strokeWidth = 6.dp,
                trackColor = zuneColors.textMuted.copy(alpha = 0.2f),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 54.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )
                if (!isRunning) {
                    Text(
                        text = stringResource(R.string.clock_change_duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = zuneColors.accentColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════


// ════════════════════════════════════════════════════════════
// DIALOGS
// ════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCityDialog(
    onDismiss: () -> Unit,
    onConfirm: (WorldCity) -> Unit
) {
    val availableCities = listOf(
        WorldCity(cityRes = R.string.city_berlin, countryRes = R.string.country_germany, timeZoneId = "Europe/Berlin"),
        WorldCity(cityRes = R.string.city_new_york, countryRes = R.string.country_usa, timeZoneId = "America/New_York"),
        WorldCity(cityRes = R.string.city_los_angeles, countryRes = R.string.country_usa, timeZoneId = "America/Los_Angeles"),
        WorldCity(cityRes = R.string.city_tokyo, countryRes = R.string.country_japan, timeZoneId = "Asia/Tokyo"),
        WorldCity(cityRes = R.string.city_beijing, countryRes = R.string.country_china, timeZoneId = "Asia/Shanghai"),
        WorldCity(cityRes = R.string.city_dubai, countryRes = R.string.country_uae, timeZoneId = "Asia/Dubai"),
        WorldCity(cityRes = R.string.city_moscow, countryRes = R.string.country_russia, timeZoneId = "Europe/Moscow"),
        WorldCity(cityRes = R.string.city_rome, countryRes = R.string.country_italy, timeZoneId = "Europe/Rome"),
        WorldCity(cityRes = R.string.city_sydney, countryRes = R.string.country_australia, timeZoneId = "Australia/Sydney")
    )

    var selectedCity by remember { mutableStateOf(availableCities.first()) }
    val zuneColors = LocalZuneColors.current

    com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.clock_add_city_cap),
        confirmButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_add_cap),
                onClick = {
                    dismissWithAnim {
                        onConfirm(selectedCity)
                    }
                },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim() },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(availableCities) { city ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCity = city }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedCity.timeZoneId == city.timeZoneId),
                        onClick = { selectedCity = city },
                        colors = RadioButtonDefaults.colors(selectedColor = zuneColors.accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(city.cityRes)} (${stringResource(city.countryRes)})",
                        color = if (zuneColors.isDark) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun SetTimerDialog(
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    var minutes by remember { mutableStateOf(((initialMs / 1000) / 60).toInt()) }
    var seconds by remember { mutableStateOf(((initialMs / 1000) % 60).toInt()) }
    val zuneColors = LocalZuneColors.current

    com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.clock_timer_duration),
        confirmButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_set_cap),
                onClick = {
                    dismissWithAnim {
                        onConfirm(0, minutes, seconds)
                    }
                },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim() },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.clock_minutes), style = MaterialTheme.typography.labelMedium, color = zuneColors.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (minutes > 0) minutes-- }) { Text("-", style = MaterialTheme.typography.headlineSmall, color = zuneColors.accentColor) }
                    Text(text = "$minutes", style = MaterialTheme.typography.titleLarge, color = if (zuneColors.isDark) Color.White else Color.Black)
                    IconButton(onClick = { if (minutes < 99) minutes++ }) { Text("+", style = MaterialTheme.typography.headlineSmall, color = zuneColors.accentColor) }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.clock_seconds), style = MaterialTheme.typography.labelMedium, color = zuneColors.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (seconds > 0) seconds-- }) { Text("-", style = MaterialTheme.typography.headlineSmall, color = zuneColors.accentColor) }
                    Text(text = "$seconds", style = MaterialTheme.typography.titleLarge, color = if (zuneColors.isDark) Color.White else Color.Black)
                    IconButton(onClick = { if (seconds < 59) seconds++ }) { Text("+", style = MaterialTheme.typography.headlineSmall, color = zuneColors.accentColor) }
                }
            }
        }
    }
}
