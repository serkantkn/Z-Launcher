package com.serkantkn.zeta.data.service

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.serkantkn.zeta.BuildConfig
import com.serkantkn.zeta.data.model.ReplyAction
import com.serkantkn.zeta.data.model.SocialMessageModel
import com.serkantkn.zeta.data.model.isSocialWorthy
import com.serkantkn.zeta.data.model.looksSocial
import com.serkantkn.zeta.data.repository.SocialRepository
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place notifications enter the launcher.
 *
 * Two quite different jobs happen here and it is worth keeping them apart:
 *
 *  - **Counting.** Every app's notifications are counted, so a tile on the Start screen can carry
 *    a number. Nothing is taken away from the phone to do this.
 *  - **The Social hub.** Only the apps the user named as sources are kept: their message is
 *    held for the hub and shown as a Windows Phone banner. The system's own notification is left
 *    where it is — taking it away meant a message held only in this process's memory, gone with
 *    the next restart. Everything else is left exactly where Android put it, which is what makes
 *    this a social hub rather than a second notification drawer.
 *
 * In the free flavour the hub does not exist, so only the counting happens here.
 */
class SocialNotificationListener : NotificationListenerService() {

    companion object {
        var instance: SocialNotificationListener? = null
        private const val TAG = "SocialNotificationListener"
    }

    /** The app's own icon, which never changes and is asked for once per app rather than per message. */
    private val appIcons = ConcurrentHashMap<String, Bitmap>()

    /** Counting runs here: walking every active notification is not main-thread work. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        try {
            if (com.serkantkn.zeta.util.Pro.unlocked) {
                val active = activeNotifications ?: return
                active.forEach { sbn ->
                    // What was already in the shade when we connected has been seen; no banner.
                    if (isSocialSource(sbn)) processAndAddNotification(sbn, announce = false)
                }
            }
            updateAllNotificationCounts()
            refreshActionCenter()
        } catch (e: Exception) {
            ZuneLog.e(TAG, "onListenerConnected failed", e)
        }
    }

    /** The system drops the binding now and then; a stale instance would answer for nobody. */
    override fun onListenerDisconnected() {
        if (instance == this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // A message from another app, for a TV paired with this phone (data/link).
        runCatching { com.serkantkn.zeta.data.link.PhoneLinkAppMessages.onPosted(this, sbn) }
        if (com.serkantkn.zeta.util.Pro.unlocked) {
            if (isSocialSource(sbn)) {
                processAndAddNotification(sbn, announce = true)
            } else {
                noteIfItLooksSocial(sbn)
            }
        }
        updateAllNotificationCounts()
        refreshActionCenter()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Dismissed in the shade is dismissed in the hub: the two stay one list.
        if (com.serkantkn.zeta.util.Pro.unlocked) SocialRepository.removeMessage(sbn.key)
        updateAllNotificationCounts()
        refreshActionCenter()
    }

    /** The action center lists the whole shade, in both flavours; it is read off the main thread. */
    private fun refreshActionCenter() {
        scope.launch {
            runCatching { com.serkantkn.zeta.data.repository.ActionCenterNotifications.refresh(this@SocialNotificationListener) }
                .onFailure { ZuneLog.e(TAG, "the action center could not be refreshed", it) }
        }
    }

    // ════════════════════════════════════════════════════════════
    // WHAT BELONGS IN THE HUB
    // ════════════════════════════════════════════════════════════

    /** Whether this notification is a social message from an app the user asked the hub to watch. */
    private fun isSocialSource(sbn: StatusBarNotification): Boolean {
        if (!SocialRepository.isSource(sbn.packageName)) return false
        return isWorthShowing(sbn)
    }

    private fun isWorthShowing(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras
        val hasText = !textOf(extras).isNullOrBlank() || !titleOf(extras).isNullOrBlank()
        return isSocialWorthy(
            category = notification.category,
            isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            hasProgress = hasProgressBar(extras),
            hasText = hasText
        )
    }

    /**
     * Whether the notification actually carries a progress bar.
     *
     * Not `containsKey(EXTRA_PROGRESS)`: Android puts that key on very nearly every notification,
     * message or not, so asking the question that way turns the whole hub off. A bar is only a bar
     * when it has a length, or when it says it does not know its length.
     */
    private fun hasProgressBar(extras: android.os.Bundle?): Boolean {
        if (extras == null) return false
        return extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
    }

    /** An app that talks like a social app but is not a source is offered to the user, once. */
    private fun noteIfItLooksSocial(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (!looksSocial(notification.category)) return
        if (!isWorthShowing(sbn)) return
        SocialRepository.suggestSource(sbn.packageName)
    }

    private fun titleOf(extras: android.os.Bundle?): String? =
        extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

    private fun textOf(extras: android.os.Bundle?): String? =
        extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

    private fun processAndAddNotification(sbn: StatusBarNotification, announce: Boolean) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = titleOf(extras).orEmpty()
        val text = textOf(extras).orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appName = getAppName(sbn.packageName)
        var iconBitmap: Bitmap? = null

        try {
            val icon: Icon? = notification.getLargeIcon() ?: notification.smallIcon
            val drawable = icon?.loadDrawable(this)
            iconBitmap = drawableToBitmap(drawable)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the message's picture would not load", e)
        }

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
            openIntent = notification.contentIntent,
            appIcon = appIconOf(sbn.packageName),
            category = notification.category,
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        )

        SocialRepository.addOrUpdateMessage(messageModel, this, announce = announce)
    }

    private fun getAppName(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
    }

    private fun appIconOf(packageName: String): Bitmap? = appIcons.getOrPut(packageName) {
        val drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
        drawableToBitmap(drawable) ?: return null
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap

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

    /**
     * How many notifications each app has waiting, for the tile badges.
     *
     * The shade is the truth for every app except the sources, whose notifications this service has
     * taken out of it — those are counted from what the hub is holding instead.
     */
    private fun updateAllNotificationCounts() {
        scope.launch {
            try {
                val fromShade = (activeNotifications ?: emptyArray())
                    .filter { sbn ->
                        val n = sbn.notification
                        n != null &&
                            (n.flags and Notification.FLAG_ONGOING_EVENT) == 0 &&
                            (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                    }
                    .groupingBy { it.packageName }
                    .eachCount()

                val fromHub = SocialRepository.messages.value
                    .groupBy { it.packageName }
                    .mapValues { it.value.size }

                val counts = (fromShade.keys + fromHub.keys).associateWith { packageName ->
                    maxOf(fromShade[packageName] ?: 0, fromHub[packageName] ?: 0)
                }
                SocialRepository.updateNotificationCounts(counts)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "updateAllNotificationCounts failed", e)
            }
        }
    }
}
