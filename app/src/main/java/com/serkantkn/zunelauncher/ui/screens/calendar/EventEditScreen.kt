package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.CalendarInfo
import com.serkantkn.zunelauncher.ui.components.MetroChip
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.components.MetroSwitchRow
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.WpDurationPicker
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.RepeatKind
import com.serkantkn.zunelauncher.util.isSameDay
import com.serkantkn.zunelauncher.util.monthGrid
import com.serkantkn.zunelauncher.util.repeatKindOf
import com.serkantkn.zunelauncher.util.shiftMonth
import com.serkantkn.zunelauncher.util.startOfDay
import java.util.Calendar

/**
 * Writing an event down.
 *
 * A page, not a dialog. The old dialog could ask for a title, a one-line description and an hour —
 * not a minute, not a date, not which day it repeats on, not where it is, and not which calendar
 * it belongs to. Half of those fields already existed in the stored event and nothing could fill
 * them in.
 *
 * The date is picked on a small month of its own rather than with a system picker, so the page
 * looks like the hub it opened from.
 */
@Composable
internal fun EventEditScreen(
    event: CalendarEvent,
    isNew: Boolean,
    calendars: List<CalendarInfo>,
    selectedCalendarId: Long,
    weekStart: Int,
    onSave: (CalendarEvent, Long) -> Unit,
    onDelete: (CalendarEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_calendar),
        title = stringResource(if (isNew) R.string.cal_new_event else R.string.cal_edit_event),
        onClose = onClose,
        modifier = modifier
    ) { padding ->
        EventEditBody(
            event = event,
            isNew = isNew,
            calendars = calendars,
            initialCalendarId = selectedCalendarId,
            weekStart = weekStart,
            onSave = onSave,
            onDelete = onDelete,
            contentPadding = padding
        )
    }
}

