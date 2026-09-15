package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.metroDigitStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.eventsOn
import com.serkantkn.zunelauncher.util.isSameDay
import java.util.Calendar
import java.util.Locale

/**
 * One day, hour by hour.
 *
 * All twenty-four of them. The old page drew eight in the morning to ten at night and nothing
 * else, so an event at seven or at midnight was simply not on the page — it existed in the list
 * and vanished here, which is worse than not having the view at all.
 *
 * It opens scrolled to the hour it is now rather than to midnight, and the current time is drawn
 * across the page as a line, so where you are in the day is the first thing you see.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DayPage(
    dayMillis: Long,
    events: List<CalendarEvent>,
    now: Long,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    onAddAt: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val dayEvents = remember(events, dayMillis) { eventsOn(events, dayMillis) }
    val allDay = dayEvents.filter { it.isAllDay }
    val timed = dayEvents.filterNot { it.isAllDay }
    val isToday = isSameDay(dayMillis, now)
    val nowHour = remember(now) {
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(dayMillis, isToday) {
        // Land on the working part of the day rather than on midnight.
        val target = if (isToday) (nowHour - 1).coerceAtLeast(0) else DEFAULT_SCROLL_HOUR
        listState.scrollToItem((target + HEADER_ITEMS).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding
    ) {
        item {
            Text(
                text = formatDate(dayMillis).lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = zuneColors.accentColor,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        item {
            if (allDay.isEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    allDay.forEach { event ->
                        EventBlock(
                            event = event,
                            onClick = { onOpen(event) },
                            onLongClick = { onLongPress(event) }
                        )
                    }
                }
            }
        }

        items24(
            dayMillis = dayMillis,
            timed = timed,
            isToday = isToday,
            nowHour = nowHour,
            now = now,
            onOpen = onOpen,
            onLongPress = onLongPress,
            onAddAt = onAddAt
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items24(
    dayMillis: Long,
    timed: List<CalendarEvent>,
    isToday: Boolean,
    nowHour: Int,
    now: Long,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    onAddAt: (Long) -> Unit
) {
    (0..23).forEach { hour ->
        item(key = "h_${dayMillis}_$hour") {
            HourRow(
                hour = hour,
                dayMillis = dayMillis,
                events = timed.filter { startHourOf(it) == hour },
                isNowHour = isToday && hour == nowHour,
                now = now,
                onOpen = onOpen,
                onLongPress = onLongPress,
                onAddAt = onAddAt
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HourRow(
    hour: Int,
    dayMillis: Long,
    events: List<CalendarEvent>,
    isNowHour: Boolean,
    now: Long,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    onAddAt: (Long) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hourStart = dayMillis + hour * 60L * 60 * 1000

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { },
                // An empty stretch of the day is where a new event most naturally starts.
                onLongClick = { onAddAt(hourStart) }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = String.format(Locale.ROOT, "%02d", hour),
            style = metroDigitStyle(fontSize = 13.sp, weight = FontWeight.Light, letterSpacing = 0.sp),
            color = if (isNowHour) zuneColors.accentColor else zuneColors.textDim,
            modifier = Modifier.width(30.dp).padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isNowHour) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(zuneColors.accentColor)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(zuneColors.textDim.copy(alpha = 0.22f))
                )
            }
            events.forEach { event ->
                EventBlock(
                    event = event,
                    onClick = { onOpen(event) },
                    onLongClick = { onLongPress(event) }
                )
            }
            Spacer(modifier = Modifier.height(if (events.isEmpty()) EMPTY_HOUR else 4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventBlock(
    event: CalendarEvent,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val colour = parseColor(event.colorHex, zuneColors.accentColor)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(colour)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = event.title.ifBlank { stringResource(R.string.cal_untitled_event) },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (event.isAllDay) {
                stringResource(R.string.cal_all_day)
            } else {
                "${formatTime(event.startMillis)} – ${formatTime(event.endMillis)}"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = Color.White.copy(alpha = 0.85f)
        )
        if (event.location.isNotBlank()) {
            Text(
                text = event.location,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun startHourOf(event: CalendarEvent): Int =
    Calendar.getInstance().apply { timeInMillis = event.startMillis }.get(Calendar.HOUR_OF_DAY)

/** How tall an hour with nothing in it is: enough to be a gap, not enough to be a scroll. */
private val EMPTY_HOUR = 14.dp

/** The date line and the all-day block sit above hour zero. */
private const val HEADER_ITEMS = 2

/** Where a day that is not today opens. */
private const val DEFAULT_SCROLL_HOUR = 7
