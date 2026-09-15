package com.serkantkn.zunelauncher.ui.screens.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.ui.components.metroDigitStyle
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DayLabel
import com.serkantkn.zunelauncher.util.dayLabelOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The pieces the Calendar hub is drawn from.
 *
 * The old hub drew every event as a rounded grey card and every day of the month as a rounded grey
 * box with a dot in it. Metro has neither: an event is a line of type with a coloured bar down its
 * left, and a day is a square. The colour of the bar is the calendar the event came from, which is
 * the only way a list holding four calendars can say which is which without writing it out.
 */

// ── One event ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EventRow(
    event: CalendarEvent,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val accent = remember(event.colorHex) { parseColor(event.colorHex, zuneColors.accentColor) }

    val timeText = when {
        event.isAllDay -> stringResource(R.string.cal_all_day)
        else -> "${formatTime(event.startMillis)} – ${formatTime(event.endMillis)}"
    }
    val dateText = if (showDate) formatDate(event.startMillis) else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title.ifBlank { stringResource(R.string.cal_untitled_event) },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(dateText, timeText).joinToString(" • "),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = zuneColors.textMuted
            )
            val detail = listOfNotNull(
                event.location.takeIf { it.isNotBlank() },
                event.description.takeIf { it.isNotBlank() }
            ).firstOrNull()
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (event.isRepeating || event.reminderMinutes != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (event.isRepeating) {
                    Text(
                        text = "↻",
                        style = MaterialTheme.typography.labelMedium,
                        color = zuneColors.textDim
                    )
                }
                if (event.reminderMinutes != null) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = accent
                    )
                }
            }
        }
    }
}

// ── A day's heading in the agenda ───────────────────────────────────────────

/** "bugün", "yarın", or the date — with a hairline running off to the edge. */
@Composable
internal fun DayHeading(dayMillis: Long, nowMillis: Long, count: Int) {
    val zuneColors = LocalZuneColors.current
    val label = when (dayLabelOf(dayMillis, nowMillis)) {
        DayLabel.TODAY -> stringResource(R.string.cal_today)
        DayLabel.TOMORROW -> stringResource(R.string.cal_tomorrow)
        DayLabel.YESTERDAY -> stringResource(R.string.cal_yesterday)
        DayLabel.OTHER -> formatDate(dayMillis)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 6.dp)
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = zuneColors.accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = zuneColors.textDim
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(zuneColors.accentColor.copy(alpha = 0.35f))
        )
    }
}

// ── One square of the month ─────────────────────────────────────────────────

/**
 * A day in the month grid.
 *
 * Flat, square, and marked with a line rather than a dot — and the line takes the colour of the
 * first event on that day, so a glance at the month says not only which days are busy but roughly
 * with what.
 */
@Composable
internal fun MonthDayCell(
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isOtherMonth: Boolean,
    markColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .wpTilt(interactionSource, maxDegrees = 6f)
            .then(
                when {
                    isSelected -> Modifier.background(zuneColors.accentColor)
                    isToday -> Modifier.border(2.dp, zuneColors.accentColor)
                    else -> Modifier.background(
                        if (zuneColors.isDark) Color.White.copy(alpha = 0.06f)
                        else Color.Black.copy(alpha = 0.05f)
                    )
                }
            )
            .combinedClickableCell(interactionSource, onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNumber.toString(),
                style = metroDigitStyle(
                    fontSize = 16.sp,
                    weight = if (isToday || isSelected) FontWeight.Normal else FontWeight.Light,
                    letterSpacing = 0.sp
                ),
                color = when {
                    isSelected -> Color.White
                    isOtherMonth -> zuneColors.textDim
                    else -> MaterialTheme.colorScheme.onBackground
                },
                textAlign = TextAlign.Center
            )
            if (markColors.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                ) {
                    markColors.take(MAX_MARKS).forEach { colour ->
                        Box(
                            modifier = Modifier
                                .size(width = 6.dp, height = 2.dp)
                                .background(if (isSelected) Color.White else colour)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCell(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick
)

// ── Words and colours ───────────────────────────────────────────────────────

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM EEEE", Locale.getDefault()).format(Date(millis))

/** The month and year, in Metro's own lower case and the reader's own language. */
internal fun formatMonthYear(millis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        .format(Date(millis))
        .lowercase(Locale.getDefault())

/** A hex colour from a calendar, falling back when it is missing or malformed. */
internal fun parseColor(hex: String?, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)

/** More marks than this in one square is a smudge, not information. */
private const val MAX_MARKS = 3
