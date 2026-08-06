package com.serkantkn.zunelauncher.data.repository

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

    fun updateNotificationCounts(counts: Map<String, Int>) {
        _notificationCounts.value = counts
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
        _latestToastMessage.value = message

        context?.let { ctx ->
            showOverlayIfPermitted(ctx, message, accentColor)
        }
    }

    fun removeMessage(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
        if (_latestToastMessage.value?.id == id) {
            _latestToastMessage.value = null
        }
    }

    fun clearToast() {
        _latestToastMessage.value = null
    }

    fun sendReply(context: Context, replyAction: ReplyAction, replyText: String): Boolean {
        if (replyAction.remoteInputResultKey == "test_reply_key") {
            android.widget.Toast.makeText(context, "Test yanıtı gönderildi: \"$replyText\"", android.widget.Toast.LENGTH_LONG).show()
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
            e.printStackTrace()
            false
        }
    }

    fun sendTestNotification(context: Context, accentColor: androidx.compose.ui.graphics.Color? = null) {
        val testMessage = SocialMessageModel(
            id = "test_msg_${System.currentTimeMillis()}",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Ahmet Yılmaz",
            text = "Selam! Windows Phone bildirim kartı harika görünüyor. Bu kartı aşağı sürükleyerek bana hızlı yanıt yazmayı deneyebilirsin!",
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
        if (android.provider.Settings.canDrawOverlays(context)) {
            val isWideScreen = context.resources.configuration.smallestScreenWidthDp >= 600
            val zuneColors = com.serkantkn.zunelauncher.ui.theme.ZuneExtendedColors(
                accentColor = accentColor ?: androidx.compose.ui.graphics.Color(0xFFD80073),
                isDark = true
            )
            com.serkantkn.zunelauncher.service.WpNotificationOverlayManager.showNotificationOverlay(
                context = context,
                message = message,
                isWideScreen = isWideScreen,
                zuneColors = zuneColors
            )
        }
    }

    fun clearAll() {
        _messages.value = emptyList()
        _latestToastMessage.value = null
    }
}
