package com.serkantkn.zeta.ui.screens.clock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.WorldCity
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.util.TimeText
import com.serkantkn.zeta.util.formatOffset
import com.serkantkn.zeta.util.isNightInZone
import com.serkantkn.zeta.util.zoneDayOffset
import com.serkantkn.zeta.util.zoneOffsetMinutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.serkantkn.zeta.ui.components.MetroBigNumber
import com.serkantkn.zeta.ui.components.MetroChip
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroRule

/**
 * The world clock page: the reader's own time at the top, then everybody else's.
 *
 * Each city says three things the old page did not: whether it is the day before or after there,
 * how far off it is to the minute rather than the hour — half the world runs on a half hour — and,
 * with a bar down the left, whether it is light or dark there. That last one is the question
 * anybody actually opens a world clock to answer: can I call them now.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorldClockPage(
    cities: List<WorldCity>,
    now: Long,
    showSeconds: Boolean,
    onAddCity: () -> Unit,
    onCityLongPress: (WorldCity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val date = remember(now) { Date(now) }
    val use24h by TimeText.use24h.collectAsState()
    // Keyed on the phone's zone as well: a format remembered once keeps the zone it was made
    // in, and a traveller's local time would stay on the old city until the hub was reopened.
    val zoneId = TimeZone.getDefault().id
    val timeFormat = remember(showSeconds, use24h, zoneId) {
        SimpleDateFormat(TimeText.pattern(withSeconds = showSeconds, use24h = use24h), Locale.getDefault())
    }
    val dateFormat = remember(zoneId) { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()) }

    ClockSplit(contentPadding = contentPadding, modifier = modifier, head = {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.clock_local_time).lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor
                )
                MetroBigNumber(
                    text = timeFormat.format(date),
                    fontSize = if (showSeconds) 62.sp else 78.sp
                )
                Text(
                    text = dateFormat.format(date).lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted
                )
            }
    }) {
        item {
            MetroRule(
                title = stringResource(R.string.clock_cities),
                actionLabel = stringResource(R.string.common_add),
                onAction = onAddCity
            )
        }

        if (cities.isEmpty()) {
            item {
                MetroEmpty(
                    message = stringResource(R.string.clock_no_cities),
                    actionLabel = stringResource(R.string.clock_add_city),
                    onAction = onAddCity
                )
            }
        }

        items(cities, key = { it.timeZoneId }) { city ->
            WorldCityRow(
                city = city,
                now = now,
                onLongPress = { onCityLongPress(city) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldCityRow(
    city: WorldCity,
    now: Long,
    onLongPress: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    val zone = remember(city.timeZoneId) { TimeZone.getTimeZone(city.timeZoneId) }
    val use24h by TimeText.use24h.collectAsState()
    val timeFormat = remember(city.timeZoneId, use24h) {
        SimpleDateFormat(TimeText.pattern(use24h = use24h), Locale.getDefault()).apply { timeZone = zone }
    }
    val offsetMinutes = zoneOffsetMinutes(now, city.timeZoneId)
    val dayOffset = zoneDayOffset(now, city.timeZoneId)
    val isNight = isNightInZone(now, city.timeZoneId)

    val offsetText = when {
        offsetMinutes == 0 -> stringResource(R.string.clock_same_as_local)
        else -> stringResource(R.string.clock_offset_hours, formatOffset(offsetMinutes))
    }
    val dayText = when (dayOffset) {
        1 -> stringResource(R.string.clock_tomorrow)
        -1 -> stringResource(R.string.clock_yesterday)
        else -> null
    }
    val country = remember(city.timeZoneId) { city.countryName(context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onLongPress,
                onLongClick = onLongPress
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Light or dark there, said without a word: the bar is lit while their day is.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(46.dp)
                .background(
                    if (isNight) zuneColors.textDim.copy(alpha = 0.45f) else zuneColors.accentColor
                )
        )
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.cityName(context),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(country.takeIf { it.isNotBlank() }, offsetText).joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            MetroBigNumber(
                text = timeFormat.format(Date(now)),
                fontSize = 36.sp,
                color = if (isNight) zuneColors.textMuted else null
            )
            if (dayText != null) {
                Text(
                    text = dayText.lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.accentColor
                )
            }
        }
    }
}

/** Nothing here yet — used by the wide layout, which has no bottom bar to add a city from. */
@Composable
internal fun WorldClockActions(onAdd: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroChip(
            label = stringResource(R.string.clock_add_city),
            selected = false,
            onClick = onAdd
        )
    }
}
