package com.serkantkn.zunelauncher.ui.screens.social

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.repository.SettingsRepository
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class SocialHubViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(SettingsDataStore(application))

    val layout: StateFlow<SocialHubLayout> = settingsRepository.socialHubLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SocialHubLayout.TIMELINE)

    val messages: StateFlow<List<SocialMessageModel>> = SocialRepository.messages

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _selectedMessage = MutableStateFlow<SocialMessageModel?>(null)
    val selectedMessage: StateFlow<SocialMessageModel?> = _selectedMessage.asStateFlow()

    fun checkPermissionAgain() {
        _hasPermission.value = checkPermission()
    }

    private fun checkPermission(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(getApplication())
        return enabledListeners.contains(getApplication<Application>().packageName)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun selectMessage(message: SocialMessageModel?) {
        _selectedMessage.value = message
    }

    fun sendReply(message: SocialMessageModel, replyText: String) {
        val replyAction = message.replyAction ?: return
        
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(replyAction.remoteInputResultKey, replyText)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(replyAction.remoteInputResultKey).build()),
            intent,
            bundle
        )

        try {
            replyAction.pendingIntent.send(getApplication(), 0, intent)
            SocialRepository.removeMessage(message.id)
            _selectedMessage.value = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun openMessage(message: SocialMessageModel) {
        try {
            message.openIntent?.send()
            SocialRepository.removeMessage(message.id)
            _selectedMessage.value = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissMessage(message: SocialMessageModel) {
        try {
            com.serkantkn.zunelauncher.data.service.SocialNotificationListener.instance?.cancelNotification(message.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        SocialRepository.removeMessage(message.id)
        if (_selectedMessage.value?.id == message.id) {
            _selectedMessage.value = null
        }
    }
}
