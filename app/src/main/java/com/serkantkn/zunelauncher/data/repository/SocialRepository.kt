package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.serkantkn.zunelauncher.data.model.ReplyAction
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SocialRepository {
    private val _messages = MutableStateFlow<List<SocialMessageModel>>(emptyList())
    val messages: StateFlow<List<SocialMessageModel>> = _messages.asStateFlow()

    private val _notificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val notificationCounts: StateFlow<Map<String, Int>> = _notificationCounts.asStateFlow()

    private val _latestToastMessage = MutableStateFlow<SocialMessageModel?>(null)
    val latestToastMessage: StateFlow<SocialMessageModel?> = _latestToastMessage.asStateFlow()

    private val shownToastIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** True while MainActivity is resumed; then LauncherScreen draws the toast instead of the overlay. */
    @Volatile var isLauncherForeground: Boolean = false
    private var disabledApps: Set<String> = emptySet()

    fun updateDisabledApps(apps: Set<String>) {
        disabledApps = apps
    }

    fun isAppNotificationAllowed(packageName: String): Boolean {
        return !disabledApps.contains(packageName)
    }

    fun updateNotificationCounts(counts: Map<String, Int>) {
        _notificationCounts.value = counts
    }

    private fun recalculateNotificationCounts() {
        _notificationCounts.value = _messages.value
            .groupBy { it.packageName }
            .mapValues { it.value.size }
    }

    fun markAsShown(id: String) {
        shownToastIds.add(id)
    }

    fun addOrUpdateMessage(message: SocialMessageModel, context: Context? = null, accentColor: androidx.compose.ui.graphics.Color? = null) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == message.id }
        if (index != -1) {
            currentList[index] = message
        } else {
            currentList.add(message)
        }
        currentList.sortByDescending { it.timestamp }
        _messages.value = currentList
        recalculateNotificationCounts()

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
        recalculateNotificationCounts()
    }

    fun clearToast() {
        _latestToastMessage.value = null
    }

    fun sendReply(context: Context, replyAction: ReplyAction, replyText: String): Boolean {
        if (replyAction.remoteInputResultKey == "test_reply_key") {
            android.widget.Toast.makeText(context, context.getString(R.string.social_test_reply_sent, replyText), android.widget.Toast.LENGTH_LONG).show()
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
            ZuneLog.e("SocialRepository", "sendReply failed", e)
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
                remoteInputResultKey = "test_reply_key"
            ),
            openIntent = null
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
        _notificationCounts.value = emptyMap()
    }
}
