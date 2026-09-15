package com.serkantkn.zunelauncher.data.repository

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ReplyAction
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Social hub holds.
 *
 * Only messages from the apps the user has named as sources ever get this far — the listener turns
 * everything else away — so this is a social feed rather than a copy of the notification shade.
 * Badge counts are the exception: those cover every app, because a number on an app's tile is a
 * launcher feature and has nothing to do with what is social.
 */
object SocialRepository {

    private const val TAG = "SocialRepository"

    private val _messages = MutableStateFlow<List<SocialMessageModel>>(emptyList())
    val messages: StateFlow<List<SocialMessageModel>> = _messages.asStateFlow()

    /** Unread notifications per package, for the Start screen's tile badges. All apps, not just social ones. */
    private val _notificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val notificationCounts: StateFlow<Map<String, Int>> = _notificationCounts.asStateFlow()

    private val _latestToastMessage = MutableStateFlow<SocialMessageModel?>(null)
    val latestToastMessage: StateFlow<SocialMessageModel?> = _latestToastMessage.asStateFlow()

    /**
     * Apps that have sent something that looked social while not being a source.
     *
     * Nothing is let in on the strength of this; it only fills the "did you mean these?" line on
     * the sources page, so an app the catalogue has never heard of can still be found.
     */
    private val _suggestedSources = MutableStateFlow<Set<String>>(emptySet())
    val suggestedSources: StateFlow<Set<String>> = _suggestedSources.asStateFlow()

    private val shownToastIds =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** True while MainActivity is resumed; then LauncherScreen draws the toast instead of the overlay. */
    @Volatile
    var isLauncherForeground: Boolean = false

    // ════════════════════════════════════════════════════════════
    // WHICH APPS FEED THE HUB
    // ════════════════════════════════════════════════════════════

    @Volatile
    private var sources: Set<String> = emptySet()

    fun updateSources(packages: Set<String>) {
        sources = packages
        // A source that has just been switched off should not leave its messages behind.
        val stale = _messages.value.filterNot { it.packageName in packages }
        if (stale.isNotEmpty()) {
            _messages.value = _messages.value.filter { it.packageName in packages }
            if (_latestToastMessage.value?.packageName !in packages) _latestToastMessage.value = null
        }
        _suggestedSources.value = _suggestedSources.value - packages
    }

    fun isSource(packageName: String): Boolean = packageName in sources

    /** Notes an app that talks like a social app but has not been let in. */
    fun suggestSource(packageName: String) {
        if (packageName in sources) return
        _suggestedSources.value = _suggestedSources.value + packageName
    }

    fun clearSuggestions() {
        _suggestedSources.value = emptySet()
    }

    // ════════════════════════════════════════════════════════════
    // MESSAGES
    // ════════════════════════════════════════════════════════════

    fun updateNotificationCounts(counts: Map<String, Int>) {
        _notificationCounts.value = counts
    }

    fun markAsShown(id: String) {
        shownToastIds.add(id)
    }

    fun addOrUpdateMessage(
        message: SocialMessageModel,
        context: Context? = null,
        accentColor: androidx.compose.ui.graphics.Color? = null
    ) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == message.id }
        if (index != -1) {
            currentList[index] = message
        } else {
            currentList.add(message)
        }
        currentList.sortByDescending { it.timestamp }
        _messages.value = currentList

        // Only trigger toast popup IF this notification has NOT been shown before
        if (!shownToastIds.contains(message.id)) {
            shownToastIds.add(message.id)
            _latestToastMessage.value = message
            context?.let { ctx -> showOverlayIfPermitted(ctx, message, accentColor) }
        }
    }

    fun removeMessage(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
        shownToastIds.remove(id)
        if (_latestToastMessage.value?.id == id) {
            _latestToastMessage.value = null
        }
    }

    /** Everything from one app, for when a source is muted from the hub itself. */
    fun removeMessagesOf(packageName: String) {
        _messages.value = _messages.value.filterNot { it.packageName == packageName }
        if (_latestToastMessage.value?.packageName == packageName) _latestToastMessage.value = null
    }

    fun clearToast() {
        _latestToastMessage.value = null
    }

    fun sendReply(context: Context, replyAction: ReplyAction, replyText: String): Boolean {
        if (replyAction.remoteInputResultKey == TEST_REPLY_KEY) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.social_test_reply_sent, replyText),
                android.widget.Toast.LENGTH_LONG
            ).show()
            return true
        }
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(replyAction.remoteInputResultKey, replyText)
            RemoteInput.addResultsToIntent(
                arrayOf(RemoteInput.Builder(replyAction.remoteInputResultKey).build()),
                intent,
                bundle
            )
            replyAction.pendingIntent.send(context, 0, intent)
            true
        } catch (e: Exception) {
            ZuneLog.e(TAG, "sendReply failed", e)
            false
        }
    }

    fun sendTestNotification(context: Context, accentColor: androidx.compose.ui.graphics.Color? = null) {
        val testMessage = SocialMessageModel(
            id = "test_msg_${System.currentTimeMillis()}",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Ahmet Yılmaz",
            text = context.getString(R.string.social_test_message),
            timestamp = System.currentTimeMillis(),
            icon = null,
            replyAction = ReplyAction(
                pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent("com.serkantkn.zunelauncher.TEST_REPLY"),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                ),
                remoteInputResultKey = TEST_REPLY_KEY
            ),
            openIntent = null,
            category = "msg"
        )
        addOrUpdateMessage(testMessage, context, accentColor)
    }

    fun showOverlayIfPermitted(
        context: Context,
        message: SocialMessageModel,
        accentColor: androidx.compose.ui.graphics.Color? = null
    ) {
        // Windows Phone banner over other apps (system overlay). No-op while the launcher is in front.
        com.serkantkn.zunelauncher.data.service.WpToastOverlay.show(context, message)
    }

    fun clearAll() {
        _messages.value = emptyList()
        shownToastIds.clear()
        _latestToastMessage.value = null
    }

    private const val TEST_REPLY_KEY = "test_reply_key"
}
