package com.serkantkn.zunelauncher.ui.screens.clock

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.service.AlarmReceiver
import com.serkantkn.zunelauncher.data.service.AlarmRinger
import com.serkantkn.zunelauncher.data.service.TimerReceiver
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import com.serkantkn.zunelauncher.util.AppLocale
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The screen an alarm wakes you with.
 *
 * A full bleed of the accent colour and the time in the largest type in the launcher — the Windows
 * Phone alarm, which was a screen rather than a dialog, and which asked exactly two questions.
 *
 * "Ertele" is the bigger of the two and sits where a thumb lands, because it is the one pressed
 * half asleep. The sound is not owned here but by [AlarmRinger], so that the same snooze can be
 * pressed from the notification when no screen of ours is up.
 */
class AlarmActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private var alarmId: String? = null
    private var snoozeMinutes: Int = Alarm.DEFAULT_SNOOZE_MINUTES
    private var isTimer: Boolean = false

    companion object {
        /** Set when the screen is ending a countdown rather than an alarm. */
        const val EXTRA_TIMER_MODE = "TIMER_MODE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }

        // Show over lockscreen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        isTimer = intent.getBooleanExtra(EXTRA_TIMER_MODE, false)
        alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
        val label = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL).orEmpty()
        snoozeMinutes = intent.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, Alarm.DEFAULT_SNOOZE_MINUTES)
        val autoSilence = intent.getIntExtra(AlarmReceiver.EXTRA_AUTO_SILENCE, Alarm.DEFAULT_AUTO_SILENCE_MINUTES)

        AlarmRinger.start(
            context = this,
            ringtoneUri = intent.getStringExtra(AlarmReceiver.EXTRA_RINGTONE),
            vibrate = intent.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATE, true),
            // A timer has been watched for; it does not need to be crept up on.
            gradualVolume = !isTimer && intent.getBooleanExtra(AlarmReceiver.EXTRA_GRADUAL, true)
        )

        setContent {
            val fontScale by appContainer.settingsDataStore.fontScale.collectAsState(initial = 1.0f)
            ZuneLauncherTheme(fontScale = fontScale) {
                // Nobody came: stop making a noise rather than ringing into an empty room.
                if (autoSilence > 0) {
                    LaunchedEffect(Unit) {
                        delay(autoSilence * 60_000L)
                        dismiss()
                    }
                }
                AlarmScreen(
                    eyebrow = stringResource(
                        if (isTimer) R.string.clock_timer_finished else R.string.alarm_wake_up
                    ).lowercase(Locale.getDefault()),
                    label = label,
                    secondaryLabel = if (isTimer) {
                        stringResource(R.string.clock_timer_add_minute)
                    } else {
                        stringResource(R.string.alarm_snooze_minutes, snoozeMinutes)
                    },
                    onSecondary = if (isTimer) ::addMinute else ::snooze,
                    onDismiss = ::dismiss
                )
            }
        }
    }

    private fun snooze() {
        AlarmRinger.stop()
        alarmId?.let { id ->
            sendBroadcast(
                Intent(this, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_SNOOZE
                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
                }
            )
        }
        finish()
    }

    /** A timer's answer to snooze: give it one more minute from now. */
    private fun addMinute() {
        AlarmRinger.stop()
        sendBroadcast(
            Intent(this, TimerReceiver::class.java).apply { action = TimerReceiver.ACTION_ADD_MINUTE }
        )
        finish()
    }

    private fun dismiss() {
        AlarmRinger.stop()
        if (isTimer) {
            sendBroadcast(
                Intent(this, TimerReceiver::class.java).apply { action = TimerReceiver.ACTION_STOP }
            )
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmRinger.stop()
    }
}

@Composable
private fun AlarmScreen(
    eyebrow: String,
    label: String,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L)
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(zuneColors.accentColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp, vertical = 40.dp)
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 2.sp
                ),
                color = Color.White.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = timeFormat.format(now),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Thin,
                    fontSize = 120.sp,
                    letterSpacing = (-6).sp,
                    lineHeight = 120.sp
                ),
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = dateFormat.format(now).lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = Color.White.copy(alpha = 0.85f)
            )
            if (label.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AlarmButton(
                    text = secondaryLabel,
                    filled = true,
                    modifier = Modifier.weight(1.4f),
                    onClick = onSecondary
                )
                AlarmButton(
                    text = stringResource(R.string.alarm_dismiss).lowercase(Locale.getDefault()),
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
            }
        }
    }
}

/** A Windows Phone button: a rectangle with a two-pixel border and nothing else. */
@Composable
private fun AlarmButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .border(2.dp, Color.White)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = if (filled) LocalZuneColors.current.accentColor else Color.White,
            textAlign = TextAlign.Center
        )
    }
}
