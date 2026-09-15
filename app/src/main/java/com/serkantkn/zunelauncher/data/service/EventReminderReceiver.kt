package com.serkantkn.zunelauncher.data.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.util.ZuneLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The reminder itself: a notification, not an alarm screen.
 *
 * An event is not an alarm. Something that takes over the whole phone and has to be dismissed is
 * right for the thing that wakes you up and wrong for a note that a meeting starts in ten minutes,
 * which you want to see and then carry on with what you were doing.
 */
class EventReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val start = intent.getLongExtra(EXTRA_START, 0L)
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        val isAllDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false)

        if (!canPost(context)) return
        ensureChannel(context)

        val whenText = if (isAllDay) {
            context.getString(R.string.cal_all_day)
        } else {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))
        }
        val text = listOfNotNull(whenText, location.takeIf { it.isNotBlank() }).joinToString(" • ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wp_calendar)
            .setContentTitle(title.ifBlank { context.getString(R.string.hub_calendar) })
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(start)
            .setShowWhen(true)
            .setContentIntent(openCalendar(context, eventId))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        }.onFailure { ZuneLog.w(TAG, "the reminder would not show", it) }
    }

    private fun openCalendar(context: Context, eventId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.CALENDAR.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.hub_calendar),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_REMIND = "com.serkantkn.zunelauncher.EVENT_REMIND"
        const val EXTRA_EVENT_ID = "EVENT_ID"
        const val EXTRA_TITLE = "TITLE"
        const val EXTRA_START = "START"
        const val EXTRA_LOCATION = "LOCATION"
        const val EXTRA_ALL_DAY = "ALL_DAY"
        private const val CHANNEL_ID = "zune_events"
        private const val TAG = "EventReminderReceiver"
    }
}
