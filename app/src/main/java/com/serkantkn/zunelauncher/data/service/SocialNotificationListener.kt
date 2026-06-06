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

class SocialNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val category = notification.category
        
        val isMessage = category == Notification.CATEGORY_MESSAGE ||
                category == Notification.CATEGORY_EMAIL ||
                category == Notification.CATEGORY_SOCIAL ||
                isKnownMessagingApp(sbn.packageName)

        if (!isMessage) return

        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

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

        SocialRepository.addOrUpdateMessage(messageModel)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        SocialRepository.removeMessage(sbn.key)
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
}
