package com.serkantkn.zunelauncher.ui.screens.calendar

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current

    val tabs = listOf(stringResource(R.string.cal_tab_agenda), stringResource(R.string.cal_tab_month), stringResource(R.string.cal_tab_day), stringResource(R.string.cal_tab_events))
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    val currentTab = pager.currentPage

    var showAddEventDialog by remember { mutableStateOf(false) }

    val events by viewModel.events.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()

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
                    text = stringResource(R.string.hub_calendar),
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
                    0 -> AgendaTabContent(
                        events = events,
                        onDeleteEvent = { viewModel.deleteEvent(it) }
                    )
                    1 -> MonthTabContent(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        events = events,
                        onSelectDate = { viewModel.selectDate(it) },
                        onNextMonth = { viewModel.nextMonth() },
                        onPreviousMonth = { viewModel.previousMonth() }
                    )
                    2 -> DayTabContent(
                        selectedDate = selectedDate,
                        events = events
                    )
                    3 -> AllEventsTabContent(
                        events = events,
                        onDeleteEvent = { viewModel.deleteEvent(it) }
                    )
                }
            }

            // Space for Bottom Bar
            Spacer(modifier = Modifier.height(72.dp))
        }

        // --- WINDOWS PHONE STYLE BOTTOM MENU BAR ---
        com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = listOf(
                com.serkantkn.zunelauncher.ui.components.WpBarAction(Icons.Default.Add, stringResource(R.string.cal_add_event)) { showAddEventDialog = true },
                com.serkantkn.zunelauncher.ui.components.WpBarAction(Icons.Default.DateRange, stringResource(R.string.common_today)) { viewModel.goToToday() }
            )
        )
    }

    // --- DIALOGS ---
    if (showAddEventDialog) {
        AddEventDialog(
            initialDate = selectedDate,
            onDismiss = { showAddEventDialog = false },
            onConfirm = { title, desc, timestamp, hour, minute, category ->
                viewModel.addEvent(
                    CalendarEvent(
                        title = title,
                        description = desc,
                        timestamp = timestamp,
                        hour = hour,
                        minute = minute,
                        category = category
                    )
                )
                showAddEventDialog = false
            }
        )
    }
    }
}

// ════════════════════════════════════════════════════════════
// TAB 0: AJANDA (AGENDA)
// ════════════════════════════════════════════════════════════
@Composable
fun AgendaTabContent(
    events: List<CalendarEvent>,
    onDeleteEvent: (CalendarEvent) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val sortedEvents = remember(events) { events.sortedBy { it.timestamp } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        contentPadding = PaddingValues(bottom = ZuneDimens.SpacingXl)
    ) {
        if (sortedEvents.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = zuneColors.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.cal_no_upcoming),
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(sortedEvents, key = { it.id }) { event ->
                AgendaEventItem(
                    event = event,
                    onDelete = { onDeleteEvent(event) }
                )
            }
        }
    }
}

