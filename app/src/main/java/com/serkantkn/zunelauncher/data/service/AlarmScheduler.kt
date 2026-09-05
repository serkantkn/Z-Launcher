package com.serkantkn.zunelauncher.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.serkantkn.zunelauncher.data.model.Alarm
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            alarm.noteId?.let { putExtra(AlarmReceiver.EXTRA_NOTE_ID, it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // One-shot reminders carry an exact trigger time.
        alarm.exactTimeMillis?.let { calendar.timeInMillis = it }

        // If time has already passed today, schedule for next occurrence.
        if (alarm.exactTimeMillis == null && calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Handle specific days of week if any are selected
        if (alarm.daysOfWeek.isNotEmpty()) {
            while (!alarm.daysOfWeek.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(
                calendar.timeInMillis,
                null // Usually a pending intent to open the alarm manager app
            )
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            // Handle if permission is denied
            e.printStackTrace()
        }
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
