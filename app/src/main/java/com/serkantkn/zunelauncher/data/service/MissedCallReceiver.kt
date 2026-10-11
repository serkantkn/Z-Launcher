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
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.util.RequestCodes
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The phone app's missed calls, as Telecom counts them.
 *
 * While the launcher is the phone app the system stops posting its own missed-call notification
 * and sends this broadcast instead, with the count and the last number. A count of nought means
 * the calls have been seen — the history was opened — and the notification comes down.
 */
class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
        val appContext = context.applicationContext

        if (count <= 0) {
            runCatching { NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID) }
            return
        }
        if (!canPost(appContext)) return

        // The name lookup is a provider query, which does not belong on the receiver's thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val who = if (number.isBlank()) {
                    appContext.localizedString(R.string.phone_private_number)
                } else {
                    SmsRepository.contactNameFor(appContext, number)
                }
                post(appContext, count, who)
            } catch (e: Exception) {
                ZuneLog.w(TAG, "the missed call could not be announced", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun post(context: Context, count: Int, who: String) {
        ensureChannel(context)
        val title = if (count == 1) {
            context.localizedString(R.string.phone_missed_call)
        } else {
            context.resources.getQuantityString(R.plurals.phone_missed_calls, count, count)
        }
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.PHONE.name)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSubText(context.localizedString(R.string.hub_phone))
            .setSmallIcon(R.drawable.ic_wp_phone)
            .setContentTitle(title)
            .setContentText(who)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    RequestCodes.of(RequestCodes.Kind.CALL, "missed"),
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure { ZuneLog.w(TAG, "not allowed to put the missed call up", it) }
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
                context.localizedString(R.string.phone_missed_call),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    internal companion object {
        const val TAG = "MissedCallReceiver"
        const val CHANNEL_ID = "zune_missed_calls"
        const val NOTIFICATION_ID = 4202
    }
}
