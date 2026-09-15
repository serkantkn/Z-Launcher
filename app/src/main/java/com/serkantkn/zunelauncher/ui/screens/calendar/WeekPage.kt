package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DAY_MS
import com.serkantkn.zunelauncher.util.eventsOn
import com.serkantkn.zunelauncher.util.isSameDay
import com.serkantkn.zunelauncher.util.weekDays
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The week, as seven days of bars.
 *
 * A proper week grid — seven columns of hours — is unreadable on a phone: each column ends up
 * about a centimetre wide and no event title fits in one. So the week is drawn the other way
 * round: each day is a row holding a bar from midnight to midnight, and the events are laid on it
 * where they actually fall.
 *
 * You cannot read what anything is called, which is not what this page is for. You can see at a
 * glance which mornings are gone and which afternoons are free, which is.
 */
@Composable
internal fun WeekPage(
    selectedDayMillis: Long,
    events: List<CalendarEvent>,
    weekStart: Int,
    now: Long,
    onSelectDay: (Long) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val days = remember(selectedDayMillis, weekStart) { weekDays(selectedDayMillis, weekStart) }
    val selectedEvents = remember(events, selectedDayMillis) { eventsOn(events, selectedDayMillis) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Text(
                text = weekRangeText(days.first(), days.last()),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        days.forEach { day ->
            item(key = "wk_$day") {
                WeekDayLane(
                    dayMillis = day,
                    events = eventsOn(events, day),
                    isToday = isSameDay(day, now),
                    isSelected = isSameDay(day, selectedDayMillis),
                    now = now,
                    onClick = { onSelectDay(day) }
                )
            }
        }

        item {
            DayHeading(dayMillis = selectedDayMillis, nowMillis = now, count = selectedEvents.size)
        }

        if (selectedEvents.isEmpty()) {
            item { MetroEmpty(message = stringResource(R.string.cal_no_events_day)) }
        }

        selectedEvents.forEach { event ->
            item(key = "wkev_${event.id}") {
                EventRow(
                    event = event,
                    onClick = { onOpen(event) },
                    onLongClick = { onLongPress(event) }
                )
            }
        }
    }
}

@Composable
private fun WeekDayLane(
    dayMillis: Long,
    events: List<CalendarEvent>,
    isToday: Boolean,
    isSelected: Boolean,
    now: Long,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val dayName = remember(dayMillis) {
        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayMillis)).lowercase(Locale.getDefault())
    }
    val dayNumber = remember(dayMillis) {
        SimpleDateFormat("d", Locale.getDefault()).format(Date(dayMillis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(44.dp)) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) zuneColors.accentColor else zuneColors.textDim
            )
            Text(
                text = dayNumber,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isToday) FontWeight.Normal else FontWeight.Light,
                    fontFeatureSettings = "tnum"
                ),
                color = if (isToday) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(LANE_HEIGHT)
                .then(
                    if (isSelected) Modifier.border(2.dp, zuneColors.accentColor) else Modifier
                )
                .background(
                    if (zuneColors.isDark) Color.White.copy(alpha = 0.06f)
                    else Color.Black.copy(alpha = 0.05f)
                )
        ) {
            val laneWidth = maxWidth
            events.forEach { event ->
                val startFraction = fractionOfDay(event.startMillis, dayMillis)
                val endFraction = fractionOfDay(event.endMillis, dayMillis)
                val width = ((endFraction - startFraction).coerceAtLeast(MIN_BLOCK)) * laneWidth.value
                Box(
                    modifier = Modifier
                        .offset(x = (startFraction * laneWidth.value).dp)
                        .width(width.dp)
                        .fillMaxHeight()
                        .background(parseColor(event.colorHex, zuneColors.accentColor))
                )
            }
            if (isToday) {
                Box(
                    modifier = Modifier
                        .offset(x = (fractionOfDay(now, dayMillis) * laneWidth.value).dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            }
        }
    }
}

/** Where in the day a moment sits, clipped to the day's own two ends. */
private fun fractionOfDay(millis: Long, dayStartMillis: Long): Float =
    ((millis - dayStartMillis).toFloat() / DAY_MS).coerceIn(0f, 1f)

@Composable
private fun weekRangeText(firstDay: Long, lastDay: Long): String {
    val format = remember { SimpleDateFormat("d MMMM", Locale.getDefault()) }
    return "${format.format(Date(firstDay))} – ${format.format(Date(lastDay))}"
        .lowercase(Locale.getDefault())
}

private val LANE_HEIGHT = 26.dp

/** A five-minute meeting still has to be visible. */
private const val MIN_BLOCK = 0.012f
