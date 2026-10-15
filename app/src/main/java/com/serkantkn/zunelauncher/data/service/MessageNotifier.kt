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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.util.RequestCodes
import com.serkantkn.zunelauncher.util.SendOutcome
import com.serkantkn.zunelauncher.util.SmsSender
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Telling somebody a message arrived, and letting them answer without leaving what they are doing.
 *
 * One notification per conversation, so two people writing at once do not overwrite each other,
 * and the conversation's own messages are carried in the notification itself — which is what makes
 * the reply box appear and what keeps the thread readable as more arrive.
 */
object MessageNotifier {

    private const val TAG = "MessageNotifier"
    internal const val CHANNEL_ID = "zune_messages"
    const val KEY_REPLY = "zune_message_reply"
    const val EXTRA_THREAD_ID = "thread_id"
    const val EXTRA_ADDRESS = "address"
    const val ACTION_REPLY = "com.serkantkn.zunelauncher.MESSAGE_REPLY"
    const val ACTION_MARK_READ = "com.serkantkn.zunelauncher.MESSAGE_MARK_READ"

    /** One line of a conversation's notification. */
    private data class Line(val sender: String, val body: String, val at: Long)

    /**
     * The unread lines of each conversation, so a second message joins the first in the shade
     * instead of replacing it. In memory only: a process restart loses the earlier lines, and
     * the notification then starts again from the newest — which is the state it was in before.
     */
    private val unread = mutableMapOf<Long, MutableList<Line>>()
    private const val LINES_KEPT = 5

