package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.DailyPoint
import com.serkantkn.zunelauncher.data.model.HourlyPoint
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.ui.screens.weather.conditionIcon
import com.serkantkn.zunelauncher.ui.screens.weather.formatAt
import com.serkantkn.zunelauncher.util.daysFrom
import com.serkantkn.zunelauncher.util.hoursFrom

/**
 * The weather tile: what it is doing outside, in the place the hub is set to.
 *
 * Every size leads with the temperature and the sky. The medium tile adds the day's high and low
 * and turns over to the next hours; the wide tile runs the next hours beside the reading and turns
 * over to the next days; the large tile has all of it at once - the reading with how it feels,
 * the hours in a strip and the coming days in a table. With nothing cached yet - the hub has never
 * been opened - it is the plain glyph face like any other hub tile.
 */
@Composable
fun W10MWeatherTile(
    snapshot: WeatherSnapshot?,
    temperatureUnit: TemperatureUnit,
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(HubType.WEATHER.titleRes)
    // The forecast was fetched a while ago: "today" and "the next hours" are the clock's, not
    // the fetch's. Keyed on the snapshot, so a day-old tile still reads today's figures.
    val nowSeconds = remember(snapshot?.fetchedAt) { System.currentTimeMillis() / 1000 }
    val days = remember(snapshot, nowSeconds) { snapshot?.daysFrom(nowSeconds).orEmpty() }
    val today = days.firstOrNull()
    val hours = remember(snapshot, nowSeconds) { snapshot?.hoursFrom(nowSeconds).orEmpty().drop(1) }
    val shape = TileShape.of(span)
    val degrees: (Double) -> String = { "${temperatureUnit.of(it)}°" }

    W10MTileSurface(
        liveKey = "hub:${HubType.WEATHER.name}",
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
        back = if (snapshot != null && today != null && shape != TileShape.LARGE) {
            {
                val fg = tileForegroundColor()
                TileFace(span) { m ->
                    when (m.shape) {
                        TileShape.SMALL -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(degrees(today.maxTemperature), style = MaterialTheme.typography.titleMedium.copy(fontSize = m.figure(18f), fontWeight = FontWeight.Light), color = fg, maxLines = 1)
                            Text(degrees(today.minTemperature), style = MaterialTheme.typography.titleMedium.copy(fontSize = m.figure(14f), fontWeight = FontWeight.Light), color = fg.copy(alpha = 0.75f), maxLines = 1)
                        }
                        TileShape.MEDIUM -> Column(modifier = Modifier.fillMaxSize().padding(m.pad)) {
                            TileCaption(null, snapshot.place.name, m, fg, Modifier.padding(bottom = m.dp(6f)))
                            HourStrip(hours.take(3), snapshot.utcOffsetSeconds, degrees, m, fg, Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.weight(1f))
                            DayTable(days.drop(1).take(2), snapshot.utcOffsetSeconds, degrees, m, fg, short = true)
                        }
                        else -> Column(modifier = Modifier.fillMaxSize().padding(m.pad)) {
                            TileCaption(null, snapshot.place.name, m, fg, Modifier.padding(bottom = m.dp(6f)))
                            DayStrip(days.take(5), snapshot.utcOffsetSeconds, degrees, m, fg, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        } else null,
        front = {
            val fg = tileForegroundColor()
            if (snapshot == null) {
                HubGlyphFace(icon = Icons.Default.WbSunny, title = title, badgeCount = 0, span = span, gridColumns = gridColumns)
                return@W10MTileSurface
            }
            val now = snapshot.now
            val sky = conditionIcon(now.condition, now.isDay)
            val condition = stringResource(now.condition.labelRes)
            val highLow = today?.let { stringResource(R.string.weather_high_low, degrees(it.maxTemperature), degrees(it.minTemperature)) }

            TileFace(span) { m ->
                when (m.shape) {
                    TileShape.SMALL -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(sky, contentDescription = condition, tint = fg, modifier = Modifier.size(m.dp(30f)))
                        Text(degrees(now.temperature), style = MaterialTheme.typography.titleMedium.copy(fontSize = m.figure(19f), fontWeight = FontWeight.Light), color = fg, maxLines = 1)
                    }
                    TileShape.MEDIUM -> TileBody(m) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Reading(degrees(now.temperature), m.figure(50f), fg)
                                Icon(sky, contentDescription = null, tint = fg, modifier = Modifier.padding(start = m.dp(8f)).size(m.dp(34f)))
                            }
                            Text(condition, style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (highLow != null) {
                                Text(highLow, style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(14f)), color = fg.copy(alpha = 0.8f), maxLines = 1)
                            }
                        }
                    }
                    TileShape.WIDE -> TileBody(m) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.padding(end = m.dp(14f))) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Reading(degrees(now.temperature), m.figure(58f), fg)
                                    Icon(sky, contentDescription = null, tint = fg, modifier = Modifier.padding(start = m.dp(8f)).size(m.dp(38f)))
                                }
                                Text(condition, style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (highLow != null) {
                                    Text(highLow, style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(14f)), color = fg.copy(alpha = 0.8f), maxLines = 1)
                                }
                            }
                            // As many hours as the width leaves room for: a wide tile on the
                            // eight-column grid is half as wide as one on the four-column grid.
                            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                                val slots = (maxWidth / m.dp(44f)).toInt().coerceIn(0, 5)
                                if (slots > 0) HourStrip(hours.take(slots), snapshot.utcOffsetSeconds, degrees, m, fg, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    TileShape.LARGE -> TileBody(m) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Reading(degrees(now.temperature), m.figure(88f), fg)
                                Icon(sky, contentDescription = null, tint = fg, modifier = Modifier.padding(start = m.dp(12f)).size(m.dp(64f)))
                            }
                            Text(condition, style = MaterialTheme.typography.headlineSmall.copy(fontSize = m.text(22f), fontWeight = FontWeight.Light), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = listOfNotNull(highLow, stringResource(R.string.weather_feels_like, degrees(now.apparent))).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                color = fg.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            HourStrip(hours.take(6), snapshot.utcOffsetSeconds, degrees, m, fg, Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.weight(1f))
                            DayTable(days.drop(1).take(4), snapshot.utcOffsetSeconds, degrees, m, fg)
                        }
                    }
                }
            }
            TileLabel(snapshot.place.name.ifBlank { title }, span, gridColumns, fg)
        }
    )
}