@Composable
fun AgendaEventItem(
    event: CalendarEvent,
    onDelete: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var isDeleting by remember { mutableStateOf(false) }

    val dateStr = remember(event.timestamp) {
        val sdf = SimpleDateFormat("d MMMM EEEE", Locale.getDefault())
        sdf.format(Date(event.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
            .clickable { isDeleting = !isDeleting }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent Bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .background(zuneColors.accentColor, RoundedCornerShape(2.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
            Text(
                text = "$dateStr • ${event.formattedTime}",
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted
            )
            if (event.description.isNotEmpty()) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (event.linkedNoteId != null) {
                Text(
                    text = stringResource(R.string.cal_open_note),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .clickable { com.serkantkn.zunelauncher.data.repository.NotesBridge.open(event.linkedNoteId) }
                        .padding(top = 4.dp)
                )
            }
        }

        if (isDeleting) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.files_delete_cap),
                    tint = zuneColors.accentColor
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// TAB 1: AY (MONTH VIEW)
// ════════════════════════════════════════════════════════════
@Composable
fun MonthTabContent(
    currentMonth: Calendar,
    selectedDate: Calendar,
    events: List<CalendarEvent>,
    onSelectDate: (Calendar) -> Unit,
    onNextMonth: () -> Unit,
    onPreviousMonth: () -> Unit
) {
    val zuneColors = LocalZuneColors.current

    val monthYearStr = remember(currentMonth) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        sdf.format(currentMonth.time).uppercase()
    }

    val daysInMonth = remember(currentMonth) {
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0

        val list = mutableListOf<Calendar?>()
        for (i in 0 until firstDayOfWeek) {
            list.add(null)
        }
        for (day in 1..maxDays) {
            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            list.add(dayCal)
        }
        list
    }

    val weekDays = listOf(R.string.cal_mon, R.string.cal_tue, R.string.cal_wed, R.string.cal_thu, R.string.cal_fri, R.string.cal_sat, R.string.cal_sun).map { stringResource(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        // Month Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.cal_prev_month),
                    tint = zuneColors.accentColor
                )
            }
            Text(
                text = monthYearStr,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.cal_next_month),
                    tint = zuneColors.accentColor
                )
            }
        }

        // Weekday Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDays.forEach { dayName ->
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = zuneColors.textMuted,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Days Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(daysInMonth) { dayCal ->
                if (dayCal == null) {
                    Box(modifier = Modifier.size(42.dp))
                } else {
                    val isSelected = isSameDay(dayCal, selectedDate)
                    val isToday = isSameDay(dayCal, Calendar.getInstance())

                    val hasEvents = remember(events, dayCal) {
                        events.any { isSameDay(Date(it.timestamp), dayCal) }
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isSelected -> zuneColors.accentColor
                                    isToday -> zuneColors.accentColor.copy(alpha = 0.25f)
                                    else -> if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)
                                }
                            )
                            .clickable { onSelectDate(dayCal) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${dayCal.get(Calendar.DAY_OF_MONTH)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = when {
                                    isSelected -> Color.White
                                    isToday -> zuneColors.accentColor
                                    else -> if (zuneColors.isDark) Color.White else Color.Black
                                }
                            )
                            if (hasEvents) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else zuneColors.accentColor)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Events Sub-section
        HorizontalDivider(
            color = zuneColors.textMuted.copy(alpha = 0.2f),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        val selectedDayEvents = remember(events, selectedDate) {
            events.filter { isSameDay(Date(it.timestamp), selectedDate) }
        }

        val selectedDateStr = remember(selectedDate) {
            val sdf = SimpleDateFormat("d MMMM EEEE", Locale.getDefault())
            sdf.format(selectedDate.time)
        }

        Text(
            text = selectedDateStr,
            style = MaterialTheme.typography.titleMedium,
            color = zuneColors.accentColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (selectedDayEvents.isEmpty()) {
            Text(
                text = stringResource(R.string.cal_no_events_day),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(selectedDayEvents) { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.formattedTime,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = zuneColors.accentColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (zuneColors.isDark) Color.White else Color.Black
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// TAB 2: GÜN (DAY VIEW TIMELINE)
// ════════════════════════════════════════════════════════════
@Composable
fun DayTabContent(
    selectedDate: Calendar,
    events: List<CalendarEvent>
) {
    val zuneColors = LocalZuneColors.current

    val dayStr = remember(selectedDate) {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        sdf.format(selectedDate.time)
    }

    val dayEvents = remember(events, selectedDate) {
        events.filter { isSameDay(Date(it.timestamp), selectedDate) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        Text(
            text = dayStr,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
            color = zuneColors.accentColor,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ZuneDimens.SpacingXl)
        ) {
            items((8..22).toList()) { hour ->
                val hourEvents = dayEvents.filter { it.hour == hour }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = String.format("%02d:00", hour),
                        style = MaterialTheme.typography.labelMedium,
                        color = zuneColors.textMuted,
                        modifier = Modifier.width(54.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        if (hourEvents.isEmpty()) {
                            HorizontalDivider(
                                color = zuneColors.textMuted.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            hourEvents.forEach { event ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(zuneColors.accentColor.copy(alpha = 0.85f))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        if (event.description.isNotEmpty()) {
                                            Text(
                                                text = event.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// TAB 3: ETKİNLİKLER (ALL EVENTS LIST)
// ════════════════════════════════════════════════════════════
@Composable
fun AllEventsTabContent(
    events: List<CalendarEvent>,
    onDeleteEvent: (CalendarEvent) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredEvents = remember(events, searchQuery) {
        if (searchQuery.isBlank()) {
            events
        } else {
            events.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.cal_search_hint), color = zuneColors.textMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = zuneColors.textMuted) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = zuneColors.accentColor,
                unfocusedBorderColor = zuneColors.textMuted.copy(alpha = 0.3f)
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ZuneDimens.SpacingXl)
        ) {
            items(filteredEvents, key = { it.id }) { event ->
                AgendaEventItem(
                    event = event,
                    onDelete = { onDeleteEvent(event) }
                )
            }

            if (filteredEvents.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isEmpty()) stringResource(R.string.cal_no_events) else stringResource(R.string.cal_no_search_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// WINDOWS PHONE BOTTOM BAR
// ════════════════════════════════════════════════════════════


// ════════════════════════════════════════════════════════════
// ADD EVENT DIALOG
// ════════════════════════════════════════════════════════════
@Composable
fun AddEventDialog(
    initialDate: Calendar,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, Int, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(10) }
    var minute by remember { mutableStateOf(0) }

    val zuneColors = LocalZuneColors.current

    com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cal_new_event_cap),
        confirmButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_add_cap),
                onClick = {
                    dismissWithAnim {
                        if (title.isNotBlank()) {
                            onConfirm(title, desc, initialDate.timeInMillis, hour, minute, "Genel")
                        }
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.cal_event_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text(stringResource(R.string.cal_event_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cal_time_label), style = MaterialTheme.typography.bodyMedium, color = if (zuneColors.isDark) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (hour > 0) hour-- }) { Text("-", style = MaterialTheme.typography.headlineSmall, color = zuneColors.accentColor) }
                    Text(String.format("%02d:%02d", hour, minute), style = MaterialTheme.typography.titleMedium, color = if (zuneColors.isDark) Color.White else Color.Black)
                    IconButton(onClick = { if (hour < 23) hour++ }) { Text("+", style = MaterialTheme.typography.headlineSmall, color = zuneColors.accentColor) }
                }
            }
        }
    }
}

// Helper to compare dates
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isSameDay(date: Date, cal2: Calendar): Boolean {
    val cal1 = Calendar.getInstance()
    cal1.time = date
    return isSameDay(cal1, cal2)
}
