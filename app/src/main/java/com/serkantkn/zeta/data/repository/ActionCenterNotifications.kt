package com.serkantkn.zeta.data.repository

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.ReplyAction
import com.serkantkn.zeta.data.service.SocialNotificationListener
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.localizedString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** One notification as the action center shows it. */
data class PanelNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val title: String,
    val text: String,
    /**
     * The line an app puts beside its name in Android's shade — the account a mail came to, the
     * kind of thing it is — which tells two notifications of one app apart.
     */
    val subText: String,
    val postTime: Long,
    /** False for ongoing ones — a call, a download, a running service — which cannot be swiped. */
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val reply: ReplyAction?,
    /** For the launcher's own notifications, the hub it speaks for (see NotificationSource). */
    val hub: HubType? = null
)

/**
 * The notifications of one app, newest first, as one group in the action center. The launcher's
 * own come in a group per hub, headed with the hub's name and glyph rather than "Zeta".
 */
data class PanelGroup(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val items: List<PanelNotification>,
    val hub: HubType? = null
) {
    val key: String get() = packageName + (hub?.let { "|" + it.name } ?: "")
}

/**
 * Everything in the notification shade, kept for the Windows Phone action center.
 *
 * The launcher's notification listener already sees every notification for the Social hub and
 * the tile counts; it hands the whole set here as well whenever it changes, and the action
 * center reads it from [groups]. Opening, dismissing and clearing go back through the same
 * listener, so the system's own record stays the one truth.
 */
object ActionCenterNotifications {

    private const val TAG = "ActionCenterNotifications"

    private val _groups = MutableStateFlow<List<PanelGroup>>(emptyList())
    val groups: StateFlow<List<PanelGroup>> = _groups.asStateFlow()

    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()

    /** Whether the launcher may see notifications at all (the listener is bound). */
    val isListening: Boolean get() = SocialNotificationListener.instance != null

    /** Re-reads the shade. Called by the listener off the main thread. */
    fun refresh(listener: NotificationListenerService) {
        val active = runCatching { listener.activeNotifications }.getOrNull() ?: return
        val context = listener.applicationContext
        val items = shown(active.toList()).mapNotNull { toItem(context, it) }
        _groups.value = group(items)
    }

    /** Asks the listener for a fresh read, if it is bound. */
    fun refreshNow() {
        SocialNotificationListener.instance?.let { refresh(it) }
    }

    /** Opens what the notification points at, and lets it go if it asks to be. */
    fun open(context: Context, item: PanelNotification) {
        val intent = item.contentIntent
        if (intent != null) {
            runCatching { intent.send(context, 0, null, null, null, null, launchOptions()) }
                .onFailure { ZuneLog.e(TAG, "the notification would not open", it) }
        }
        if (item.autoCancel && item.clearable) dismiss(item)
    }

    fun dismiss(item: PanelNotification) {
        runCatching { SocialNotificationListener.instance?.cancelNotification(item.key) }
        _groups.value = _groups.value.mapNotNull { group ->
            val left = group.items.filterNot { it.key == item.key }
            if (left.isEmpty()) null else group.copy(items = left)
        }
    }

    fun dismissGroup(group: PanelGroup) {
        group.items.filter { it.clearable }.forEach { dismiss(it) }
    }

    /** Clears everything that can be cleared; ongoing notifications stay, as in the shade. */
    fun clearAll() {
        runCatching { SocialNotificationListener.instance?.cancelAllNotifications() }
        _groups.value = _groups.value.mapNotNull { group ->
            val left = group.items.filterNot { it.clearable }
            if (left.isEmpty()) null else group.copy(items = left)
        }
    }

    /**
     * A notification is started from the launcher's accessibility service, which counts as a
     * background start. Since Android 14 the sender has to say that it allows the activity to
     * come up; the service is exempt from the limit, so saying so is enough.
     */
    private fun launchOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        options.setPendingIntentBackgroundActivityStartMode(mode)
        return options.toBundle()
    }

    // ── What the panel shows ──────────────────────────────────────────────────────

    /**
     * The notifications worth a row: not a group's summary when its children are there, not a
     * media player (the music card has that), and not one with nothing to say.
     */
    internal fun shown(active: List<StatusBarNotification>): List<StatusBarNotification> {
        val groupsWithChildren = active
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && it.notification.group != null }
            .map { it.packageName + "|" + it.notification.group }
            .toSet()
        return active.filter { sbn ->
            val n = sbn.notification ?: return@filter false
            val isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isSummary && (sbn.packageName + "|" + n.group) in groupsWithChildren) return@filter false
            if (n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return@filter false
            val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)
            val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)
            !(title.isNullOrBlank() && text.isNullOrBlank())
        }
    }

    /** Groups by app: the app with the newest notification first, newest first inside. */
    internal fun group(items: List<PanelNotification>): List<PanelGroup> =
        items.groupBy { it.packageName to it.hub }
            .map { (source, list) ->
                val sorted = list.sortedByDescending { it.postTime }
                PanelGroup(source.first, sorted.first().appName, sorted.first().appIcon, sorted, source.second)
            }
            .sortedByDescending { it.items.first().postTime }

    private fun toItem(context: Context, sbn: StatusBarNotification): PanelNotification? {
        val n = sbn.notification ?: return null
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val hub = if (sbn.packageName == context.packageName) {
            NotificationSource.hubOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId else null,
                n.category
            )
        } else null
        val hubName = hub?.let { context.localizedString(it.titleRes) }
        // The launcher's own say which hub they come from in their sub-text too, for Android's
        // shade; here the group heading says it already.
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            .let { sub ->
                when {
                    hubName == null -> sub
                    sub.equals(hubName, ignoreCase = true) -> ""
                    sub.startsWith("$hubName · ", ignoreCase = true) -> sub.substring(hubName.length + 3)
                    else -> sub
                }
            }
        return PanelNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = hubName ?: labelOf(context, sbn.packageName),
            appIcon = iconOf(context, sbn.packageName),
            title = title,
            text = text,
            subText = subText,
            postTime = sbn.postTime,
            clearable = sbn.isClearable,
            contentIntent = n.contentIntent,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            reply = replyOf(n),
            hub = hub
        )
    }

    /** The notification's own reply box, if it has one that takes free text. */
    private fun replyOf(n: Notification): ReplyAction? {
        val action = n.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: return null
        val input = action.remoteInputs.first { it.allowFreeFormInput }
        return ReplyAction(action.actionIntent, input.resultKey)
    }

    private fun labelOf(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun iconOf(context: Context, pkg: String): Bitmap? {
        icons[pkg]?.let { return it }
        val drawable = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
            val size = 96
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            }
        }
        icons[pkg] = bitmap
        return bitmap
    }

}
