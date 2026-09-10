package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.ui.screens.weather.conditionIcon
import com.serkantkn.zunelauncher.ui.screens.weather.formatAt

/**
 * The weather tile: what it is doing outside, in the place the hub is set to.
 *
 * The front carries the temperature and the sky; the wide tile also runs the next few hours along
 * the bottom. It turns over to the day's high and low. With nothing cached yet — the hub has never
 * been opened — it falls back to the plain glyph face like any other hub tile.
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
    cornerStyle: TileCornerStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(HubType.WEATHER.titleRes)
    val today = snapshot?.daily?.firstOrNull()

    W10MTileSurface(
        liveKey = "hub:${HubType.WEATHER.name}",
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        back = if (snapshot != null && today != null) {
            {
                val fg = tileForegroundColor()
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = snapshot.place.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = fg.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column {
                        Text(
                            text = stringResource(
                                R.string.weather_high_low,
                                "${temperatureUnit.of(today.maxTemperature)}°",
                                "${temperatureUnit.of(today.minTemperature)}°"
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isCompactTile(span, gridColumns)) 16.sp else 22.sp,
                                fontWeight = FontWeight.Light
                            ),
                            color = fg,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(snapshot.now.condition.labelRes),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (isCompactTile(span, gridColumns)) 9.sp else 11.sp
                            ),
                            color = fg.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else null,
        front = {
            val fg = tileForegroundColor()
            if (snapshot == null) {
                HubGlyphFace(
                    icon = Icons.Default.WbSunny,
                    title = title,
                    badgeCount = 0,
                    span = span,
                    gridColumns = gridColumns
                )
                return@W10MTileSurface
            }

            val compact = isCompactTile(span, gridColumns)
            // How many hours fit is a question of width, not of tile size: a wide tile on the
            // eight-column grid is half as wide as one on the four-column grid, and a fixed strip
            // would climb straight over the temperature.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // A wide tile on the eight-column grid is half the width of one on the four-column
                // grid, so the reading is sized by how much room there actually is.
                val roomForBigReading = maxWidth >= 260.dp
                val readingWidth = if (roomForBigReading) 104.dp else 76.dp
                val hourSlots = ((maxWidth - readingWidth - 16.dp) / HOUR_COLUMN_WIDTH)
                    .toInt()
                    .coerceIn(0, 3)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${temperatureUnit.of(snapshot.now.temperature)}°",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = when {
                                        span == 1 -> 16.sp
                                        !roomForBigReading -> 26.sp
                                        else -> 40.sp
                                    },
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = (-1).sp
                                ),
                                color = fg,
                                maxLines = 1
                            )
                            if (span > 1) {
                                Icon(
                                    imageVector = conditionIcon(snapshot.now.condition, snapshot.now.isDay),
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(if (roomForBigReading) 24.dp else 16.dp)
                                )
                            }
                        }
                        if (span > 1) {
                            Text(
                                text = stringResource(snapshot.now.condition.labelRes),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 11.sp),
                                color = fg.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (span >= 4 && hourSlots > 0 && snapshot.hourly.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            snapshot.hourly.drop(1).take(hourSlots).forEach { hour ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(HOUR_COLUMN_WIDTH)
                                ) {
                                    Text(
                                        text = formatAt(hour.timeSeconds, snapshot.utcOffsetSeconds, "HH"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = fg.copy(alpha = 0.75f),
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = conditionIcon(hour.condition, hour.isDay),
                                        contentDescription = null,
                                        tint = fg.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(vertical = 3.dp).size(14.dp)
                                    )
                                    Text(
                                        text = "${temperatureUnit.of(hour.temperature)}°",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = fg,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TileLabel(snapshot.place.name.ifBlank { title }, span, gridColumns, fg)
        }
    )
}

/** One hour of the strip on the wide tile. */
private val HOUR_COLUMN_WIDTH = 38.dp
