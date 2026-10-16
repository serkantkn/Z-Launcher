package com.serkantkn.zunelauncher.ui.screens.settings

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.VolumeBarStyle
import com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService
import com.serkantkn.zunelauncher.ui.components.WpSlider
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DisplaySound
import com.serkantkn.zunelauncher.util.QuickActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * "display+sound": the phone's own brightness, screen timeout, rotation, ringer, volumes, tones
 * and touch sounds, as Windows Phone gathered them under "ringtones+sounds" and "brightness" -
 * and the launcher's volume bar and music lookups beside them.
 *
 * What is shown is read from the phone, and read again whenever the phone changes it (a volume
 * key, the action center, Android's own settings), so the page never says something stale.
 */
@Composable
internal fun DisplayAndSoundSettingsPage(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val volumeBarStyle by viewModel.volumeBarStyle.collectAsState()
    val musicOnlineExtras by viewModel.musicOnlineExtras.collectAsState()
    val musicOnlineWifiOnly by viewModel.musicOnlineWifiOnly.collectAsState()

    var device by remember { mutableStateOf(DisplaySound.State()) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) { device = withContext(Dispatchers.IO) { DisplaySound.read(context) } }
    PhoneChanges { tick++ }

    val askToWrite = { QuickActions.askToWriteSettings(context); Unit }
    /** Runs [change] when the launcher may change system settings, or asks for that first. */
    fun written(change: () -> Unit) {
        if (device.canWrite) { change(); tick++ } else askToWrite()
    }

    // Which tone the picker was opened for; kept across the picker's own activity.
    var pickingTone by rememberSaveable { mutableIntStateOf(RingtoneManager.TYPE_RINGTONE) }
    val tonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            DisplaySound.setTone(context, pickingTone, picked)
            tick++
        }
    }
    val toneTitle = stringResource(R.string.settings_tones)
    fun pickTone(type: Int) = written {
        pickingTone = type
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, toneTitle)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, type != RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, DisplaySound.toneUri(context, type))
        runCatching { tonePicker.launch(intent) }
    }

    SettingsLazyColumn {
        if (!device.canWrite) {
            item(key = "write_permission") {
                SystemSettingRow(
                    title = stringResource(R.string.settings_write_permission),
                    subtitle = stringResource(R.string.settings_write_permission_sub),
                    onClick = askToWrite
                )
            }
        }

        item(key = "screen") {
            SettingGroup(title = stringResource(R.string.settings_display_brightness)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_auto_brightness),
                        subtitle = stringResource(R.string.settings_auto_brightness_sub),
                        checked = device.autoBrightness,
                        onCheckedChange = { on -> written { QuickActions.setAutoBrightness(context, on) } }
                    )
                    LevelSlider(
                        label = stringResource(R.string.settings_brightness),
                        value = device.brightness,
                        format = ::percentText,
                        range = 0f..1f,
                        onChange = { value ->
                            // Dragging turns automatic brightness off, as it does in Android.
                            if (device.canWrite) {
                                QuickActions.setBrightness(context, value)
                                device = device.copy(brightness = value, autoBrightness = false)
                            } else askToWrite()
                        }
                    )
                    TimeoutPicker(
                        current = device.screenTimeoutMs,
                        onPick = { ms -> written { DisplaySound.setScreenTimeout(context, ms) } }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_auto_rotate),
                        subtitle = stringResource(R.string.settings_auto_rotate_sub),
                        checked = device.autoRotate,
                        onCheckedChange = { on -> written { DisplaySound.setAutoRotate(context, on) } }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_more_display),
                        subtitle = stringResource(R.string.settings_more_display_sub),
                        onClick = { openSystemPage(context, Settings.ACTION_DISPLAY_SETTINGS) }
                    )
                }
            }
        }

        item(key = "ringer") {
            SettingGroup(title = stringResource(R.string.settings_ringer)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AudioManager.RINGER_MODE_NORMAL to R.string.settings_ringer_ring,
                            AudioManager.RINGER_MODE_VIBRATE to R.string.settings_ringer_vibrate,
                            AudioManager.RINGER_MODE_SILENT to R.string.settings_ringer_silent
                        ).forEach { (mode, label) ->
                            SettingPill(
                                label = stringResource(label),
                                selected = device.ringerMode == mode,
                                onClick = {
                                    if (DisplaySound.setRingerMode(context, mode)) {
                                        device = device.copy(ringerMode = mode)
                                    }
                                    tick++
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (!device.maySilence) {
                        Text(
                            text = stringResource(R.string.settings_ringer_silent_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalZuneColors.current.textMuted,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_vibrate_when_ringing),
                        subtitle = stringResource(R.string.settings_vibrate_when_ringing_sub),
                        checked = device.vibrateWhenRinging,
                        onCheckedChange = { on -> written { DisplaySound.setVibrateWhenRinging(context, on) } }
                    )
                }
            }
        }

        item(key = "volume") {
            SettingGroup(title = stringResource(R.string.settings_volume_levels)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple(R.string.settings_media_volume, AudioManager.STREAM_MUSIC, device.media),
                        Triple(R.string.settings_ring_volume, AudioManager.STREAM_RING, device.ring),
                        Triple(R.string.settings_notification_volume, AudioManager.STREAM_NOTIFICATION, device.notification),
                        Triple(R.string.settings_alarm_volume, AudioManager.STREAM_ALARM, device.alarm)
                    ).forEach { (label, stream, level) ->
                        LevelSlider(
                            label = stringResource(label),
                            value = level.current.toFloat(),
                            format = { it.roundToInt().toString() },
                            range = level.min.toFloat()..level.max.toFloat(),
                            steps = (level.max - level.min - 1).coerceAtLeast(0),
                            onChange = { DisplaySound.setVolume(context, stream, it.roundToInt()) }
                        )
                    }
                }
            }
        }

        item(key = "tones") {
            SettingGroup(title = stringResource(R.string.settings_tones)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val none = stringResource(R.string.settings_tone_none)
                    listOf(
                        Triple(R.string.settings_ringtone, RingtoneManager.TYPE_RINGTONE, device.ringtone),
                        Triple(R.string.settings_notification_sound, RingtoneManager.TYPE_NOTIFICATION, device.notificationSound),
                        Triple(R.string.settings_alarm_sound, RingtoneManager.TYPE_ALARM, device.alarmSound)
                    ).forEach { (label, type, title) ->
                        SystemSettingRow(
                            title = stringResource(label),
                            subtitle = when {
                                title == null -> ""
                                title.isEmpty() -> none
                                else -> title
                            },
                            onClick = { pickTone(type) }
                        )
                    }
                }
            }
        }

        item(key = "touch") {
            SettingGroup(title = stringResource(R.string.settings_touch_feedback)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touch_sounds),
                        subtitle = stringResource(R.string.settings_touch_sounds_sub),
                        checked = device.touchSounds,
                        onCheckedChange = { on -> written { DisplaySound.setTouchSounds(context, on) } }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_dial_tones),
                        subtitle = stringResource(R.string.settings_dial_tones_sub),
                        checked = device.dialTones,
                        onCheckedChange = { on -> written { DisplaySound.setDialTones(context, on) } }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_touch_vibration),
                        subtitle = stringResource(R.string.settings_touch_vibration_sub),
                        checked = device.touchVibration,
                        onCheckedChange = { on -> written { DisplaySound.setTouchVibration(context, on) } }
                    )
                    SystemSettingRow(
                        title = stringResource(R.string.settings_more_sound),
                        subtitle = stringResource(R.string.settings_more_sound_sub),
                        onClick = { openSystemPage(context, Settings.ACTION_SOUND_SETTINGS) }
                    )
                }
            }
        }

        item(key = "volume_bar_style") {
            SettingGroup(title = stringResource(R.string.settings_volume_style)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.volume_bar_wp),
                        subtitle = stringResource(R.string.settings_volume_wp_sub),
                        selected = volumeBarStyle == VolumeBarStyle.WINDOWS_PHONE,
                        onClick = { viewModel.setVolumeBarStyle(VolumeBarStyle.WINDOWS_PHONE) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.volume_bar_system),
                        subtitle = stringResource(R.string.settings_volume_system_sub),
                        selected = volumeBarStyle == VolumeBarStyle.SYSTEM,
                        onClick = { viewModel.setVolumeBarStyle(VolumeBarStyle.SYSTEM) }
                    )

                    if (volumeBarStyle == VolumeBarStyle.WINDOWS_PHONE) {
                        val hasOverlay = remember(tick) { Settings.canDrawOverlays(context) }
                        val hasKeyService = remember(tick) { ZuneKeyAccessibilityService.isEnabled(context) }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_volume_permissions_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalZuneColors.current.textMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SystemSettingRow(
                            title = stringResource(R.string.settings_overlay_permission),
                            subtitle = if (hasOverlay) stringResource(R.string.settings_permission_granted) else stringResource(R.string.settings_overlay_permission_sub),
                            isActive = hasOverlay,
                            onClick = {
                                openSystemPage(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            }
                        )
                        SystemSettingRow(
                            title = stringResource(R.string.settings_key_service),
                            subtitle = if (hasKeyService) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_key_service_sub),
                            isActive = hasKeyService,
                            onClick = { openSystemPage(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                        )
                    }
                }
            }
        }

        item(key = "music_online") {
            SettingGroup(title = stringResource(R.string.settings_music_online)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.music_online_extras),
                        subtitle = stringResource(R.string.music_online_extras_summary),
                        checked = musicOnlineExtras,
                        onCheckedChange = viewModel::setMusicOnlineExtras
                    )
                    // Only worth showing once there is something for it to apply to.
                    if (musicOnlineExtras) {
                        SettingSwitchRow(
                            title = stringResource(R.string.music_online_wifi_only),
                            subtitle = stringResource(R.string.music_online_wifi_only_summary),
                            checked = musicOnlineWifiOnly,
                            onCheckedChange = viewModel::setMusicOnlineWifiOnly
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_music_online_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZuneColors.current.textDim,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** Calls [onChange] whenever the phone's system settings or a volume change, while on screen. */
@Composable
private fun PhoneChanges(onChange: () -> Unit) {
    val context = LocalContext.current
    val latest by androidx.compose.runtime.rememberUpdatedState(onChange)
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = latest()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = latest()
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(VOLUME_CHANGED)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

/**
 * A label with its value beside it and a slider under them. While the finger is on it the slider
 * shows where the finger put it, not the phone's answer, which comes a moment later.
 */
@Composable
private fun LevelSlider(
    label: String,
    value: Float,
    format: (Float) -> String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0
) {
    var held by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) held = null }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = format(held ?: value),
                style = MaterialTheme.typography.titleMedium,
                color = LocalZuneColors.current.accentColor
            )
        }
        WpSlider(
            value = held ?: value,
            onValueChange = {
                dragging = true
                held = it
                onChange(it)
            },
            onValueChangeFinished = { dragging = false },
            valueRange = range,
            steps = steps
        )
    }
}

