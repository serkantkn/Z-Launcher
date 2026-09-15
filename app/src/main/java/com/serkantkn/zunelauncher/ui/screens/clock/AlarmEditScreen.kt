package com.serkantkn.zunelauncher.ui.screens.clock

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import java.util.Locale
import com.serkantkn.zunelauncher.ui.components.MetroChip
import com.serkantkn.zunelauncher.ui.components.MetroDaySquare
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.components.MetroSwitchRow
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.MetroValueRow
import com.serkantkn.zunelauncher.ui.components.WpDurationPicker

/**
 * Setting an alarm.
 *
 * It is a page, not a dialog, because that is what it was on Windows Phone and because there is
 * more to an alarm than a time: which days it comes back on, what it is for, what it plays, how
 * long it is allowed to go on for. The old hub could only ask for the time, which is why every
 * alarm it made was a nameless one-off — the repeat field existed in the data and there was simply
 * nothing on screen that could fill it in.
 */
@Composable
internal fun AlarmEditScreen(
    alarm: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_clock),
        title = stringResource(if (isNew) R.string.clock_add_alarm else R.string.clock_edit_alarm),
        onClose = onClose,
        modifier = modifier
    ) { padding ->
        AlarmEditBody(
            alarm = alarm,
            isNew = isNew,
            onSave = onSave,
            onDelete = onDelete,
            contentPadding = padding
        )
    }
}

@Composable
private fun AlarmEditBody(
    alarm: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current

    var draft by remember(alarm.id) { mutableStateOf(alarm) }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            draft = draft.copy(ringtoneUri = picked?.toString())
        }
    }

    val ringtoneName = remember(draft.ringtoneUri) {
        val uri = draft.ringtoneUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri == null) {
            null
        } else {
            runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            WpDurationPicker(
                hours = draft.hour,
                minutes = draft.minute,
                seconds = null,
                onHours = { draft = draft.copy(hour = it) },
                onMinutes = { draft = draft.copy(minute = it) },
                onSeconds = {},
                hourLabel = stringResource(R.string.clock_hours),
                minuteLabel = stringResource(R.string.clock_minutes),
                secondLabel = stringResource(R.string.clock_seconds),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        // ── Which days ──
        item { MetroRule(title = stringResource(R.string.clock_repeat)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Alarm.weekOrder().forEach { day ->
                    MetroDaySquare(
                        label = shortDayName(day).take(3),
                        selected = day in draft.daysOfWeek,
                        onClick = {
                            val days = draft.daysOfWeek.toMutableSet()
                            if (!days.add(day)) days.remove(day)
                            draft = draft.copy(daysOfWeek = days)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetroChip(
                    label = stringResource(R.string.clock_repeat_once),
                    selected = draft.daysOfWeek.isEmpty(),
                    onClick = { draft = draft.copy(daysOfWeek = emptySet()) }
                )
                MetroChip(
                    label = stringResource(R.string.clock_repeat_weekdays),
                    selected = draft.daysOfWeek == Alarm.WEEKDAYS,
                    onClick = { draft = draft.copy(daysOfWeek = Alarm.WEEKDAYS) }
                )
                MetroChip(
                    label = stringResource(R.string.clock_repeat_every_day),
                    selected = draft.daysOfWeek == Alarm.EVERY_DAY,
                    onClick = { draft = draft.copy(daysOfWeek = Alarm.EVERY_DAY) }
                )
            }
        }

        // ── What it is for ──
        item { MetroRule(title = stringResource(R.string.clock_alarm_label)) }
        item {
            MetroTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it) },
                placeholder = stringResource(R.string.clock_alarm_label_hint)
            )
        }

        // ── How it rings ──
        item { MetroRule(title = stringResource(R.string.clock_sound)) }
        item {
            MetroValueRow(
                title = stringResource(R.string.clock_ringtone),
                value = ringtoneName ?: stringResource(R.string.clock_ringtone_default),
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.clock_ringtone))
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            draft.ringtoneUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        )
                    }
                    runCatching { ringtonePicker.launch(intent) }
                }
            )
        }
        item {
            MetroSwitchRow(
                title = stringResource(R.string.clock_vibrate),
                checked = draft.vibrate,
                onCheckedChange = { draft = draft.copy(vibrate = it) }
            )
        }
        item {
            MetroSwitchRow(
                title = stringResource(R.string.clock_gradual_volume),
                subtitle = stringResource(R.string.clock_gradual_volume_hint),
                checked = draft.gradualVolume,
                onCheckedChange = { draft = draft.copy(gradualVolume = it) }
            )
        }

        // ── How it behaves afterwards ──
        item { MetroRule(title = stringResource(R.string.clock_when_it_rings)) }
        item {
            MetroValueRow(
                title = stringResource(R.string.clock_snooze_length),
                value = stringResource(R.string.clock_minutes_short, draft.snoozeMinutes),
                onClick = {
                    draft = draft.copy(snoozeMinutes = nextIn(SNOOZE_CHOICES, draft.snoozeMinutes))
                }
            )
        }
        item {
            MetroValueRow(
                title = stringResource(R.string.clock_auto_silence),
                value = if (draft.autoSilenceMinutes <= 0) {
                    stringResource(R.string.clock_never)
                } else {
                    stringResource(R.string.clock_minutes_short, draft.autoSilenceMinutes)
                },
                onClick = {
                    draft = draft.copy(
                        autoSilenceMinutes = nextIn(AUTO_SILENCE_CHOICES, draft.autoSilenceMinutes)
                    )
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_save_cap).lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.accentColor,
                    modifier = Modifier.clickable {
                        onSave(draft.copy(isEnabled = true, snoozedUntilMillis = null))
                    }
                )
                if (!isNew) {
                    Text(
                        text = stringResource(R.string.common_delete).lowercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onDelete(draft) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Steps through a short list of choices, wrapping — a value row that needs no picker of its own. */
private fun nextIn(choices: List<Int>, current: Int): Int {
    val index = choices.indexOf(current)
    return choices[(index + 1).coerceAtLeast(0) % choices.size]
}

private val SNOOZE_CHOICES = listOf(5, 10, 15, 20, 30)
private val AUTO_SILENCE_CHOICES = listOf(5, 10, 15, 30, 0)
