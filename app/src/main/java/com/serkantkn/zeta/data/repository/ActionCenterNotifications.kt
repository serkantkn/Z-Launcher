package com.serkantkn.zeta.data.repository

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
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
    val hub: HubType? = null,
    /** The notification's own buttons - "mark as read", "archive" - other than its reply box; at most three. */
    val actions: List<PanelAction> = emptyList(),
    /** Who it is from, or what it is about: the picture the app puts beside it (a person's, usually). */
    val avatar: Bitmap? = null,
    /** The picture a notification carries whole (a photo sent, a screenshot taken). */
    val picture: Bitmap? = null,
    /** A conversation's last few messages, sender and words, oldest first. */
    val messages: List<PanelMessage> = emptyList(),
    /** How far along a download or upload is, 0-1; -1 for "under way, length unknown"; null for none. */
    val progress: Float? = null,
    /** Android ranks it low or silent: it goes in the folded group at the foot. */
    val silent: Boolean = false,
    /** A conversation with a person: it stays at the top. */
    val conversation: Boolean = false,
    /** The app's channel, for its notification settings. */
    val channelId: String? = null
)

/** One of a notification's own buttons. */
data class PanelAction(val title: String, val intent: PendingIntent)

/** One message of a conversation. */
data class PanelMessage(val sender: String, val text: String)

/**
 * The notifications of one app, newest first, as one group in the action center. The launcher's
 * own come in a group per hub, headed with the hub's name and glyph rather than "Zeta".
 */
