package com.serkantkn.zunelauncher.data.service

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
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.ClockPage
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * The running timer, in the shade.
 *
 * Android can count a notification down by itself given the moment it ends, so the shade stays
 * right to the second without the launcher being awake to update it — which is the point, since a
 * timer is nearly always set and then left alone while something else is on screen.
 *
 * Pausing and cancelling are offered here too: a timer you cannot stop without first finding the
 * launcher is a timer you end up stopping by silencing the phone.
 */
object TimerNotifier {

    private const val TAG = "TimerNotifier"
    private const val CHANNEL_ID = "zune_timer"
    private const val NOTIFICATION_ID = 7410

    /** Shows (or updates) the countdown. */
    fun showRunning(context: Context, endsAtMillis: Long, label: String) {
        if (!canPost(context)) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wp_timer)
            .setContentTitle(label.ifBlank { context.getString(R.string.clock_tab_timer) })
            .setContentText(context.getString(R.string.clock_timer_running))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAtMillis)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openTimerPage(context))
            .addAction(0, context.getString(R.string.common_pause), broadcast(context, TimerReceiver.ACTION_PAUSE))
            .addAction(0, context.getString(R.string.common_cancel), broadcast(context, TimerReceiver.ACTION_CANCEL))
            .build()

        post(context, notification)
    }

    /** Takes the countdown back down: paused, cancelled, or finished and answered. */
    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun post(context: Context, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the timer would not show in the shade", e)
        }
    }

    private fun openTimerPage(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.CLOCK.name)
            putExtra(MainActivity.EXTRA_OPEN_CLOCK_PAGE, ClockPage.TIMER.name)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun broadcast(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, TimerReceiver::class.java).apply { this.action = action },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.clock_tab_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