@Composable
private fun EventEditBody(
    event: CalendarEvent,
    isNew: Boolean,
    calendars: List<CalendarInfo>,
    initialCalendarId: Long,
    weekStart: Int,
    onSave: (CalendarEvent, Long) -> Unit,
    onDelete: (CalendarEvent) -> Unit,
    contentPadding: PaddingValues
) {
    val zuneColors = LocalZuneColors.current
    var draft by remember(event.id) { mutableStateOf(event) }
    var calendarId by remember(event.id) { mutableStateOf(initialCalendarId) }
    var pickerMonth by remember(event.id) { mutableStateOf(monthOf(event.startMillis)) }

    val readOnly = !event.isEditable && !isNew

    fun setDay(dayMillis: Long) {
        val start = Calendar.getInstance().apply { timeInMillis = draft.startMillis }
        val day = Calendar.getInstance().apply { timeInMillis = dayMillis }
        val newStart = Calendar.getInstance().apply {
            timeInMillis = dayMillis
            set(Calendar.HOUR_OF_DAY, start.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, start.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        draft = draft.copy(startMillis = newStart, endMillis = newStart + draft.durationMillis)
        pickerMonth = monthOf(day.timeInMillis)
    }

    fun setTime(hour: Int, minute: Int) {
        val newStart = Calendar.getInstance().apply {
            timeInMillis = draft.startMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        draft = draft.copy(startMillis = newStart, endMillis = newStart + draft.durationMillis)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        if (readOnly) {
            item {
                Text(
                    text = stringResource(R.string.cal_read_only_calendar),
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.accentColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        // ── What ──
        item {
            MetroTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                placeholder = stringResource(R.string.cal_event_title)
            )
        }

        // ── When ──
        item { MetroRule(title = stringResource(R.string.cal_when)) }
        item {
            MiniMonth(
                monthMillis = pickerMonth,
                selectedDayMillis = startOfDay(draft.startMillis),
                weekStart = weekStart,
                onPrevious = { pickerMonth = shiftMonth(pickerMonth, -1) },
                onNext = { pickerMonth = shiftMonth(pickerMonth, 1) },
                onSelectDay = { setDay(it) }
            )
        }
        item {
            MetroSwitchRow(
                title = stringResource(R.string.cal_all_day),
                checked = draft.isAllDay,
                onCheckedChange = { draft = draft.copy(isAllDay = it) }
            )
        }
        if (!draft.isAllDay) {
            item {
                WpDurationPicker(
                    hours = draft.hour,
                    minutes = draft.minute,
                    seconds = null,
                    onHours = { setTime(it, draft.minute) },
                    onMinutes = { setTime(draft.hour, it) },
                    onSeconds = {},
                    hourLabel = stringResource(R.string.clock_hours),
                    minuteLabel = stringResource(R.string.clock_minutes),
                    secondLabel = stringResource(R.string.clock_seconds),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.cal_duration).lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DURATION_CHOICES) { minutes ->
                        MetroChip(
                            label = durationLabel(minutes),
                            selected = draft.durationMillis == minutes * 60_000L,
                            onClick = {
                                draft = draft.copy(endMillis = draft.startMillis + minutes * 60_000L)
                            }
                        )
                    }
                }
            }
        }

        // ── Where and what about ──
        item { MetroRule(title = stringResource(R.string.cal_details)) }
        item {
            MetroTextField(
                value = draft.location,
                onValueChange = { draft = draft.copy(location = it) },
                placeholder = stringResource(R.string.cal_location)
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            MetroTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                placeholder = stringResource(R.string.cal_event_description)
            )
        }

        // ── Which calendar ──
        item { MetroRule(title = stringResource(R.string.cal_calendar)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calendars) { info ->
                    MetroChip(
                        label = info.displayName.ifBlank { info.accountName },
                        selected = calendarId == info.id,
                        onClick = { calendarId = info.id }
                    )
                }
                items(listOf(CalendarInfo.LOCAL_ID)) { localId ->
                    MetroChip(
                        label = stringResource(R.string.cal_local_calendar),
                        selected = calendarId == localId,
                        onClick = { calendarId = localId }
                    )
                }
            }
        }

        // ── Warning and repeat ──
        item { MetroRule(title = stringResource(R.string.cal_reminder)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(-1) + CalendarEvent.REMINDER_CHOICES) { minutes ->
                    MetroChip(
                        label = reminderLabel(minutes),
                        selected = (draft.reminderMinutes ?: -1) == minutes,
                        onClick = {
                            draft = draft.copy(reminderMinutes = minutes.takeIf { it >= 0 })
                        }
                    )
                }
            }
        }

        item { MetroRule(title = stringResource(R.string.cal_repeat)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CalendarEvent.RECURRENCE_CHOICES) { rule ->
                    MetroChip(
                        label = repeatLabel(repeatKindOf(rule)),
                        selected = (draft.recurrence ?: "") == (rule ?: ""),
                        onClick = { draft = draft.copy(recurrence = rule) }
                    )
                }
            }
        }

        // ── Colour, for the launcher's own events ──
        if (calendarId == CalendarInfo.LOCAL_ID) {
            item { MetroRule(title = stringResource(R.string.cal_colour)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PALETTE.forEach { hex ->
                        ColourSwatch(
                            hex = hex,
                            selected = draft.colorHex.equals(hex, ignoreCase = true),
                            onClick = { draft = draft.copy(colorHex = hex) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                if (!readOnly) {
                    Text(
                        text = stringResource(R.string.common_save_cap).lowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.accentColor,
                        modifier = Modifier.clickable {
                            val title = draft.title.trim()
                            onSave(draft.copy(title = title), calendarId)
                        }
                    )
                }
                if (!isNew && draft.isEditable) {
                    Text(
                        text = stringResource(R.string.common_delete).lowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onDelete(draft) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── A month small enough to pick a date on ──────────────────────────────────

@Composable
private fun MiniMonth(
    monthMillis: Long,
    selectedDayMillis: Long,
    weekStart: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDay: (Long) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val grid = remember(monthMillis, weekStart) { monthGrid(monthMillis, weekStart) }
    val names = remember(weekStart) { shortWeekdayNames(weekStart) }
    val today = remember { System.currentTimeMillis() }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MiniChevron("‹", onPrevious)
            Text(
                text = formatMonthYear(monthMillis),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            MiniChevron("›", onNext)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            names.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textDim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { column ->
                    val day = week.getOrNull(column)
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        MonthDayCell(
                            dayNumber = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_MONTH),
                            isToday = isSameDay(day, today),
                            isSelected = isSameDay(day, selectedDayMillis),
                            isOtherMonth = false,
                            markColors = emptyList(),
                            onClick = { onSelectDay(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniChevron(text: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = zuneColors.accentColor)
    }
}

@Composable
private fun ColourSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(parseColor(hex, zuneColors.accentColor))
            .then(
                if (selected) {
                    Modifier.border(3.dp, if (zuneColors.isDark) Color.White else Color.Black)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

// ── Words ───────────────────────────────────────────────────────────────────

@Composable
private fun durationLabel(minutes: Int): String = when {
    minutes >= 1440 -> stringResource(R.string.cal_all_day)
    minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.clock_hours_short, minutes / 60)
    minutes >= 60 -> stringResource(R.string.clock_hours_minutes_short, minutes / 60, minutes % 60)
    else -> stringResource(R.string.clock_minutes_short, minutes)
}

@Composable
private fun reminderLabel(minutes: Int): String = when {
    minutes < 0 -> stringResource(R.string.cal_no_reminder)
    minutes == 0 -> stringResource(R.string.cal_reminder_at_start)
    minutes >= 1440 -> stringResource(R.string.cal_reminder_days, minutes / 1440)
    minutes >= 60 -> stringResource(R.string.clock_hours_short, minutes / 60)
    else -> stringResource(R.string.clock_minutes_short, minutes)
}

@Composable
private fun repeatLabel(kind: RepeatKind): String = when (kind) {
    RepeatKind.ONCE -> stringResource(R.string.clock_repeat_once)
    RepeatKind.DAILY -> stringResource(R.string.cal_repeat_daily)
    RepeatKind.WEEKLY -> stringResource(R.string.cal_repeat_weekly)
    RepeatKind.MONTHLY -> stringResource(R.string.cal_repeat_monthly)
    RepeatKind.YEARLY -> stringResource(R.string.cal_repeat_yearly)
    RepeatKind.OTHER -> stringResource(R.string.cal_repeat_custom)
}

private fun monthOf(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(millis)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private val DURATION_CHOICES = listOf(15, 30, 60, 90, 120, 240, 480)

/** The Zune palette, for events the launcher keeps itself. */
private val PALETTE = listOf("#E0007A", "#00A3A3", "#7B1FA2", "#F57C00", "#2E7D32", "#1565C0")
