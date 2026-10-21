package com.serkantkn.zeta.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.serkantkn.zeta.MainActivity
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.repository.ClockPage
import com.serkantkn.zeta.util.ZuneLog

/**
 * Putting an alarm into the system's own clock.
 *
 * [AlarmManager.setAlarmClock] is used rather than a plain exact alarm because it is the one kind
 * of alarm Android will not delay for battery: it is the call the phone's own clock app makes, and
 * it is what puts the little alarm symbol in the status bar. That symbol is a link, so it is given
 * somewhere to go — the launcher's own Clock hub, on the alarms page.
 *
 * An alarm has two bookings, not one. Its own — the next weekday, or the one moment a reminder is
 * for — and, while it is snoozed, the snooze. They are booked under different actions and request
 * codes so that one never replaces the other: snoozing a weekday alarm used to overwrite the next
 * morning's booking, and the morning after the snooze nothing rang.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Books the alarm's own next moment. Returns when it was booked for, or null when there was
     * nothing left to book (disabled, or a reminder whose moment has already passed).
     */
    fun schedule(alarm: Alarm): Long? {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return null
        }
        val now = System.currentTimeMillis()
        // The snooze has its own booking; this one is for the alarm's own time.
        val own = alarm.copy(snoozedUntilMillis = null)
        val triggerAt = own.dueAt(now)
        if (triggerAt == null) {
            cancel(alarm)
            return null
        }
        arm(alarm, triggerAt, isSnooze = false)
        if (alarm.isSnoozedAt(now)) arm(alarm, alarm.snoozedUntilMillis!!, isSnooze = true)
        return triggerAt
    }

    /** Puts a ringing alarm off until [untilMillis] without touching when it would next be due. */
    fun snooze(alarm: Alarm, untilMillis: Long) {
        arm(alarm, untilMillis, isSnooze = true)
    }

    /** Drops a pending snooze and nothing else. */
    fun cancelSnooze(alarm: Alarm) {
        alarmManager.cancel(pendingIntent(alarm.id, isSnooze = true))
    }

    private fun arm(alarm: Alarm, triggerAt: Long, isSnooze: Boolean) {
        val intent = fireIntent(alarm.id, isSnooze).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
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
            requestCode(alarm.id, isSnooze),
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

    /** Takes both of the alarm's bookings out of the system's clock. */
    fun cancel(alarm: Alarm) {
        alarmManager.cancel(pendingIntent(alarm.id, isSnooze = false))
        alarmManager.cancel(pendingIntent(alarm.id, isSnooze = true))
    }

    private fun fireIntent(alarmId: String, isSnooze: Boolean): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            // A distinct action per alarm and per booking, so no two ever collide into one
            // pending intent.
            action = (if (isSnooze) AlarmReceiver.ACTION_SNOOZE_FIRE else AlarmReceiver.ACTION_FIRE) + ".$alarmId"
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZE, isSnooze)
        }

    private fun pendingIntent(alarmId: String, isSnooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, isSnooze),
            fireIntent(alarmId, isSnooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(alarmId: String, isSnooze: Boolean): Int =
        if (isSnooze) Alarm.snoozeRequestCode(alarmId) else Alarm.requestCode(alarmId)

    /**
     * Whether the phone will let the launcher name an exact minute.
     *
     * SCHEDULE_EXACT_ALARM is the user's to give, and from Android 14 a new install starts without
     * it; the clock hub sends them to the switch. Until then alarms are booked without the exact
     * minute rather than not at all.
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
