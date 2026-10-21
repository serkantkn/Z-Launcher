package com.serkantkn.zeta.data.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.serkantkn.zeta.MainActivity
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.repository.ClockPage
import com.serkantkn.zeta.ui.screens.clock.AlarmActivity
import com.serkantkn.zeta.util.TimeText
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.localizedString

/**
 * The ringing alarm, in the shade.
 *
 * A receiver is not allowed to simply put a screen up any more — from Android 10 a background
 * start is refused unless the system itself does it, and the way the system does it is a
 * notification with a full-screen intent. So the receiver posts this and lets the phone decide:
 * screen off, the alarm screen comes up by itself; screen on and busy, a heads-up with the two
 * buttons that matter. Either way the sound has already started, because the sound does not wait
 * on a screen.
 *
 * It also carries the one notification an alarm clock owes its owner: that an alarm was missed.
 */
object AlarmNotifier {

    private const val TAG = "AlarmNotifier"
    internal const val CHANNEL_RINGING = "zune_alarm_ringing"
    internal const val CHANNEL_MISSED = "zune_alarm_missed"
    private const val RINGING_ID = 7420
    private const val MISSED_ID_BASE = 7430

    /** Posts the full-screen notification for an alarm that has just started ringing. */
    fun showAlarm(context: Context, fireIntent: Intent) {
        if (!canPost(context)) return
        ensureChannels(context)
        val alarmId = fireIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID) ?: return
        val label = fireIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL).orEmpty()
        val snoozeMinutes = fireIntent.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, Alarm.DEFAULT_SNOOZE_MINUTES)

        val screen = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            fireIntent.extras?.let { putExtras(it) }
        }
        val notification = builder(context, screen)
            .setContentTitle(label.ifBlank { context.localizedString(R.string.alarm_notification_title) })
            .setContentText(TimeText.format(System.currentTimeMillis()))
            .addAction(
                0,
                context.localizedString(R.string.alarm_snooze_minutes, snoozeMinutes),
                alarmAction(context, AlarmReceiver.ACTION_SNOOZE, alarmId)
            )
            .addAction(
                0,
                context.localizedString(R.string.alarm_dismiss).lowercase(),
                alarmAction(context, AlarmReceiver.ACTION_DISMISS, alarmId)
            )
            .build()
        post(context, notification)
    }

    /** The same, for a countdown that has reached zero. */
    fun showTimer(context: Context, label: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val screen = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_TIMER_MODE, true)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
        }
        val notification = builder(context, screen)
            .setSmallIcon(R.drawable.ic_wp_timer)
            .setContentTitle(label.ifBlank { context.localizedString(R.string.clock_tab_timer) })
            .setContentText(context.localizedString(R.string.clock_timer_finished))
            .addAction(
                0,
                context.localizedString(R.string.clock_timer_add_minute),
                timerAction(context, TimerReceiver.ACTION_ADD_MINUTE)
            )
            .addAction(
                0,
                context.localizedString(R.string.alarm_dismiss).lowercase(),
                timerAction(context, TimerReceiver.ACTION_STOP)
            )
            .build()
        post(context, notification)
    }

    /** Takes the ringing notification down: answered, snoozed, or silenced. */
    fun clearRinging(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(RINGING_ID) }
    }

    /** Says that an alarm did not ring — the phone was off, or the launcher was stopped. */
    fun showMissed(context: Context, alarm: Alarm) {
        if (!canPost(context)) return
        ensureChannels(context)
        val time = alarm.exactTimeMillis?.let { TimeText.format(it) }
            ?: alarm.bookedForMillis?.let { TimeText.format(it) }
            ?: alarm.timeString
        val what = listOf(time, alarm.label).filter { it.isNotBlank() }.joinToString(" • ")
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSubText(context.localizedString(R.string.hub_clock))
            .setSmallIcon(R.drawable.ic_wp_clock)
            .setContentTitle(context.localizedString(R.string.alarm_missed_title))
            .setContentText(context.localizedString(R.string.alarm_missed_text, what))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAlarms(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(MISSED_ID_BASE + (alarm.id.hashCode() and 0xFFFF), notification)
        }.onFailure { ZuneLog.w(TAG, "the missed alarm would not show", it) }
    }

    private fun builder(context: Context, screen: Intent): NotificationCompat.Builder {
        val fullScreen = PendingIntent.getActivity(
            context,
            RINGING_ID,
            screen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSubText(context.localizedString(R.string.hub_clock))
            .setSmallIcon(R.drawable.ic_wp_clock)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // The sound is AlarmRinger's; the shade only shows the buttons.
            .setSilent(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
    }

    private fun post(context: Context, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(RINGING_ID, notification)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the alarm would not show in the shade", e)
        }
    }

    private fun alarmAction(context: Context, action: String, alarmId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action + alarmId).hashCode(),
            Intent(context, AlarmReceiver::class.java).apply {
                this.action = action
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun timerAction(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, TimerReceiver::class.java).apply { this.action = action },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openAlarms(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        MISSED_ID_BASE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.CLOCK.name)
            putExtra(MainActivity.EXTRA_OPEN_CLOCK_PAGE, ClockPage.ALARMS.name)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_RINGING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RINGING,
                    context.localizedString(R.string.alarm_channel_ringing),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_MISSED) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MISSED,
                    context.localizedString(R.string.alarm_channel_missed),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
