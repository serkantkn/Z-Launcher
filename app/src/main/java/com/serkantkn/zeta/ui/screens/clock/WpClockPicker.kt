package com.serkantkn.zeta.ui.screens.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.ui.components.MetroChip
import com.serkantkn.zeta.ui.components.WpDurationPicker
import com.serkantkn.zeta.util.TimeText
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * An hour-and-minute dial that follows the clock setting.
 *
 * With a 24-hour clock it is the plain duration picker running 0 to 23. With a 12-hour one the
 * hours run 1 to 12 and a pair of chips says which half of the day — the number handed back is
 * always the 24-hour hour, so nothing downstream has to know which dial was shown.
 */
@Composable
internal fun WpClockPicker(
    hour: Int,
    minute: Int,
    onTime: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val use24h by TimeText.use24h.collectAsState()
    val amPm = remember { DateFormatSymbols.getInstance(Locale.getDefault()).amPmStrings.map { it.lowercase(Locale.getDefault()) } }

    Column(modifier = modifier) {
        WpDurationPicker(
            hours = if (use24h) hour else TimeText.hour12Of(hour),
            minutes = minute,
            seconds = null,
            onHours = { onTime(if (use24h) it else TimeText.hour24Of(it, TimeText.isPm(hour)), minute) },
            onMinutes = { onTime(hour, it) },
            onSeconds = {},
            hourLabel = stringResource(R.string.clock_hours),
            minuteLabel = stringResource(R.string.clock_minutes),
            secondLabel = stringResource(R.string.clock_seconds),
            hourRange = if (use24h) 0..23 else 1..12,
            modifier = Modifier.fillMaxWidth()
        )
        if (!use24h) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetroChip(
                    label = amPm.getOrElse(0) { "am" },
                    selected = !TimeText.isPm(hour),
                    onClick = { onTime(TimeText.hour24Of(TimeText.hour12Of(hour), pm = false), minute) }
                )
                MetroChip(
                    label = amPm.getOrElse(1) { "pm" },
                    selected = TimeText.isPm(hour),
                    onClick = { onTime(TimeText.hour24Of(TimeText.hour12Of(hour), pm = true), minute) }
                )
            }
        }
    }
}
