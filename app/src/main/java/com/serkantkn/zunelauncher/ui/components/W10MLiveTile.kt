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
import com.serkantkn.zunelauncher.ui.theme.LocalScreenAwake
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

/**
 * Emits the current time once a minute, aligned to the minute boundary. Behind an opaque hub it
 * stops ticking — nothing it feeds can be seen — and catches up the moment the screen is back.
 */
@Composable
fun rememberMinuteTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val awake = LocalScreenAwake.current
    LaunchedEffect(awake) {
        if (!awake) return@LaunchedEffect
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
 * "saat" live tile: the time in light Segoe-style type and today's date, with the alarms that are
 * set. The small and medium tiles turn over to the next alarm; the wide tile lists the next two
 * beside the time and the large one the next three under it.
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
    val coming = remember(alarms, now) {
        alarms.map { it to it.nextTriggerMillis(now) }.filter { it.second > now }.sortedBy { it.second }
    }
    val nextAlarm = coming.firstOrNull()
    val timeText = format(timeFormat, now)
    val timePattern24 = timeFormat.contains("HH") || timeFormat.contains("H:")
    val alarmTimeOf: (Long) -> String = { format(if (timePattern24) "HH:mm" else "h:mm a", it) }
    val shape = TileShape.of(span)
    val alarmLines = coming.map { (alarm, millis) ->
        TileLine(
            title = alarmTimeOf(millis),
            body = alarm.label.takeIf { it.isNotBlank() },
            meta = relativeDay(millis, now)
        )
    }

    W10MLiveTileFrame(
        liveKey = "hub:${HubType.CLOCK.name}",
        span = span, gridColumns = gridColumns, spacing = spacing,
        isEditing = isEditing, isDragging = isDragging, cornerStyle = cornerStyle,
        // The wide and large tiles already list the alarms on their front.
        flipEnabled = nextAlarm != null && (shape == TileShape.SMALL || shape == TileShape.MEDIUM),
        onClick = onClick, onLongClick = onLongClick, onRemoveClick = onRemoveClick, onResizeClick = onResizeClick,
        modifier = modifier,
        front = {
            TileFace(span) { m ->
                when (m.shape) {
                    TileShape.SMALL -> {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = m.text(21f), fontWeight = FontWeight.Light),
                            color = fg,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        if (nextAlarm != null) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = fg.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(m.dp(6f)).size(m.dp(12f)))
                        }
                    }
                    TileShape.MEDIUM -> TileBody(m) {
                        Column {
                            BigNumber(timeText, m.text(46f), fg)
                            Text(
                                text = format("EEEE", now).lowercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                color = fg, maxLines = 1
                            )
                            Text(
                                text = format("d MMMM", now).lowercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                color = fg.copy(alpha = 0.8f), maxLines = 1
                            )
                        }
                        if (nextAlarm != null) {
                            AlarmMark(alarmTimeOf(nextAlarm.second), m, fg, Modifier.align(Alignment.BottomEnd).padding(bottom = m.dp(2f)))
                        }
                    }
                    TileShape.WIDE -> TileBody(m) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                BigNumber(timeText, m.text(64f), fg)
                                Text(
                                    text = format("EEEE, d MMMM", now).lowercase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                    color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (alarmLines.isNotEmpty()) {
                                Column(modifier = Modifier.weight(0.8f).padding(start = m.dp(12f))) {
                                    TileCaption(Icons.Default.Alarm, stringResource(R.string.tile_next_alarm), m, fg,
                                        Modifier.padding(bottom = m.dp(6f)))
                                    TileLineList(alarmLines, m, fg, maxItems = 2)
                                }
                            }
                        }
                    }
                    TileShape.LARGE -> TileBody(m) {
                        // With no alarm to list, the time and the day stand in the middle of the
                        // tile rather than at the top of an empty one.
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = if (alarmLines.isEmpty()) Arrangement.Center else Arrangement.Top
                        ) {
                            BigNumber(timeText, m.text(104f), fg)
                            Text(
                                text = format("EEEE", now).lowercase(),
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = m.text(26f), fontWeight = FontWeight.Light),
                                color = fg, maxLines = 1
                            )
                            Text(
                                text = format("d MMMM yyyy", now).lowercase(),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = m.text(18f)),
                                color = fg.copy(alpha = 0.8f), maxLines = 1
                            )
                            if (alarmLines.isNotEmpty()) {
                                Spacer(modifier = Modifier.weight(1f))
                                TileCaption(Icons.Default.Alarm, stringResource(R.string.tile_alarms), m, fg,
                                    Modifier.padding(bottom = m.dp(8f)))
                                TileLineList(alarmLines, m, fg, maxItems = 3)
                            }
                        }
                    }
                }
            }
            TileLabel(stringResource(HubType.CLOCK.titleRes), span, gridColumns, fg)
        },
        back = {
            val alarm = nextAlarm ?: return@W10MLiveTileFrame
            TileFace(span) { m ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(m.pad),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    if (m.isSmall) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = fg, modifier = Modifier.size(m.dp(16f)))
                    } else {
                        TileCaption(Icons.Default.Alarm, stringResource(R.string.tile_next_alarm), m, fg)
                    }
                    Column {
                        Text(
                            text = alarmTimeOf(alarm.second),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = if (m.isSmall) m.text(19f) else m.text(40f),
                                fontWeight = FontWeight.Light
                            ),
                            color = fg,
                            maxLines = 1
                        )
                        if (!m.isSmall) {
                            val detail = buildString {
                                append(relativeDay(alarm.second, now))
                                val label = alarm.first.label
                                if (label.isNotBlank()) append(" · ").append(label)
                            }
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = m.text(14f)),
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

