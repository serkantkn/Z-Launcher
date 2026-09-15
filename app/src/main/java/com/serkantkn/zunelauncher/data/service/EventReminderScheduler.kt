package com.serkantkn.zunelauncher.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * Warning somebody about an event the launcher keeps itself.
 *
 * Events in a phone calendar do not come through here: their reminder is written onto the event
 * and whatever the person already uses to be reminded does the reminding. This is only for the
 * launcher's own events — a note's reminder, or anything written down on a phone whose calendar
 * permission was refused — which nothing else in the world knows about.
 *
 * It is booked as a plain exact alarm rather than an alarm clock: a meeting reminder is not worth
 * waking a sleeping phone for at the exact second, and it should not put an alarm symbol in the
 * status bar as though somebody had set an alarm.
 */
class EventReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(event: CalendarEvent) {
        cancel(event.id)
        val minutes = event.reminderMinutes ?: return
        if (!event.isLocal) return
        val fireAt = event.startMillis - minutes * 60_000L
        if (fireAt <= System.currentTimeMillis()) return

        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = "${EventReminderReceiver.ACTION_REMIND}.${event.id}"
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(EventReminderReceiver.EXTRA_TITLE, event.title)
            putExtra(EventReminderReceiver.EXTRA_START, event.startMillis)
            putExtra(EventReminderReceiver.EXTRA_LOCATION, event.location)
            putExtra(EventReminderReceiver.EXTRA_ALL_DAY, event.isAllDay)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
        }.onFailure {
            ZuneLog.w(TAG, "the reminder could not be booked exactly; asking for a window", it)
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent) }
        }
    }

    fun cancel(eventId: String) {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = "${EventReminderReceiver.ACTION_REMIND}.$eventId"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** Books every reminder again — after a reboot, or on a cold start. */
    fun rescheduleAll(events: List<CalendarEvent>) {
        events.filter { it.isLocal && it.reminderMinutes != null }.forEach { schedule(it) }
    }

    private companion object {
        const val TAG = "EventReminderScheduler"
    }
}
