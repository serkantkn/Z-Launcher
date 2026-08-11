package com.serkantkn.zunelauncher.data.service

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.serkantkn.zunelauncher.data.model.ReplyAction
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class SocialNotificationListener : NotificationListenerService() {

    companion object {
        var instance: SocialNotificationListener? = null
    }

    private val selfCanceledKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        try {
            val active = activeNotifications ?: return
            active.forEach { sbn ->
                if (isNotificationValid(sbn) && SocialRepository.isAppNotificationAllowed(sbn.packageName)) {
                    SocialRepository.markAsShown(sbn.key)
                    processAndAddNotification(sbn)
                }
            }
            updateAllNotificationCounts()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isNotificationValid(sbn) && SocialRepository.isAppNotificationAllowed(sbn.packageName)) {
            processAndAddNotification(sbn)
            // Immediately cancel system notification so Android's native heads-up notification card does not show
            try {
                selfCanceledKeys.add(sbn.key)
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateAllNotificationCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (selfCanceledKeys.remove(sbn.key)) {
            // Canceled by us to replace Android notification card with Windows Phone toast
            // Keep in SocialRepository so live tile count & Social Hub & Toast card continue to work
            updateAllNotificationCounts()
            return
        }
        SocialRepository.removeMessage(sbn.key)
        updateAllNotificationCounts()
    }

    private fun processAndAddNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = getAppName(sbn.packageName)
        var iconBitmap: Bitmap? = null

        try {
            val icon: Icon? = notification.getLargeIcon() ?: notification.smallIcon
            val drawable = icon?.loadDrawable(this)
            iconBitmap = drawableToBitmap(drawable)
        } catch (e: Exception) {}

        var replyActionModel: ReplyAction? = null
        for (action in notification.actions ?: emptyArray()) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null) {
                for (remoteInput in remoteInputs) {
                    if (remoteInput.allowFreeFormInput) {
                        replyActionModel = ReplyAction(
                            pendingIntent = action.actionIntent,
                            remoteInputResultKey = remoteInput.resultKey
                        )
                        break
                    }
                }
            }
            if (replyActionModel != null) break
        }

        val messageModel = SocialMessageModel(
            id = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime,
            icon = iconBitmap,
            replyAction = replyActionModel,
            openIntent = notification.contentIntent
        )

        SocialRepository.addOrUpdateMessage(messageModel, this)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun isKnownMessagingApp(packageName: String): Boolean {
        val knownApps = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.twitter.android",
            "com.instagram.android",
            "com.google.android.apps.messaging",
            "com.google.android.gm"
        )
        return knownApps.contains(packageName)
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun updateAllNotificationCounts() {
        try {
            val counts = SocialRepository.messages.value
                .groupBy { it.packageName }
                .mapValues { it.value.size }
            SocialRepository.updateNotificationCounts(counts)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isNotificationValid(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isGroupHeader = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return !isOngoing && !isGroupHeader
    }
}
