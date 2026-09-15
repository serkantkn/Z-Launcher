package com.serkantkn.zunelauncher.ui.screens.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.datastore.ClockDataStore
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.formatCountdown
import java.util.Locale
import com.serkantkn.zunelauncher.ui.components.MetroBigNumber
import com.serkantkn.zunelauncher.ui.components.MetroChip
import com.serkantkn.zunelauncher.ui.components.MetroProgressLine
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.WpDurationPicker

/**
 * The timer page.
 *
 * The circle is gone. Progress runs as a line under the figures, which is how Windows Phone drew
 * it and which leaves the number the largest thing on the page instead of a ring around it.
 *
 * While it runs it also says the wall-clock time it will finish at — the thing you work out in
 * your head otherwise — and the durations you last used sit under it as one tap each.
 */
@Composable
internal fun TimerPage(
    state: ClockDataStore.TimerState,
    remainingMillis: Long,
    presets: List<Long>,
    onSetDuration: (Long) -> Unit,
    onOpenSetup: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val progress = if (state.totalMillis > 0L) {
        1f - (remainingMillis.toFloat() / state.totalMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val finished = state.totalMillis > 0L && remainingMillis <= 0L && !state.isRunning

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (state.label.isNotBlank()) {
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentColor
                    )
                }
                MetroBigNumber(
                    text = formatCountdown(remainingMillis),
                    fontSize = if (remainingMillis >= 3_600_000L) 68.sp else 88.sp,
                    color = if (finished) zuneColors.accentColor else null
                )
                Spacer(modifier = Modifier.height(14.dp))
                MetroProgressLine(progress = progress)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        finished -> stringResource(R.string.clock_timer_finished)
                        state.isRunning -> stringResource(
                            R.string.clock_timer_ends_at,
                            timeOf(state.endsAtMillis)
                        )
                        else -> stringResource(
                            R.string.clock_timer_set_to,
                            formatCountdown(state.totalMillis)
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted
                )
            }
        }

        if (!state.isRunning) {
            item {
                MetroRule(
                    title = stringResource(R.string.clock_timer_duration),
                    actionLabel = stringResource(R.string.clock_change_duration),
                    onAction = onOpenSetup
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        MetroChip(
                            label = presetLabel(preset),
                            selected = preset == state.totalMillis,
                            onClick = { onSetDuration(preset) }
                        )
                    }
                }
            }
        }
    }
}

/** A preset written as shortly as it can be: "5 dk", "1 sa 30 dk". */
@Composable
private fun presetLabel(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.clock_hours_minutes_short, hours, minutes)
        hours > 0 -> stringResource(R.string.clock_hours_short, hours)
        else -> stringResource(R.string.clock_minutes_short, totalMinutes)
    }
}

/** The page's own settings, opened over the hub as a sub-screen. */
@Composable
internal fun TimerSetupScreen(
    initialMillis: Long,
    initialLabel: String,
    onCancel: () -> Unit,
    onConfirm: (durationMillis: Long, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_clock),
        title = stringResource(R.string.clock_timer_duration),
        onClose = onCancel,
        modifier = modifier
    ) { padding ->
        TimerSetupBody(
            initialMillis = initialMillis,
            initialLabel = initialLabel,
            onConfirm = onConfirm,
            contentPadding = padding
        )
    }
}

@Composable
private fun TimerSetupBody(
    initialMillis: Long,
    initialLabel: String,
    onConfirm: (Long, String) -> Unit,
    contentPadding: PaddingValues
) {
    val total = (initialMillis / 1000L).toInt()
    var hours by rememberSaveable { mutableIntStateOf(total / 3600) }
    var minutes by rememberSaveable { mutableIntStateOf((total % 3600) / 60) }
    var seconds by rememberSaveable { mutableIntStateOf(total % 60) }
    var label by rememberSaveable { mutableStateOf(initialLabel) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            WpDurationPicker(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                onHours = { hours = it },
                onMinutes = { minutes = it },
                onSeconds = { seconds = it },
                hourLabel = stringResource(R.string.clock_hours),
                minuteLabel = stringResource(R.string.clock_minutes),
                secondLabel = stringResource(R.string.clock_seconds),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        }

        item { MetroRule(title = stringResource(R.string.clock_timer_label)) }
        item {
            MetroTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = stringResource(R.string.clock_timer_label_hint)
            )
        }

        item {
            Text(
                text = stringResource(R.string.common_save_cap).lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                color = LocalZuneColors.current.accentColor,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .clickable {
                        val millis = (hours * 3600L + minutes * 60L + seconds) * 1000L
                        if (millis > 0L) onConfirm(millis, label.trim())
                    }
            )
        }
    }
}
