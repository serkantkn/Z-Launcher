package com.serkantkn.zeta.ui.screens.clock

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
import androidx.compose.runtime.mutableIntStateOf
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
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.data.service.AlarmReceiver
import com.serkantkn.zeta.data.service.AlarmRinger
import com.serkantkn.zeta.data.service.TimerReceiver
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneLauncherTheme
import com.serkantkn.zeta.util.AppLocale
import com.serkantkn.zeta.util.TimeText
import com.serkantkn.zeta.util.ZuneLog
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
 * half asleep. The sound is not owned here but by [AlarmRinger], started by the receiver before
 * this screen exists, so that the same snooze can be pressed from the notification when no screen
 * of ours is up — and so that this screen being recreated, replaced by a second alarm, or swept
 * away by the system never silences an alarm nobody has answered. Only "ertele" and "kapat" do.
 *
 * Manifest: the activity should carry `android:taskAffinity=""`, `android:excludeFromRecents="true"`,
 * `android:showWhenLocked="true"`, `android:turnScreenOn="true"` and `android:launchMode="singleTop"`,
 * so it sits in a task of its own, leaves no trace in recents, and a second alarm lands in
 * [onNewIntent] rather than stacking a second copy.
 */
class AlarmActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    /** Which alarm (or [AlarmRinger.TIMER_ID]) this screen is answering for. */
    private var ringingId by mutableStateOf<String?>(null)
    private var label by mutableStateOf("")
    private var snoozeMinutes by mutableIntStateOf(Alarm.DEFAULT_SNOOZE_MINUTES)
    private var isTimer by mutableStateOf(false)

    companion object {
        /** Set when the screen is ending a countdown rather than an alarm. */
        const val EXTRA_TIMER_MODE = "TIMER_MODE"
        private const val TAG = "AlarmActivity"
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

        bind(intent)
        // Opened late — the notification tapped after the alarm was already answered or gave up.
        if (!AlarmRinger.isRinging) {
            finish()
            return
        }

        setContent {
            val fontScale by appContainer.settingsDataStore.fontScale.collectAsState(initial = 1.0f)
            val use24h by TimeText.use24h.collectAsState()
            ZuneLauncherTheme(fontScale = fontScale) {
                // The sound is the alarm; when it stops — answered from the shade, or given up on
                // by the ringer itself — there is nothing left for this screen to ask.
                LaunchedEffect(Unit) {
                    while (AlarmRinger.isRinging) delay(500L)
                    finish()
                }
                AlarmScreen(
                    eyebrow = stringResource(
                        if (isTimer) R.string.clock_timer_finished else R.string.alarm_wake_up
                    ).lowercase(Locale.getDefault()),
                    label = label,
                    use24h = use24h,
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

    /** A second alarm while the first is up: the newer one takes the screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val previous = ringingId
        bind(intent)
        if (previous != null && previous != ringingId) {
            ZuneLog.w(TAG, "alarm $ringingId replaces $previous on the alarm screen")
        }
    }

    private fun bind(intent: Intent) {
        isTimer = intent.getBooleanExtra(EXTRA_TIMER_MODE, false)
        ringingId = if (isTimer) AlarmRinger.TIMER_ID else intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
        label = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL).orEmpty()
        snoozeMinutes = intent.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, Alarm.DEFAULT_SNOOZE_MINUTES)
    }

    private fun snooze() {
        val id = ringingId
        AlarmRinger.stop(id)
        if (id != null) {
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
        AlarmRinger.stop(AlarmRinger.TIMER_ID)
        sendBroadcast(
            Intent(this, TimerReceiver::class.java).apply { action = TimerReceiver.ACTION_ADD_MINUTE }
        )
        finish()
    }

    private fun dismiss() {
        val id = ringingId
        AlarmRinger.stop(id)
        if (isTimer) {
            sendBroadcast(
                Intent(this, TimerReceiver::class.java).apply { action = TimerReceiver.ACTION_STOP }
            )
        } else if (id != null) {
            sendBroadcast(
                Intent(this, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_DISMISS
                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
                }
            )
        }
        finish()
    }
}

@Composable
private fun AlarmScreen(
    eyebrow: String,
    label: String,
    use24h: Boolean,
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
    val timeFormat = remember(use24h) { SimpleDateFormat(TimeText.pattern(use24h = use24h), Locale.getDefault()) }
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
