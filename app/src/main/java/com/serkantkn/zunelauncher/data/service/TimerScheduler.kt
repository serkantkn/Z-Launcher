package com.serkantkn.zunelauncher.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.ClockPage
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * Handing the countdown to the system, so that it ends whatever else is happening.
 *
 * The old timer only counted inside the launcher's own process: it stopped the moment the launcher
 * was killed, and even when it survived it did nothing at all on reaching zero. What actually ends
 * a timer is an alarm in the system's clock, and that is what this books.
 *
 * It is booked with [AlarmManager.setAlarmClock], the same call the alarms use — not only because
 * it is the one kind the phone will not delay for battery, but because it is the only one that
 * lets the receiver bring a screen up when it goes off.
 */
class TimerScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(endsAtMillis: Long) {
        val pendingIntent = firePendingIntent()
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(endsAtMillis, showTimerIntent()),
                pendingIntent
            )
        } catch (e: SecurityException) {
            ZuneLog.e(TAG, "the system would not take the timer", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
            }
        }
    }

    fun cancel() {
        alarmManager.cancel(firePendingIntent())
    }

    private fun firePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        FIRE_REQUEST,
        Intent(context, TimerReceiver::class.java).apply { action = TimerReceiver.ACTION_DONE },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Where the status bar's clock symbol goes while a timer is booked. */
    private fun showTimerIntent(): PendingIntent? = runCatching {
        PendingIntent.getActivity(
            context,
            SHOW_REQUEST,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.CLOCK.name)
                putExtra(MainActivity.EXTRA_OPEN_CLOCK_PAGE, ClockPage.TIMER.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }.getOrNull()

    private companion object {
        const val TAG = "TimerScheduler"
        const val FIRE_REQUEST = 7401
        const val SHOW_REQUEST = 7402
    }
}