data class PanelGroup(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val items: List<PanelNotification>,
    val hub: HubType? = null,
    /** Every notification in it is one Android ranks low: it sits in the folded group at the foot. */
    val silent: Boolean = false
) {
    val key: String get() = packageName + (hub?.let { "|" + it.name } ?: "") + (if (silent) "|silent" else "")

    /** It holds a conversation with someone: it stays above the rest. */
    val conversation: Boolean get() = items.any { it.conversation }
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

    /** Cleared from the panel but not yet from the phone: "undo" can still bring them back. */
    private val _pendingClear = MutableStateFlow<Set<String>>(emptySet())
    val pendingClear: StateFlow<Set<String>> = _pendingClear.asStateFlow()
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val commitClear by lazy { Runnable { commitPendingClear() } }

    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()

    /** Whether the launcher may see notifications at all (the listener is bound). */
    val isListening: Boolean get() = SocialNotificationListener.instance != null

    /** Re-reads the shade. Called by the listener off the main thread. */
    fun refresh(listener: NotificationListenerService) {
        val active = runCatching { listener.activeNotifications }.getOrNull() ?: return
        val context = listener.applicationContext
        val ranking = runCatching { listener.currentRanking }.getOrNull()
        val shown = shown(active.toList())
        val items = shown.mapNotNull { toItem(context, it, ranking) }
        // Pictures of notifications that have gone are let go.
        val keys = shown.map { it.key }.toSet()
        pictures.keys.retainAll(keys)
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

    /** Clears a group, after a moment in which it can be undone. */
    fun dismissGroup(group: PanelGroup) {
        clearLater(group.items.filter { it.clearable }.map { it.key })
    }

    /** Clears everything that can be cleared, after a moment in which it can be undone; ongoing ones stay. */
    fun clearAll() {
        clearLater(_groups.value.flatMap { g -> g.items.filter { it.clearable }.map { it.key } })
    }

    /** Takes [keys] off the panel now and off the phone in [UNDO_MILLIS], unless undone first. */
    private fun clearLater(keys: List<String>) {
        if (keys.isEmpty()) return
        main.removeCallbacks(commitClear)
        _pendingClear.value = _pendingClear.value + keys
        main.postDelayed(commitClear, UNDO_MILLIS)
    }

    /** Brings back what was just cleared. */
    fun undoClear() {
        main.removeCallbacks(commitClear)
        _pendingClear.value = emptySet()
    }

    /** Clears now what is waiting to be cleared: the time for undo is over, or the panel went away. */
    fun commitPendingClear() {
        main.removeCallbacks(commitClear)
        val keys = _pendingClear.value
        if (keys.isEmpty()) return
        val listener = SocialNotificationListener.instance
        keys.forEach { key -> runCatching { listener?.cancelNotification(key) } }
        _groups.value = _groups.value.mapNotNull { group ->
            val left = group.items.filterNot { it.key in keys }
            if (left.isEmpty()) null else group.copy(items = left)
        }
        _pendingClear.value = emptySet()
    }

    /** Puts a notification away for [millis]; Android brings it back then. */
    fun snooze(item: PanelNotification, millis: Long) {
        runCatching { SocialNotificationListener.instance?.snoozeNotification(item.key, millis) }
            .onFailure { ZuneLog.e(TAG, "the notification could not be snoozed", it) }
        dismissLocally(item)
    }

    /** Presses one of the notification's own buttons. */
    fun press(context: Context, item: PanelNotification, action: PanelAction) {
        runCatching { action.intent.send(context, 0, null, null, null, null, launchOptions()) }
            .onFailure { ZuneLog.e(TAG, "the notification's button did nothing", it) }
    }

    /** The app's own settings for this kind of notification (its channel), or for all of them. */
    fun openSettings(context: Context, item: PanelNotification) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && item.channelId != null) {
            android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, item.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, item.channelId)
        } else {
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, item.packageName)
        }
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { ZuneLog.e(TAG, "the notification settings would not open", it) }
    }

    private fun dismissLocally(item: PanelNotification) {
        _groups.value = _groups.value.mapNotNull { group ->
            val left = group.items.filterNot { it.key == item.key }
            if (left.isEmpty()) null else group.copy(items = left)
        }
    }

    /** How long "undo" is offered after clearing. */
    const val UNDO_MILLIS = 4_000L

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

    /**
     * Groups by app, newest first inside. Conversations with people come first, then the rest by
     * their newest notification, and last - each app's apart - what Android ranks as silent.
     */
    internal fun group(items: List<PanelNotification>): List<PanelGroup> =
        items.groupBy { Triple(it.packageName, it.hub, it.silent) }
            .map { (source, list) ->
                val sorted = list.sortedByDescending { it.postTime }
                PanelGroup(source.first, sorted.first().appName, sorted.first().appIcon, sorted, source.second, source.third)
            }
            .sortedWith(
                compareBy<PanelGroup> { it.silent }
                    .thenByDescending { it.conversation }
                    .thenByDescending { it.items.first().postTime }
            )

    private fun toItem(
        context: Context,
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.RankingMap?
    ): PanelNotification? {
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
            hub = hub,
            actions = actionsOf(n),
            avatar = picturesOf(context, sbn).first,
            picture = picturesOf(context, sbn).second,
            messages = messagesOf(n),
            progress = progressOf(n),
            silent = rankOf(ranking, sbn)?.let { r ->
                r.importance in 1..NotificationManager.IMPORTANCE_LOW ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && r.isAmbient)
            } ?: false,
            conversation = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && rankOf(ranking, sbn)?.isConversation == true) ||
                n.category == Notification.CATEGORY_MESSAGE || n.extras?.containsKey(Notification.EXTRA_MESSAGES) == true,
            channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId else null
        )
    }

    private fun rankOf(map: NotificationListenerService.RankingMap?, sbn: StatusBarNotification): NotificationListenerService.Ranking? {
        map ?: return null
        val r = NotificationListenerService.Ranking()
        return if (map.getRanking(sbn.key, r)) r else null
    }

    /** The buttons that do something by themselves; the reply box has its own place. */
    private fun actionsOf(n: Notification): List<PanelAction> =
        n.actions.orEmpty()
            .filter { a -> a.remoteInputs.isNullOrEmpty() && a.actionIntent != null && !a.title.isNullOrBlank() }
            .take(MAX_ACTIONS)
            .map { PanelAction(it.title.toString(), it.actionIntent) }

    /** A conversation's last messages; empty for anything else. */
    private fun messagesOf(n: Notification): List<PanelMessage> {
        val raw = n.extras?.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return raw.mapNotNull { (it as? Bundle) }
            .mapNotNull { b ->
                val text = b.getCharSequence("text")?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val sender = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (b.get("sender_person") as? android.app.Person)?.name
                } else null) ?: b.getCharSequence("sender")
                PanelMessage(sender?.toString().orEmpty(), text)
            }
            .takeLast(MAX_MESSAGES)
    }

    private fun progressOf(n: Notification): Float? {
        val extras = n.extras ?: return null
        if (extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) return -1f
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        if (max <= 0) return null
        return (extras.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max).coerceIn(0f, 1f)
    }

    /** Decoded once per notification post: the panel is read again on every change in the shade. */
    private val pictures = ConcurrentHashMap<String, Triple<Long, Bitmap?, Bitmap?>>()

    private fun picturesOf(context: Context, sbn: StatusBarNotification): Pair<Bitmap?, Bitmap?> {
        pictures[sbn.key]?.takeIf { it.first == sbn.postTime }?.let { return it.second to it.third }
        val n = sbn.notification
        val avatar = runCatching {
            n.getLargeIcon()?.loadDrawable(context)?.let { drawableBitmap(it, AVATAR_PX) }
        }.getOrNull()
        val picture = runCatching {
            val extras = n.extras
            val bmp = extras?.get(Notification.EXTRA_PICTURE) as? Bitmap
                ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (extras?.get(Notification.EXTRA_PICTURE_ICON) as? android.graphics.drawable.Icon)
                        ?.loadDrawable(context)?.let { drawableBitmap(it, PICTURE_PX) }
                } else null)
            bmp?.let { shrink(it, PICTURE_PX) }
        }.getOrNull()
        pictures[sbn.key] = Triple(sbn.postTime, avatar, picture)
        return avatar to picture
    }

    private fun drawableBitmap(d: android.graphics.drawable.Drawable, max: Int): Bitmap {
        (d as? BitmapDrawable)?.bitmap?.let { return shrink(it, max) }
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: max
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: max
        val scale = minOf(1f, max / maxOf(w, h).toFloat())
        val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, bmp.width, bmp.height)
        d.draw(Canvas(bmp))
        return bmp
    }

    /** No larger than [max] on its long side: a photo in a notification can be the camera's whole frame. */
    private fun shrink(b: Bitmap, max: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= max) return b
        val scale = max / long.toFloat()
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
    }

    private const val MAX_ACTIONS = 3
    private const val MAX_MESSAGES = 3
    private const val AVATAR_PX = 128
    private const val PICTURE_PX = 720

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