/** The temperature, in the light figures the Windows Phone weather tile used. */
@Composable
private fun Reading(text: String, size: TextUnit, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall.copy(fontSize = size, lineHeight = size * 1.05f, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        color = color,
        maxLines = 1,
        softWrap = false
    )
}

/** The next hours side by side: the hour, the sky, the temperature. */
@Composable
private fun HourStrip(
    hours: List<HourlyPoint>,
    offsetSeconds: Int,
    degrees: (Double) -> String,
    m: TileMetrics,
    ink: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        hours.forEach { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatAt(hour.timeSeconds, offsetSeconds, "HH"), style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(12f)), color = ink.copy(alpha = 0.75f), maxLines = 1)
                Icon(conditionIcon(hour.condition, hour.isDay), contentDescription = null, tint = ink.copy(alpha = 0.9f),
                    modifier = Modifier.padding(vertical = m.dp(4f)).size(m.dp(20f)))
                Text(degrees(hour.temperature), style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(14f)), color = ink, maxLines = 1)
            }
        }
    }
}

/** The coming days side by side, for the back of the wide tile. */
@Composable
private fun DayStrip(
    days: List<DailyPoint>,
    offsetSeconds: Int,
    degrees: (Double) -> String,
    m: TileMetrics,
    ink: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatAt(day.timeSeconds, offsetSeconds, "EEE").lowercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(12f)), color = ink.copy(alpha = 0.75f), maxLines = 1)
                Icon(conditionIcon(day.condition, true), contentDescription = null, tint = ink.copy(alpha = 0.9f),
                    modifier = Modifier.padding(vertical = m.dp(4f)).size(m.dp(22f)))
                Text(degrees(day.maxTemperature), style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(14f)), color = ink, maxLines = 1)
                Text(degrees(day.minTemperature), style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(12f)), color = ink.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

/** The coming days one under another, for the large tile: the day, the sky, high and low. */
@Composable
private fun DayTable(
    days: List<DailyPoint>,
    offsetSeconds: Int,
    degrees: (Double) -> String,
    m: TileMetrics,
    ink: Color,
    /** Three-letter day names, for the narrow medium tile. */
    short: Boolean = false
) {
    // As many days as there is room for: the large tile on the eight-column grid has fewer.
    FitColumn(spacing = m.dp(5f)) {
        days.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(formatAt(day.timeSeconds, offsetSeconds, if (short) "EEE" else "EEEE").lowercase(), style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)), color = ink, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(conditionIcon(day.condition, true), contentDescription = null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.padding(horizontal = m.dp(10f)).size(m.dp(20f)))
                Text(degrees(day.maxTemperature), style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)), color = ink, maxLines = 1, modifier = Modifier.width(m.dp(40f)))
                Text(degrees(day.minTemperature), style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)), color = ink.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.width(m.dp(36f)))
            }
        }
    }
}
