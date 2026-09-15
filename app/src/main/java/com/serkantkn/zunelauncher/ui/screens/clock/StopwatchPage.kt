package com.serkantkn.zunelauncher.ui.screens.clock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.fastestLapIndex
import com.serkantkn.zunelauncher.util.formatStopwatch
import com.serkantkn.zunelauncher.util.lapSplits
import com.serkantkn.zunelauncher.util.slowestLapIndex
import java.util.Locale
import com.serkantkn.zunelauncher.ui.components.MetroBigNumber
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.metroDigitStyle

/**
 * The stopwatch page.
 *
 * A lap list that only repeats the reading on the watch is a list of numbers that all look alike;
 * what a lap is worth is how long *it* took. So each row leads with its own length and keeps the
 * running total beside it, small — and the fastest and slowest of them are marked, which is the
 * comparison anybody scanning the list is making by eye anyway.
 */
@Composable
internal fun StopwatchPage(
    readingMillis: Long,
    laps: List<Long>,
    onClearLaps: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val splits = remember(laps) { lapSplits(laps) }
    val fastest = remember(splits) { fastestLapIndex(splits) }
    val slowest = remember(splits) { slowestLapIndex(splits) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                MetroBigNumber(
                    text = formatStopwatch(readingMillis),
                    fontSize = if (readingMillis >= 3_600_000L) 56.sp else 72.sp
                )
            }
        }

        item {
            MetroRule(
                title = stringResource(R.string.clock_laps),
                actionLabel = if (laps.isEmpty()) null else stringResource(R.string.common_clear),
                onAction = if (laps.isEmpty()) null else onClearLaps
            )
        }

        if (laps.isEmpty()) {
            item { MetroEmpty(message = stringResource(R.string.clock_no_laps)) }
        }

        itemsIndexed(laps) { index, cumulative ->
            val split = splits.getOrElse(index) { cumulative }
            LapRow(
                number = laps.size - index,
                split = split,
                cumulative = cumulative,
                mark = when (index) {
                    fastest -> LapMark.FASTEST
                    slowest -> LapMark.SLOWEST
                    else -> LapMark.NONE
                }
            )
        }
    }
}

private enum class LapMark { FASTEST, SLOWEST, NONE }

@Composable
private fun LapRow(number: Int, split: Long, cumulative: Long, mark: LapMark) {
    val zuneColors = LocalZuneColors.current
    val splitColor = when (mark) {
        LapMark.FASTEST -> zuneColors.accentColor
        LapMark.SLOWEST -> SLOW_COLOR
        LapMark.NONE -> MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.clock_lap_n, number),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted
            )
            val markLabel = when (mark) {
                LapMark.FASTEST -> stringResource(R.string.clock_fastest_lap)
                LapMark.SLOWEST -> stringResource(R.string.clock_slowest_lap)
                LapMark.NONE -> null
            }
            if (markLabel != null) {
                Text(
                    text = markLabel.lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = splitColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatStopwatch(split),
                style = metroDigitStyle(fontSize = 26.sp, weight = FontWeight.Light, letterSpacing = (-1).sp),
                color = splitColor,
                maxLines = 1
            )
            Text(
                text = formatStopwatch(cumulative),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = zuneColors.textDim,
                maxLines = 1
            )
        }
    }
}

/** The one colour in the hub that is not the accent: a lap that lost time. */
private val SLOW_COLOR = Color(0xFFE51C23)
