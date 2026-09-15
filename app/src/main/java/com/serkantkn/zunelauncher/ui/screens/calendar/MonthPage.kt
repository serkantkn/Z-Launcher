package com.serkantkn.zunelauncher.ui.screens.calendar

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DAY_MS
import com.serkantkn.zunelauncher.util.eventsOn
import com.serkantkn.zunelauncher.util.isSameDay
import com.serkantkn.zunelauncher.util.monthGrid
import com.serkantkn.zunelauncher.util.startOfDay
import com.serkantkn.zunelauncher.util.weekOrder
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * The month, as a grid of squares.
 *
 * Two things were wrong with the old one beyond its rounded grey boxes. The leading blanks were
 * worked out with a formula that assumed the week starts on Monday, so the whole grid slid a day
 * sideways in any country whose week starts on Sunday; and the weekday letters were seven strings
 * typed out by hand rather than the ones the reader's own language uses.
 */
@Composable
internal fun MonthPage(
    monthMillis: Long,
    selectedDayMillis: Long,
    events: List<CalendarEvent>,
    weekStart: Int,
    now: Long,
    onSelectDay: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val grid = remember(monthMillis, weekStart) { monthGrid(monthMillis, weekStart) }
    val dayNames = remember(weekStart) { shortWeekdayNames(weekStart) }
    val selectedEvents = remember(events, selectedDayMillis) { eventsOn(events, selectedDayMillis) }
    val monthOfSelected = remember(monthMillis) {
        Calendar.getInstance().apply { timeInMillis = monthMillis }.get(Calendar.MONTH)
    }

    // The month is turned with the chevrons, not by swiping: a sideways swipe inside a hub belongs
    // to the pivot, and a page that takes it for itself means you can never leave that page.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chevron(text = "‹", onClick = onPreviousMonth)
                Text(
                    text = formatMonthYear(monthMillis),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Chevron(text = "›", onClick = onNextMonth)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dayNames.forEach { name ->
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
            Spacer(modifier = Modifier.padding(top = 4.dp))
        }

        // Six rows of seven; a LazyColumn cannot hold a grid, and a month is never longer than this.
        grid.chunked(7).forEachIndexed { rowIndex, week ->
            item(key = "week_${monthMillis}_$rowIndex") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(7) { column ->
                        val day = week.getOrNull(column)
                        if (day == null) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val dayEvents = remember(events, day) { eventsOn(events, day) }
                            val dayNumber = Calendar.getInstance()
                                .apply { timeInMillis = day }
                                .get(Calendar.DAY_OF_MONTH)
                            MonthDayCell(
                                dayNumber = dayNumber,
                                isToday = isSameDay(day, now),
                                isSelected = isSameDay(day, selectedDayMillis),
                                isOtherMonth = Calendar.getInstance()
                                    .apply { timeInMillis = day }
                                    .get(Calendar.MONTH) != monthOfSelected,
                                markColors = dayEvents.map { parseColor(it.colorHex, zuneColors.accentColor) },
                                onClick = { onSelectDay(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            DayHeading(dayMillis = selectedDayMillis, nowMillis = now, count = selectedEvents.size)
        }

        if (selectedEvents.isEmpty()) {
            item { MetroEmpty(message = stringResource(R.string.cal_no_events_day)) }
        }

        selectedEvents.forEach { event ->
            item(key = event.id) {
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
private fun Chevron(text: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .width(44.dp)
            .aspectRatio(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
            color = zuneColors.accentColor
        )
    }
}

/** The weekday letters the reader's own language uses, in the order that week runs. */
internal fun shortWeekdayNames(weekStart: Int, locale: Locale = Locale.getDefault()): List<String> {
    val symbols = DateFormatSymbols.getInstance(locale).shortWeekdays
    return weekOrder(weekStart).map { day ->
        symbols.getOrNull(day)?.take(2)?.lowercase(locale).orEmpty()
    }
}

/** The day belonging to a grid square, for callers that only have the square. */
internal fun dayOf(millis: Long): Long = startOfDay(millis)

/** One day, for arithmetic that reads better with a name. */
internal const val ONE_DAY = DAY_MS