    /**
     * Puts an arriving message up, unless the user is already reading that very conversation —
     * a notification for something on screen is noise.
     */
    fun showIncoming(context: Context, sender: String, body: String, threadId: Long) {
        if (!canPost(context)) return
        if (threadId > 0L && MessagingBridge.visibleThreadId == threadId) return
        ensureChannel(context)

        val name = displayName(context, sender)
        val lines = synchronized(unread) {
            val list = unread.getOrPut(threadId) { mutableListOf() }
            list += Line(name, body, System.currentTimeMillis())
            while (list.size > LINES_KEPT) list.removeAt(0)
            list.toList()
        }

        // The reader of a conversation needs a name too, and Android will not take an empty one.
        val self = Person.Builder().setName(context.localizedString(R.string.msg_me)).build()
        val unknown = context.localizedString(R.string.msg_unknown_sender)
        val style = NotificationCompat.MessagingStyle(self)
        lines.forEach { line ->
            val person = Person.Builder().setName(line.sender.ifBlank { unknown }).build()
            style.addMessage(line.body, line.at, person)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSubText(context.localizedString(R.string.hub_messaging))
            .setSmallIcon(R.drawable.ic_wp_message)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openThread(context, threadId))
            .addAction(replyAction(context, threadId, sender))
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_wp_message,
                    context.localizedString(R.string.msg_mark_read),
                    broadcast(context, ACTION_MARK_READ, threadId, sender)
                ).build()
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId(threadId), notification)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "not allowed to show the message", e)
        }
    }

    /**
     * A picture message has arrived, and the launcher cannot show it. It says so, and the tap
     * opens the conversation — where the message's text, if any, is — rather than nothing.
     */
    fun showIncomingMms(context: Context, sender: String, threadId: Long) {
        if (!canPost(context)) return
        if (threadId > 0L && MessagingBridge.visibleThreadId == threadId) return
        ensureChannel(context)

        val name = displayName(context, sender).ifBlank { context.localizedString(R.string.msg_unknown_sender) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSubText(context.localizedString(R.string.hub_messaging))
            .setSmallIcon(R.drawable.ic_wp_message)
            .setContentTitle(name)
            .setContentText(context.localizedString(R.string.msg_mms_arrived))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openThread(context, threadId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(threadId), notification)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "not allowed to show the picture message", e)
        }
    }

    /**
     * A text has reached the other phone. Said quietly and briefly - the tick on the message says
     * it for good - and in a channel of its own, so it can be silenced without silencing messages.
     */
    fun showDelivered(context: Context, address: String, threadId: Long) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DELIVERED_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    DELIVERED_CHANNEL_ID,
                    context.localizedString(R.string.msg_delivered_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val name = displayName(context, address).ifBlank { address }
        val notification = NotificationCompat.Builder(context, DELIVERED_CHANNEL_ID)
            .setSubText(context.localizedString(R.string.hub_messaging))
            .setSmallIcon(R.drawable.ic_wp_message)
            .setContentTitle(name)
            .setContentText(context.localizedString(R.string.msg_delivered_notice))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(DELIVERED_SHOWN_MILLIS)
            .setContentIntent(openThread(context, threadId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DELIVERED_NOTIFICATION_BASE + (threadId % 100_000L).toInt(), notification)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "not allowed to say the message arrived", e)
        }
    }

    private const val DELIVERED_CHANNEL_ID = "zune_messages_delivered"
    private const val DELIVERED_NOTIFICATION_BASE = 1_250_000_000
    private const val DELIVERED_SHOWN_MILLIS = 8_000L

    /** Takes a conversation's notification down, for when it has been read in the hub. */
    fun clear(context: Context, threadId: Long) {
        synchronized(unread) { unread.remove(threadId) }
        try {
            NotificationManagerCompat.from(context).cancel(notificationId(threadId))
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the message notification would not go away", e)
        }
    }

    /**
     * One id per conversation, and never the same one for two.
     *
     * Thread numbers are a provider's autoincrement, so they are small; shifted up past every
     * other id the launcher uses they still fit an Int with room for a billion conversations.
     * The old `threadId % 1000` put conversations 12 and 1012 on one notification.
     */
    internal fun notificationId(threadId: Long): Int =
        NOTIFICATION_BASE + (threadId.coerceAtLeast(0L) % THREAD_ID_SPAN).toInt()

    private const val NOTIFICATION_BASE = 1_100_000_000
    private const val THREAD_ID_SPAN = 1_000_000_000L

    /** The sender's name when the address book knows them, and their number when it does not. */
    private fun displayName(context: Context, sender: String): String =
        SmsRepository.contactNameFor(context, sender)

    private fun replyAction(context: Context, threadId: Long, address: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel(context.localizedString(R.string.msg_type_hint))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_wp_message,
            context.localizedString(R.string.msg_reply),
            broadcast(context, ACTION_REPLY, threadId, address, mutable = true)
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun broadcast(
        context: Context,
        action: String,
        threadId: Long,
        address: String,
        mutable: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, MessageActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_THREAD_ID, threadId)
            .putExtra(EXTRA_ADDRESS, address)
        // A reply's pending intent has to be mutable: the typed text is put into it by the system.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        val kind = if (action == ACTION_REPLY) RequestCodes.Kind.SMS_REPLY else RequestCodes.Kind.SMS_MARK_READ
        return PendingIntent.getBroadcast(context, RequestCodes.of(kind, threadId), intent, flags)
    }

    private fun openThread(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_MESSAGE_THREAD, threadId)
        return PendingIntent.getActivity(
            context,
            RequestCodes.of(RequestCodes.Kind.SMS_OPEN, threadId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
                context.localizedString(R.string.msg_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.localizedString(R.string.msg_channel_description)
                setShowBadge(true)
            }
        )
    }
}

/**
 * Answering a message, or waving it away, from the notification itself.
 *
 * The work outlives the broadcast, so it is done on a scope of its own rather than on the few
 * milliseconds a receiver is given.
 */
class MessageActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MessageNotifier.EXTRA_THREAD_ID, 0L)
        val address = intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()

        when (intent.action) {
            MessageNotifier.ACTION_REPLY -> {
                val reply = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(MessageNotifier.KEY_REPLY)
                    ?.toString()
                    .orEmpty()
                if (reply.isBlank() || address.isBlank()) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val outcome = SmsSender.send(context, address, reply, threadId)
                        // A reply that did not leave is no reason to take the message down: the
                        // notification is the only sign the user has that there is still something
                        // to answer.
                        if (outcome == SendOutcome.HANDED_OVER) {
                            SmsRepository.markThreadRead(context, threadId)
                            MessageNotifier.clear(context, threadId)
                        } else {
                            ZuneLog.w("MessageActionReceiver", "the reply from the notification did not go: $outcome")
                        }
                        MessagingBridge.messagesChanged()
                    } finally {
                        pending.finish()
                    }
                }
            }

            MessageNotifier.ACTION_MARK_READ -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SmsRepository.markThreadRead(context, threadId)
                        MessageNotifier.clear(context, threadId)
                        MessagingBridge.messagesChanged()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
