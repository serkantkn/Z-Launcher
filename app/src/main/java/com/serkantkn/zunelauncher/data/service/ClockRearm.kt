package com.serkantkn.zunelauncher.data.service

import android.content.Context
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.first

/**
 * Everything the launcher has booked with the system's clock, booked again.
 *
 * AlarmManager forgets every alarm on a reboot, a force-stop and an update, and it keeps ringing
 * at the old wall-clock moment when the time or the zone changes under it. The same walk over the
 * records fixes all of those, so it lives here once and is called from [BootReceiver],
 * [TimeChangeReceiver] and the Application's cold start.
 *
 * It is also the one place that notices an alarm that never rang. A one-shot alarm whose moment
 * went by while the phone was off is not booked again for tomorrow — that would ring a day late
 * and say nothing about it. It is switched off and the owner is told it was missed.
 */
object ClockRearm {

    private const val TAG = "ClockRearm"

    /** Books alarms, note reminders, event reminders and a running timer again. */
    suspend fun rearmAll(context: Context, reason: String) {
        val app = context.applicationContext
        val container = app.appContainer
        val now = System.currentTimeMillis()
        ZuneLog.d(TAG, "re-arming the clock: $reason")

        runCatching { rearmAlarms(app, now) }
            .onFailure { ZuneLog.e(TAG, "the alarms would not re-arm", it) }

        runCatching {
            // The launcher's own events carry their own reminders, which nothing else would set.
            val events = container.calendarDataStore.eventsFlow.first()
            container.eventReminderScheduler.rescheduleAll(events)
        }.onFailure { ZuneLog.e(TAG, "the event reminders would not re-arm", it) }

        runCatching { rearmTimer(app, now) }
            .onFailure { ZuneLog.e(TAG, "the timer would not re-arm", it) }
    }

    private suspend fun rearmAlarms(context: Context, now: Long) {
        val container = context.appContainer
        val store = container.alarmDataStore
        val scheduler = container.alarmScheduler
        val alarms = store.alarmsFlow.first()
        val settled = mutableListOf<Alarm>()
        var changed = false

        alarms.forEach { alarm ->
            if (!alarm.isEnabled) {
                settled += alarm
                return@forEach
            }
            if (alarm.isMissedAt(now)) {
                ZuneLog.w(TAG, "alarm ${alarm.id} was missed; switching it off")
                scheduler.cancel(alarm)
                AlarmNotifier.showMissed(context, alarm)
                changed = true
                if (alarm.noteId != null) {
                    // A note's reminder has nothing to stay switched off for: drop the record.
                    return@forEach
                }
                settled += alarm.copy(isEnabled = false, snoozedUntilMillis = null, bookedForMillis = null)
                return@forEach
            }
            val booked = scheduler.schedule(alarm)
            if (booked != alarm.bookedForMillis) changed = true
            settled += alarm.copy(bookedForMillis = booked)
        }

        if (changed || settled.size != alarms.size) store.saveAlarms(settled)
    }

    private suspend fun rearmTimer(context: Context, now: Long) {
        val container = context.appContainer
        // A countdown booked before the phone went off is gone from the system's clock too.
        val timer = container.clockDataStore.timer.first()
        if (!timer.isRunning) return
        if (timer.endsAtMillis > now) {
            container.timerScheduler.schedule(timer.endsAtMillis)
            TimerNotifier.showRunning(context, timer.endsAtMillis, timer.label)
        } else {
            // It ran out while the phone was off; there is nothing left to ring about.
            container.clockDataStore.saveTimer(
                timer.copy(isRunning = false, endsAtMillis = 0L, remainingMillis = 0L)
            )
            TimerNotifier.clear(context)
        }
    }
}
