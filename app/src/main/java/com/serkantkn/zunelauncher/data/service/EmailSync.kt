package com.serkantkn.zunelauncher.data.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailFolder
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.RequestCodes
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Periodic inbox sync without WorkManager: an inexact repeating alarm wakes
 * [EmailSyncReceiver], which syncs every account's inbox and announces new mail through
 * [EmailNotifier]. Re-armed on boot by BootReceiver and whenever accounts change.
 */
class EmailSyncScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Arms (or disarms) the repeating sync using the shortest interval among the accounts. */
    suspend fun reschedule() {
        val accounts = context.appContainer.emailDataStore.accountsFlow.first()
        val minutes = accounts.filter { it.syncMinutes > 0 }.minOfOrNull { it.syncMinutes }
        val pending = pendingIntent()
        if (minutes == null) {
            alarmManager.cancel(pending)
            ZuneLog.d(TAG, "sync disarmed")
            return
        }
        val interval = minutes * 60_000L
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval,
            interval,
            pending
        )
        ZuneLog.d(TAG, "sync armed every $minutes min")
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, EmailSyncReceiver::class.java).setAction(ACTION_SYNC),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val TAG = "EmailSyncScheduler"
        private const val REQUEST_CODE = 4101
        const val ACTION_SYNC = "com.serkantkn.zunelauncher.action.EMAIL_SYNC"
    }
}

/** Background sync entry point (alarm). Runs the inbox sync for every account. */
class EmailSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EmailSyncScheduler.ACTION_SYNC) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncAllInboxes(context.applicationContext, notify = true)
            } catch (e: Exception) {
                ZuneLog.e("EmailSyncReceiver", "onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * Syncs the inbox of every account. Returns the new messages found; when [notify] is
         * true they are also announced through [EmailNotifier].
         */
        suspend fun syncAllInboxes(context: Context, notify: Boolean): List<Pair<EmailAccount, EmailMessage>> {
            val container = context.appContainer
            val accounts = container.emailDataStore.accountsFlow.first()
            // Every account at once, and all of them within the time a broadcast is given: three
            // slow servers in a row used to run past the receiver's deadline, and the process
            // was killed with the third still connecting. An account that does not answer in
            // time is simply tried again on the next alarm.
            val found = withTimeoutOrNull(SYNC_DEADLINE_MILLIS) {
                coroutineScope {
                    accounts.map { account ->
                        async {
                            try {
                                if (container.emailCache.loadFolders(account.id).isEmpty()) container.emailRepository.refreshFolders(account)
                                val result = container.emailRepository.syncFolder(account, EmailFolder.INBOX_NAME)
                                result.newMessages.map { account to it }
                            } catch (e: Exception) {
                                ZuneLog.w("EmailSyncReceiver", "sync failed for ${EmailNotifier.maskedAddress(account.email)}", e)
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
            } ?: run {
                ZuneLog.w("EmailSyncReceiver", "the inbox sync ran out of time")
                emptyList()
            }
            if (notify) found.filter { it.first.notificationsEnabled }.forEach { (account, message) ->
                EmailNotifier.announce(context, account, message)
            }
            return found
        }

        /** Under the ~30 s a receiver has before the system gives up on it. */
        private const val SYNC_DEADLINE_MILLIS = 25_000L
    }
}

/** New-mail announcements: Windows Phone toast (via SocialRepository) plus a system notification. */
object EmailNotifier {

    private const val CHANNEL_ID = "email_new_mail"

    /** "a…@example.com": enough to tell accounts apart in a log, not enough to write to. */
    fun maskedAddress(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return "…"
        return email.take(1) + "…" + email.substring(at)
    }

    /** Takes a message's new-mail notification down, for when it has been opened in the hub. */
    fun cancel(context: Context, message: EmailMessage) {
        runCatching {
            val manager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId(message))
        }
    }

    private fun notificationId(message: EmailMessage): Int = ("email:" + message.key).hashCode()

    fun announce(context: Context, account: EmailAccount, message: EmailMessage) {
        val appContext = context.applicationContext
        val openIntent = PendingIntent.getActivity(
            appContext,
            // One code per message: two mails from one account used to share a target when
            // their uids happened to agree after the old modulo.
            RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, account.id, message.folder, message.uid),
            Intent(appContext, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_EMAIL_ACCOUNT, account.id)
                putExtra(MainActivity.EXTRA_OPEN_EMAIL_FOLDER, message.folder)
                putExtra(MainActivity.EXTRA_OPEN_EMAIL_UID, message.uid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subject = message.subject.ifBlank { appContext.localizedString(R.string.email_no_subject) }

        // Windows Phone toast card (foreground banner or overlay), same path as app notifications.
        SocialRepository.addOrUpdateMessage(
            SocialMessageModel(
                id = "email:${message.key}",
                packageName = appContext.packageName,
                appName = appContext.localizedString(R.string.hub_email),
                title = message.from.display,
                text = subject + if (message.snippet.isNotBlank()) " · ${message.snippet}" else "",
                timestamp = message.date.takeIf { it > 0 } ?: System.currentTimeMillis(),
                icon = null,
                replyAction = null,
                openIntent = openIntent
            ),
            appContext
        )

        // System notification (Android 13+ needs the runtime permission).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, appContext.localizedString(R.string.hub_email), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wp_mail)
            .setContentTitle(message.from.display)
            .setContentText(subject)
            .setSubText(account.email)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setGroup("email_${account.id}")
            .build()
        manager.notify(notificationId(message), notification)
    }
}
