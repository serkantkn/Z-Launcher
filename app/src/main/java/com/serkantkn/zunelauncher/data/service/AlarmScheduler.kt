package com.serkantkn.zunelauncher.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.ClockPage
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * Putting an alarm into the system's own clock.
 *
 * [AlarmManager.setAlarmClock] is used rather than a plain exact alarm because it is the one kind
 * of alarm Android will not delay for battery: it is the call the phone's own clock app makes, and
 * it is what puts the little alarm symbol in the status bar. That symbol is a link, so it is given
 * somewhere to go — the launcher's own Clock hub, on the alarms page.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }
        val triggerAt = alarm.nextTriggerMillis()
        arm(alarm, triggerAt, isSnooze = false)
    }

    /** Puts a ringing alarm off until [untilMillis] without touching when it would next be due. */
    fun snooze(alarm: Alarm, untilMillis: Long) {
        arm(alarm, untilMillis, isSnooze = true)
    }

    private fun arm(alarm: Alarm, triggerAt: Long, isSnooze: Boolean) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            // A distinct action per alarm, so two alarms never collide into one pending intent.
            action = "${AlarmReceiver.ACTION_FIRE}.${alarm.id}"
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZE, isSnooze)
            alarm.noteId?.let { putExtra(AlarmReceiver.EXTRA_NOTE_ID, it) }
            // How it should ring travels with the alarm rather than being looked up when it goes
            // off: the intent is rebuilt on every save, so these are always what was last saved,
            // and the screen that rings can start ringing without waiting on a disk read.
            putExtra(AlarmReceiver.EXTRA_RINGTONE, alarm.ringtoneUri)
            putExtra(AlarmReceiver.EXTRA_VIBRATE, alarm.vibrate)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
            putExtra(AlarmReceiver.EXTRA_GRADUAL, alarm.gradualVolume)
            putExtra(AlarmReceiver.EXTRA_AUTO_SILENCE, alarm.autoSilenceMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExact()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showAlarmsIntent()),
                    pendingIntent
                )
            } else {
                // No permission for an exact alarm: still ring, just without the promise of the
                // exact minute — better than an alarm that silently never goes off.
                ZuneLog.w(TAG, "no exact alarm permission; falling back to an inexact alarm")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            ZuneLog.e(TAG, "the system would not take the alarm", e)
        }
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "${AlarmReceiver.ACTION_FIRE}.${alarm.id}"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Whether the phone will let the launcher name an exact minute.
     *
     * The launcher holds USE_EXACT_ALARM, which an alarm clock is allowed to and which is not
     * revocable — but a build without it, or an OEM that treats it differently, would otherwise
     * fail silently at the moment the alarm was supposed to ring.
     */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    /** Where the status bar's alarm symbol goes when it is tapped. */
    private fun showAlarmsIntent(): PendingIntent? = try {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.CLOCK.name)
            putExtra(MainActivity.EXTRA_OPEN_CLOCK_PAGE, ClockPage.ALARMS.name)
        }
        PendingIntent.getActivity(
            context,
            SHOW_ALARMS_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } catch (e: Exception) {
        ZuneLog.w(TAG, "could not build the status bar's alarm link", e)
        null
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val SHOW_ALARMS_REQUEST = 7301
    }
}