/** "screen times out after", opening in place onto its choices, as Windows Phone's list picker did. */
@Composable
private fun TimeoutPicker(current: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = !open }
        ) {
            SettingRowContent(
                title = stringResource(R.string.settings_screen_timeout),
                subtitle = timeoutText(current),
                trailing = {
                    Icon(
                        imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = LocalZuneColors.current.textMuted
                    )
                }
            )
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                DisplaySound.timeoutChoices(current).forEach { ms ->
                    SettingChoiceRow(
                        title = timeoutText(ms),
                        subtitle = "",
                        selected = ms == current,
                        onClick = {
                            onPick(ms)
                            open = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun timeoutText(ms: Int): String {
    val seconds = ms / 1000
    return if (seconds < 60 || seconds % 60 != 0) {
        pluralStringResource(R.plurals.settings_timeout_seconds, seconds, seconds)
    } else {
        pluralStringResource(R.plurals.settings_timeout_minutes, seconds / 60, seconds / 60)
    }
}

/** "%50" in Turkish, "50%" in English: the locale decides which side the sign goes. */
private fun percentText(fraction: Float): String =
    java.text.NumberFormat.getPercentInstance(java.util.Locale.getDefault()).format(fraction.toDouble())

private fun openSystemPage(context: Context, action: String, data: Uri? = null) {
    runCatching {
        context.startActivity(Intent(action, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Sent by Android whenever a stream's volume changes; not public, but long stable. */
private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