/** A time or a day of the month, in the light, tight type Windows Phone set its numbers in. */
@Composable
private fun BigNumber(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = size,
            lineHeight = size * 1.05f,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/** The bell and the time of the next alarm, in the corner of the medium tile. */
@Composable
private fun AlarmMark(time: String, m: TileMetrics, ink: Color, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.dp(4f)),
        modifier = modifier
    ) {
        Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.tile_next_alarm), tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(m.dp(14f)))
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(13f), fontWeight = FontWeight.Medium),
            color = ink.copy(alpha = 0.9f)
        )
    }
}

// ════════════════════════════════════════════════════════════
// CALENDAR
// ════════════════════════════════════════════════════════════

/**
 * "takvim" live tile: the day and its big number, as the Windows Phone calendar tile had them,
 * with what is coming. The medium tile names the next appointment and turns over to the ones
 * after it; the wide tile shows two beside the date and the large one is the week's agenda.
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
    val shape = TileShape.of(span)

    // Upcoming first (soonest on top); today's already-passed events trail behind them.
    val upcoming = remember(events, now) {
        events.map { it to it.startMillis }
            .filter { it.second >= now - 60L * 60 * 1000 || isSameDay(it.second, now) }
            .sortedWith(compareBy({ it.second < now - 60L * 60 * 1000 }, { it.second }))
    }
    val next = upcoming.firstOrNull()
    val allDay = stringResource(R.string.tile_all_day)
    val lines = upcoming.map { (event, millis) ->
        TileLine(
            title = event.title,
            body = event.location.takeIf { it.isNotBlank() },
            meta = relativeDay(millis, now) + " " + if (event.isAllDay) allDay else event.formattedTime
        )
    }
    // What the front already shows, so the back starts after it.
    val onFront = when (shape) { TileShape.SMALL -> 0; TileShape.MEDIUM -> 1; TileShape.WIDE -> 2; TileShape.LARGE -> 5 }

    W10MLiveTileFrame(
        liveKey = "hub:${HubType.CALENDAR.name}",
        span = span, gridColumns = gridColumns, spacing = spacing,
        isEditing = isEditing, isDragging = isDragging, cornerStyle = cornerStyle,
        flipEnabled = shape != TileShape.LARGE && lines.size > onFront && (shape != TileShape.SMALL || next != null),
        onClick = onClick, onLongClick = onLongClick, onRemoveClick = onRemoveClick, onResizeClick = onResizeClick,
        modifier = modifier,
        front = {
            TileFace(span) { m ->
                when (m.shape) {
                    TileShape.SMALL -> {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = format("EEE", now).lowercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(11f)),
                                color = fg.copy(alpha = 0.85f), maxLines = 1
                            )
                            BigNumber(format("d", now), m.text(32f), fg)
                        }
                        if (next != null) {
                            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(m.dp(7f)).size(m.dp(6f)).clip(CircleShape).background(fg))
                        }
                    }
                    TileShape.MEDIUM -> TileBody(m) {
                        if (next != null) {
                            Column(modifier = Modifier.align(Alignment.TopStart)) {
                                Text(
                                    text = next.first.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(16f), fontWeight = FontWeight.SemiBold, lineHeight = m.text(19f)),
                                    color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = lines.first().meta.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = m.text(13f)),
                                    color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                lines.first().body?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = m.text(13f)),
                                        color = fg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Text(
                                text = format("EEEE", now).lowercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                color = fg, maxLines = 1, modifier = Modifier.align(Alignment.TopStart)
                            )
                        }
                        // The day's number in the corner the name leaves free, as on Windows Phone.
                        BigNumber(format("d", now), m.text(if (next != null) 34f else 56f), fg, Modifier.align(Alignment.BottomEnd))
                    }
                    TileShape.WIDE -> TileBody(m) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.padding(end = m.dp(16f))) {
                                Text(
                                    text = format("EEEE", now).lowercase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                    color = fg, maxLines = 1
                                )
                                BigNumber(format("d", now), m.text(72f), fg)
                                Text(
                                    text = format("MMMM", now).lowercase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(14f)),
                                    color = fg.copy(alpha = 0.8f), maxLines = 1
                                )
                            }
                            if (lines.isNotEmpty()) {
                                TileLineList(lines, m, fg, maxItems = 2, bodyLines = 1, modifier = Modifier.weight(1f).padding(top = m.dp(4f)))
                            } else {
                                NoEvents(m, fg, Modifier.weight(1f).padding(top = m.dp(4f)))
                            }
                        }
                    }
                    TileShape.LARGE -> TileBody(m) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                BigNumber(format("d", now), m.text(80f), fg)
                                Column(modifier = Modifier.padding(start = m.dp(12f), bottom = m.dp(12f))) {
                                    Text(
                                        text = format("EEEE", now).lowercase(),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = m.text(24f), fontWeight = FontWeight.Light),
                                        color = fg, maxLines = 1
                                    )
                                    Text(
                                        text = format("MMMM yyyy", now).lowercase(),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = m.text(16f)),
                                        color = fg.copy(alpha = 0.8f), maxLines = 1
                                    )
                                }
                            }
                            if (lines.isNotEmpty()) {
                                TileLineList(lines, m, fg, maxItems = 5, bodyLines = 1, modifier = Modifier.padding(top = m.dp(14f)))
                            } else {
                                NoEvents(m, fg, Modifier.padding(top = m.dp(14f)))
                            }
                        }
                    }
                }
            }
            TileLabel(stringResource(HubType.CALENDAR.titleRes), span, gridColumns, fg)
        },
        back = {
            TileFace(span) { m ->
                if (m.isSmall) {
                    val first = lines.firstOrNull() ?: return@TileFace
                    Column(modifier = Modifier.fillMaxSize().padding(m.dp(7f)), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(first.title, style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(11f), fontWeight = FontWeight.SemiBold, lineHeight = m.text(13f)),
                            color = fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(upcoming.first().first.formattedTime, style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(11f)),
                            color = fg.copy(alpha = 0.85f), maxLines = 1)
                    }
                    return@TileFace
                }
                Column(modifier = Modifier.fillMaxSize().padding(m.pad)) {
                    TileCaption(null, stringResource(R.string.tile_upcoming), m, fg, Modifier.padding(bottom = m.dp(8f)))
                    TileLineList(lines.drop(onFront), m, fg, maxItems = if (m.shape == TileShape.WIDE) 2 else 3)
                }
            }
        }
    )
}

/** "Nothing planned", where an agenda would be. */
@Composable
private fun NoEvents(m: TileMetrics, ink: Color, modifier: Modifier) {
    Text(
        text = stringResource(R.string.tile_no_events),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
        color = ink.copy(alpha = 0.7f),
        maxLines = 2,
        modifier = modifier
    )
}
