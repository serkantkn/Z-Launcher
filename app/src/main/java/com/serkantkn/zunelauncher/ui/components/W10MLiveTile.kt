package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════
// FRAME
// ════════════════════════════════════════════════════════════

/**
 * Chrome shared by the custom live tiles: it is [W10MTileSurface] with the live face wired to
 * [flipEnabled], so a tile with nothing to report simply stands still.
 */
@Composable
fun W10MLiveTileFrame(
    liveKey: String,
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    flipEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit
) {
    W10MTileSurface(
        liveKey = liveKey,
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        highlighted = isMergeTarget,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        back = if (flipEnabled) back else null,
        front = front
    )
}

/** Emits the current time once a minute, aligned to the minute boundary. */
@Composable
private fun rememberMinuteTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    return now
}

private fun format(pattern: String, millis: Long): String =
    runCatching { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)) }.getOrDefault("")

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** "bugün", "yarın" or the weekday name for [millis] relative to [now]. */
@Composable
private fun relativeDay(millis: Long, now: Long): String = when {
    isSameDay(millis, now) -> stringResource(R.string.common_today)
    isSameDay(millis, now + 24L * 60 * 60 * 1000) -> stringResource(R.string.notes_tomorrow)
    millis - now < 6L * 24 * 60 * 60 * 1000 -> format("EEEE", millis).lowercase()
    else -> format("d MMM", millis).lowercase()
}

// ════════════════════════════════════════════════════════════
// CLOCK
// ════════════════════════════════════════════════════════════

/**
 * "saat" live tile: the current time in light Segoe-style type, today's date, and the next
 * alarm (icon + time) in the corner. Flips to the alarm details while an alarm is set.
 */
@Composable
fun W10MClockTile(
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    timeFormat: String,
    alarms: List<Alarm>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fg = tileForegroundColor()
    val now = rememberMinuteTicker()
    val nextAlarm = remember(alarms, now) {
        alarms.map { it to it.nextTriggerMillis(now) }.filter { it.second > now }.minByOrNull { it.second }
    }
    val timeText = format(timeFormat, now)
    val compact = isCompactTile(span, gridColumns)
    val timePattern24 = timeFormat.contains("HH") || timeFormat.contains("H:")
    val alarmTimeOf: (Long) -> String = { format(if (timePattern24) "HH:mm" else "h:mm a", it) }

    W10MLiveTileFrame(
        liveKey = "hub:${HubType.CLOCK.name}",
        span = span, gridColumns = gridColumns, spacing = spacing,
        isEditing = isEditing, isDragging = isDragging, cornerStyle = cornerStyle,
        flipEnabled = nextAlarm != null,
        onClick = onClick, onLongClick = onLongClick, onRemoveClick = onRemoveClick, onResizeClick = onResizeClick,
        modifier = modifier,
        front = {
            if (span == 1) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = fg,
                    modifier = Modifier.align(Alignment.Center)
                )
                if (nextAlarm != null) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(10.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = if (span >= 4) 10.dp else 8.dp, end = 8.dp)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = when {
                                span >= 4 -> if (compact) 30.sp else 40.sp
                                compact -> 22.sp
                                else -> 30.sp
                            },
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp
                        ),
                        color = fg,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = format(if (span >= 4) "EEEE, d MMMM" else "EEE, d MMM", now).lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Normal),
                        color = fg.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (nextAlarm != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 5.dp)
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.tile_next_alarm), tint = fg.copy(alpha = 0.9f), modifier = Modifier.size(if (compact) 10.dp else 13.dp))
                        Text(
                            text = alarmTimeOf(nextAlarm.second),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Medium),
                            color = fg.copy(alpha = 0.9f)
                        )
                    }
                }
                TileLabel(stringResource(HubType.CLOCK.titleRes), span, gridColumns, fg)
            }
        },
        back = {
            val alarm = nextAlarm
            if (alarm != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = fg, modifier = Modifier.size(if (span == 1) 12.dp else 16.dp))
                        if (span > 1) {
                            Text(
                                text = stringResource(R.string.tile_next_alarm),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = fg.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = alarmTimeOf(alarm.second),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = when { span == 1 -> 13.sp; compact -> 20.sp; else -> 26.sp },
                                fontWeight = FontWeight.Light
                            ),
                            color = fg,
                            maxLines = 1
                        )
                        if (span > 1) {
                            val detail = buildString {
                                append(relativeDay(alarm.second, now))
                                val label = alarm.first.label
                                if (label.isNotBlank()) append(" · ").append(label)
                            }
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 9.sp else 11.sp),
                                color = fg.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}

// ════════════════════════════════════════════════════════════
// CALENDAR
// ════════════════════════════════════════════════════════════

/**
 * "takvim" live tile: weekday and big day number like the Windows Phone calendar tile, with the
 * next appointment underneath. Flips to the following appointments while any exist.
 */
@Composable
fun W10MCalendarTile(
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fg = tileForegroundColor()
    val now = rememberMinuteTicker()
    val compact = isCompactTile(span, gridColumns)

    // An event knows exactly when it starts now; it used to carry a date and a time separately.
    fun eventMillis(e: CalendarEvent): Long = e.startMillis

    // Upcoming first (soonest on top); today's already-passed events trail behind them.
    val upcoming = remember(events, now) {
        events.map { it to eventMillis(it) }
            .filter { it.second >= now - 60L * 60 * 1000 || isSameDay(it.second, now) }
            .sortedWith(compareBy({ it.second < now - 60L * 60 * 1000 }, { it.second }))
    }
    val next = upcoming.firstOrNull()

    W10MLiveTileFrame(
        liveKey = "hub:${HubType.CALENDAR.name}",
        span = span, gridColumns = gridColumns, spacing = spacing,
        isEditing = isEditing, isDragging = isDragging, cornerStyle = cornerStyle,
        flipEnabled = upcoming.size > 1,
        onClick = onClick, onLongClick = onLongClick, onRemoveClick = onRemoveClick, onResizeClick = onResizeClick,
        modifier = modifier,
        front = {
            if (span == 1) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = format("d", now),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Light),
                        color = fg
                    )
                }
                if (next != null) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(5.dp).clip(CircleShape).background(fg))
                }
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 18.dp)) {
                    // Date block (left)
                    Column {
                        Text(
                            text = format("EEEE", now).lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 11.sp),
                            color = fg.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                        Text(
                            text = format("d", now),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = when { span >= 4 -> 40.sp; compact -> 26.sp; else -> 34.sp },
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp
                            ),
                            color = fg,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(if (span >= 4) 14.dp else 8.dp))
                    // Next appointment (right / below)
                    Column(
                        modifier = Modifier.weight(1f).padding(top = if (span >= 4) 4.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (next != null) {
                            Text(
                                text = next.first.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold),
                                color = fg,
                                maxLines = if (span >= 4) 2 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = relativeDay(next.second, now) + " " + next.first.formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 10.sp),
                                color = fg.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.tile_no_events),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 10.sp),
                                color = fg.copy(alpha = 0.6f),
                                maxLines = 2
                            )
                        }
                    }
                }
                TileLabel(stringResource(HubType.CALENDAR.titleRes), span, gridColumns, fg)
            }
        },
        back = {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
            ) {
                if (span > 1) {
                    Text(
                        text = stringResource(R.string.tile_upcoming),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = fg.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                upcoming.drop(1).take(if (span >= 4) 3 else if (compact) 2 else 3).forEach { (event, millis) ->
                    Column {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.SemiBold),
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = relativeDay(millis, now) + " " + event.formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 8.sp else 9.sp),
                            color = fg.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    )
}
