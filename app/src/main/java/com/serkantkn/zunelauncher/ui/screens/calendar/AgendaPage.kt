package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.MetroBigNumber
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DayLabel
import com.serkantkn.zunelauncher.util.agendaDays
import com.serkantkn.zunelauncher.util.dayLabelOf
import com.serkantkn.zunelauncher.util.untilParts
import java.util.Locale

/**
 * The agenda: what is coming, in the order it is coming.
 *
 * It opens with the next thing and how long there is until it, because that is the question the
 * page is for. Underneath, the days run in order with their own headings — "bugün", "yarın", then
 * dates — which is how a diary reads and what the old flat list of cards could not say.
 *
 * What has already happened is gone from here unless it is asked for in Settings. The old agenda
 * was headed "yaklaşan etkinlikler" and in fact listed everything ever entered, oldest first, so
 * the top of the page was always the least useful part of it.
 */
@Composable
internal fun AgendaPage(
    events: List<CalendarEvent>,
    now: Long,
    showPast: Boolean,
    onOpen: (CalendarEvent) -> Unit,
    onLongPress: (CalendarEvent) -> Unit,
    onAdd: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val days = remember(events, now, showPast) { agendaDays(events, now, showPast) }
    val next = remember(events, now) {
        events.filter { it.endMillis > now }.minByOrNull { it.startMillis }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.cal_next_event).lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor
                )
                if (next == null) {
                    Text(
                        text = stringResource(R.string.cal_nothing_ahead),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    MetroBigNumber(
                        text = if (next.isAllDay) {
                            stringResource(R.string.cal_all_day)
                        } else {
                            formatTime(next.startMillis)
                        },
                        fontSize = 62.sp
                    )
                    Text(
                        text = next.title.ifBlank { stringResource(R.string.cal_untitled_event) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Text(
                        text = countdownText(next, now),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted
                    )
                }
            }
        }

        if (days.isEmpty()) {
            item {
                MetroEmpty(
                    message = stringResource(R.string.cal_no_upcoming),
                    actionLabel = stringResource(R.string.cal_add_event),
                    onAction = onAdd,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        days.forEach { (day, dayEvents) ->
            item(key = "day_$day") {
                DayHeading(dayMillis = day, nowMillis = now, count = dayEvents.size)
            }
            dayEvents.forEach { event ->
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
}

/** "3 saat 20 dakika sonra", or which day it is on when it is further off than that. */
@Composable
private fun countdownText(event: CalendarEvent, now: Long): String {
    if (event.startMillis <= now) return stringResource(R.string.cal_happening_now)
    val parts = untilParts(now, event.startMillis)
    return when {
        parts.days > 0 -> when (dayLabelOf(event.startMillis, now)) {
            DayLabel.TOMORROW -> stringResource(R.string.cal_tomorrow)
            else -> stringResource(R.string.clock_in_days_hours, parts.days, parts.hours)
        }
        parts.hours > 0 -> stringResource(R.string.clock_in_hours_minutes, parts.hours, parts.minutes)
        else -> stringResource(R.string.clock_in_minutes, parts.minutes)
    }
}
