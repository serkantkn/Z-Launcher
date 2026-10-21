package com.serkantkn.zeta.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.serkantkn.zeta.data.model.CalendarEvent
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.nextReminderOccurrence

/**
 * Warning somebody about an event the launcher keeps itself.
 *
 * Events in a phone calendar do not come through here: their reminder is written onto the event
 * and whatever the person already uses to be reminded does the reminding. This is only for the
 * launcher's own events — a note's reminder, or anything written down on a phone whose calendar
 * permission was refused — which nothing else in the world knows about.
 *
 * A repeating event is booked one occurrence at a time: the next one whose warning is still to
 * come. When that rings, [EventReminderReceiver] asks for the one after it to be booked.
 *
 * It is booked as a plain exact alarm rather than an alarm clock: a meeting reminder is not worth
 * waking a sleeping phone for at the exact second, and it should not put an alarm symbol in the
 * status bar as though somebody had set an alarm.
 */
class EventReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Books the event's next warning. [event] is the record as stored: a series, not one week of it. */
    fun schedule(event: CalendarEvent) {
        cancel(event.id)
        if (!event.isLocal) return
        val minutes = event.reminderMinutes ?: return
        val now = System.currentTimeMillis()
        val next = nextReminderOccurrence(event, now) ?: return
        val fireAt = next.startMillis - minutes * 60_000L
        if (fireAt <= now) return

        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = "${EventReminderReceiver.ACTION_REMIND}.${event.id}"
            // The series' own id, so the receiver can find the record and book the next one.
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(EventReminderReceiver.EXTRA_TITLE, event.title)
            putExtra(EventReminderReceiver.EXTRA_START, next.startMillis)
            putExtra(EventReminderReceiver.EXTRA_LOCATION, event.location)
            putExtra(EventReminderReceiver.EXTRA_ALL_DAY, event.isAllDay)
            putExtra(EventReminderReceiver.EXTRA_REPEATS, event.isRepeating)
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

    /** Books every reminder again — after a reboot, a clock change, or on a cold start. */
    fun rescheduleAll(events: List<CalendarEvent>) {
        events.filter { it.isLocal && it.reminderMinutes != null }.forEach { schedule(it) }
    }

    private companion object {
        const val TAG = "EventReminderScheduler"
    }
}
