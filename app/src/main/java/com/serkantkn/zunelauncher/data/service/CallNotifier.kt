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
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * The way back to a call the launcher is not currently showing.
 *
 * A call can arrive while another app is in front, or while the screen is off. The launcher's own
 * call screen only exists inside the launcher, so the notification is what carries the call
 * everywhere else: it goes up as a full screen intent, which is what wakes the screen and puts the
 * call in front, and it stays for as long as the call lasts so there is always a way back to it.
 */
object CallNotifier {

    private const val TAG = "CallNotifier"
    private const val CHANNEL_ID = "zune_calls"
    const val NOTIFICATION_ID = 4201

    const val ACTION_ANSWER = "com.serkantkn.zunelauncher.CALL_ANSWER"
    const val ACTION_DECLINE = "com.serkantkn.zunelauncher.CALL_DECLINE"

    /**
     * Builds the call's notification, or null when there is no call to show.
     *
     * Posting it is the service's job, not this object's, because Android will not accept a
     * CallStyle notification from just anywhere: a ringing call is allowed through on the strength
     * of its full screen intent, and a call in progress only as a foreground service. Building and
     * posting are kept apart so the service can do whichever of those the call calls for.
     */
    fun build(context: Context, status: CallStatus, caller: String): Notification? {
        if (!canPost(context)) return null
        ensureChannel(context)

        val person = Person.Builder().setName(caller).setImportant(true).build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wp_phone)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall(context))

        when (status) {
            CallStatus.INCOMING -> builder
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        action(context, ACTION_DECLINE),
                        action(context, ACTION_ANSWER)
                    )
                )
                // This is what puts a ringing call in front of whatever else is on screen.
                .setFullScreenIntent(openCall(context), true)

            CallStatus.OUTGOING, CallStatus.ACTIVE -> builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(person, action(context, ACTION_DECLINE))
            )

            CallStatus.IDLE, CallStatus.ENDED -> return null
        }
        return builder.build()
    }

    /** Puts a notification up without a foreground service; only a ringing call may do this. */
    fun post(context: Context, notification: Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        true
    } catch (e: Exception) {
        ZuneLog.w(TAG, "not allowed to put the call up", e)
        false
    }

    fun clear(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the call notification would not go away", e)
        }
    }

    /** Whether the phone will show a full screen call at all, so the caller can warn if not. */
    fun canRingThrough(context: Context): Boolean {
        if (!canPost(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.call_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.call_channel_description)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        )
    }

    private fun openCall(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Answering and turning down a call from the notification, for when the call screen is not what
 * the user is looking at.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifier.ACTION_ANSWER -> CallManager.answerCall()
            CallNotifier.ACTION_DECLINE -> CallManager.declineCall()
        }
    }
}
