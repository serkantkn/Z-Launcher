package com.serkantkn.zunelauncher.ui.screens.clock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.UntilParts
import com.serkantkn.zunelauncher.util.untilParts
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import com.serkantkn.zunelauncher.ui.components.MetroBigNumber
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.MetroNotice
import com.serkantkn.zunelauncher.ui.components.MetroRule

/**
 * The alarms page.
 *
 * The line at the top is the one thing a person checks an alarm list for — not which alarms exist
 * but how long they have got — so it is said in words and in the accent colour, above everything
 * else. Underneath, each alarm states its own repeat in the same breath as its time, because an
 * alarm whose repeat you cannot see is an alarm you set twice.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlarmsPage(
    alarms: List<Alarm>,
    now: Long,
    canScheduleExact: Boolean,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm) -> Unit,
    onLongPress: (Alarm) -> Unit,
    onFixExactPermission: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val next = remember(alarms, now) {
        alarms.filter { it.isEnabled }
            .map { it to it.nextTriggerMillis(now) }
            .filter { it.second > now }
            .minByOrNull { it.second }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        if (!canScheduleExact) {
            item {
                MetroNotice(
                    message = stringResource(R.string.clock_exact_alarm_warning),
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = onFixExactPermission,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.clock_next_alarm).lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor
                )
                if (next == null) {
                    Text(
                        text = stringResource(R.string.clock_no_alarm_set),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    MetroBigNumber(
                        text = timeOf(next.second),
                        fontSize = 72.sp
                    )
                    Text(
                        text = untilText(untilParts(now, next.second)),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted
                    )
                }
            }
        }

        item {
            MetroRule(
                title = stringResource(R.string.clock_tab_alarms),
                actionLabel = stringResource(R.string.common_add),
                onAction = onAdd
            )
        }

        if (alarms.isEmpty()) {
            item {
                MetroEmpty(
                    message = stringResource(R.string.clock_no_alarms),
                    actionLabel = stringResource(R.string.clock_add_alarm),
                    onAction = onAdd
                )
            }
        }

        items(alarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                now = now,
                onClick = { onEdit(alarm) },
                onLongClick = { onLongPress(alarm) },
                onToggle = { onToggle(alarm) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val snoozedUntil = alarm.snoozedUntilMillis?.takeIf { it > now && alarm.isEnabled }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MetroBigNumber(
                text = alarm.timeString,
                fontSize = 44.sp,
                color = if (alarm.isEnabled) null else zuneColors.textMuted
            )
            Text(
                text = listOfNotNull(
                    alarm.label.takeIf { it.isNotBlank() },
                    repeatSummary(alarm)
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (snoozedUntil != null) {
                Text(
                    text = stringResource(R.string.clock_snoozed_until, timeOf(snoozedUntil)),
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.accentColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        com.serkantkn.zunelauncher.ui.screens.settings.WindowsPhoneSwitch(
            checked = alarm.isEnabled,
            onCheckedChange = { onToggle() }
        )
    }
}

// ── Words ───────────────────────────────────────────────────────────────────

/** "her gün" / "hafta içi" / "pzt, çar, cum" / "bir kez": what the alarm's repeat amounts to. */
@Composable
internal fun repeatSummary(alarm: Alarm): String {
    val days = alarm.daysOfWeek
    return when {
        days.isEmpty() -> stringResource(R.string.clock_repeat_once)
        days == Alarm.EVERY_DAY -> stringResource(R.string.clock_repeat_every_day)
        days == Alarm.WEEKDAYS -> stringResource(R.string.clock_repeat_weekdays)
        days == Alarm.WEEKEND -> stringResource(R.string.clock_repeat_weekend)
        else -> Alarm.weekOrder()
            .filter { it in days }
            .joinToString(", ") { shortDayName(it) }
    }
}

/** The three-letter form of a weekday, as the reader's own language abbreviates it. */
internal fun shortDayName(calendarDay: Int, locale: Locale = Locale.getDefault()): String =
    DateFormatSymbols.getInstance(locale).shortWeekdays.getOrNull(calendarDay)
        ?.lowercase(locale)
        .orEmpty()

/** How long until something, in the units it is worth saying. */
@Composable
internal fun untilText(parts: UntilParts): String = when {
    parts.isNow -> stringResource(R.string.clock_in_a_moment)
    parts.days > 0 -> stringResource(R.string.clock_in_days_hours, parts.days, parts.hours)
    parts.hours > 0 -> stringResource(R.string.clock_in_hours_minutes, parts.hours, parts.minutes)
    else -> stringResource(R.string.clock_in_minutes, parts.minutes)
}

/** A moment as a clock reading. */
internal fun timeOf(millis: Long, locale: Locale = Locale.getDefault()): String {
    val calendar = Calendar.getInstance(locale).apply { timeInMillis = millis }
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE)
    )
}
