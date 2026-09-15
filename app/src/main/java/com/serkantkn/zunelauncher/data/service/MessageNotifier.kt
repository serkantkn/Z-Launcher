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
import com.serkantkn.zunelauncher.util.SmsSender
import com.serkantkn.zunelauncher.util.ZuneLog
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
    private const val CHANNEL_ID = "zune_messages"
    private const val NOTIFICATION_BASE = 5200
    const val KEY_REPLY = "zune_message_reply"
    const val EXTRA_THREAD_ID = "thread_id"
    const val EXTRA_ADDRESS = "address"
    const val ACTION_REPLY = "com.serkantkn.zunelauncher.MESSAGE_REPLY"
    const val ACTION_MARK_READ = "com.serkantkn.zunelauncher.MESSAGE_MARK_READ"

    /**
     * Puts an arriving message up, unless the user is already reading that very conversation —
     * a notification for something on screen is noise.
     */
    fun showIncoming(context: Context, sender: String, body: String, threadId: Long) {
        if (!canPost(context)) return
        if (threadId > 0L && MessagingBridge.visibleThreadId == threadId) return
        ensureChannel(context)

        val name = displayName(context, sender)
        val person = Person.Builder().setName(name.ifBlank { context.getString(R.string.msg_unknown_sender) }).build()
        // The reader of a conversation needs a name too, and Android will not take an empty one.
        val self = Person.Builder().setName(context.getString(R.string.msg_me)).build()
        val style = NotificationCompat.MessagingStyle(self)
            .addMessage(body, System.currentTimeMillis(), person)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
                    context.getString(R.string.msg_mark_read),
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

    /** Takes a conversation's notification down, for when it has been read in the hub. */
    fun clear(context: Context, threadId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId(threadId))
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the message notification would not go away", e)
        }
    }

    private fun notificationId(threadId: Long): Int = NOTIFICATION_BASE + (threadId % 1000L).toInt()

    /** The sender's name when the address book knows them, and their number when it does not. */
    private fun displayName(context: Context, sender: String): String =
        SmsRepository.contactNameFor(context, sender)

    private fun replyAction(context: Context, threadId: Long, address: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel(context.getString(R.string.msg_type_hint))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_wp_message,
            context.getString(R.string.msg_reply),
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
        return PendingIntent.getBroadcast(context, (action + threadId).hashCode(), intent, flags)
    }

    private fun openThread(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_MESSAGE_THREAD, threadId)
        return PendingIntent.getActivity(
            context,
            threadId.toInt(),
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
                context.getString(R.string.msg_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.msg_channel_description)
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
                        SmsSender.send(context, address, reply, threadId)
                        SmsRepository.markThreadRead(context, threadId)
                        MessageNotifier.clear(context, threadId)
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
