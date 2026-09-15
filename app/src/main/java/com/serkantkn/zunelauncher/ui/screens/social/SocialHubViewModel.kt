package com.serkantkn.zunelauncher.ui.screens.social

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.SocialApps
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.HubBridge
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import com.serkantkn.zunelauncher.util.HubDestination
import com.serkantkn.zunelauncher.util.hubFor
import com.serkantkn.zunelauncher.util.matchConversation
import com.serkantkn.zunelauncher.util.sendAllowingBackgroundStart
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialHubViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = application.appContainer.settingsDataStore
    private val appRepository = application.appContainer.appRepository

    val messages: StateFlow<List<SocialMessageModel>> = SocialRepository.messages

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _selectedMessage = MutableStateFlow<SocialMessageModel?>(null)
    val selectedMessage: StateFlow<SocialMessageModel?> = _selectedMessage.asStateFlow()

    /**
     * The apps feeding the hub.
     *
     * Until somebody chooses, this is whichever social apps are on the phone — a hub that starts
     * empty and says nothing about why would just look broken.
     */
    val sources: StateFlow<Set<String>> = combine(
        settingsDataStore.socialSources,
        _installedApps
    ) { chosen, installed ->
        chosen ?: SocialApps.defaultSources(installed.map { it.packageName })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Apps that have sent something social while switched off, offered on the sources page. */
    val suggestions: StateFlow<Set<String>> = SocialRepository.suggestedSources

    /** Messages grouped by the app they came from, newest app first. */
    val byApp: StateFlow<List<Pair<String, List<SocialMessageModel>>>> = messages.map { list ->
        list.groupBy { it.appName }
            .toList()
            .sortedByDescending { (_, group) -> group.maxOfOrNull { it.timestamp } ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** How the hub lays its messages out; chosen in Settings, under the hubs tab. */
    val layout: StateFlow<SocialHubLayout> = settingsDataStore.socialHubLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SocialHubLayout.TIMELINE)

    /** Whether anything is feeding the hub at all, which is a different silence from "nothing new". */
    val hasSources: StateFlow<Boolean> = sources
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            appRepository.getInstalledApps().collect { apps -> _installedApps.value = apps }
        }
    }

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

    // ── Sources ─────────────────────────────────────────────────────────────

    /** Switching an app off from the hub itself; choosing them is done in Settings. */
    fun muteSource(packageName: String) {
        viewModelScope.launch {
            settingsDataStore.setSocialSources(sources.value - packageName)
            SocialRepository.removeMessagesOf(packageName)
        }
    }

    // ── Messages ────────────────────────────────────────────────────────────

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
            ZuneLog.e(TAG, "sendReply failed", e)
        }
    }

    /**
     * Opens the notification where it is best read.
     *
     * When the launcher has a hub that already holds the same thing — a text message, a missed
     * call — it goes there, on the conversation itself where that can be worked out. Everything
     * else is handed back to the app that sent it.
     */
    fun openMessage(message: SocialMessageModel) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val destination = hubFor(
                packageName = message.packageName,
                category = message.category,
                defaultSmsPackage = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
                defaultDialerPackage = runCatching {
                    context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                }.getOrNull()
            )

            when (destination) {
                HubDestination.MESSAGING -> openInMessagingHub(message)
                HubDestination.PHONE -> {
                    HubBridge.open(HubType.PHONE)
                    finishWith(message)
                }

                null -> openApp(message)
            }
        }
    }

    private suspend fun openInMessagingHub(message: SocialMessageModel) {
        val context = getApplication<Application>()
        val conversations = runCatching { SmsRepository.getConversations(context) }
            .getOrDefault(emptyList())
        val conversation = matchConversation(message.heading, conversations)
        if (conversation != null) {
            MessagingBridge.openThread(conversation.threadId)
        } else {
            HubBridge.open(HubType.MESSAGING)
        }
        finishWith(message)
    }

    /**
     * Hands the notification back to the app that sent it.
     *
     * The notification's own intent is the good answer — it lands on the right screen — but a lot
     * of notifications carry none, and "go to the app" should work either way, so the app's
     * launcher entry is the fallback.
     */
    fun openApp(message: SocialMessageModel) {
        val context = getApplication<Application>()
        val opened = message.openIntent?.sendAllowingBackgroundStart(context) == true
        if (!opened) {
            runCatching { appRepository.launchApp(message.packageName) }
        }
        finishWith(message)
    }

    private fun finishWith(message: SocialMessageModel) {
        SocialRepository.removeMessage(message.id)
        _selectedMessage.value = null
    }

    fun dismissMessage(message: SocialMessageModel) {
        try {
            com.serkantkn.zunelauncher.data.service.SocialNotificationListener.instance
                ?.cancelNotification(message.id)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "dismissMessage failed", e)
        }
        SocialRepository.removeMessage(message.id)
        if (_selectedMessage.value?.id == message.id) {
            _selectedMessage.value = null
        }
    }

    /** Clears one app's messages without switching it off as a source. */
    fun clearApp(packageName: String) {
        val listener = com.serkantkn.zunelauncher.data.service.SocialNotificationListener.instance
        messages.value.filter { it.packageName == packageName }.forEach { message ->
            runCatching { listener?.cancelNotification(message.id) }
        }
        SocialRepository.removeMessagesOf(packageName)
        if (_selectedMessage.value?.packageName == packageName) _selectedMessage.value = null
    }

    fun clearAll() {
        val listener = com.serkantkn.zunelauncher.data.service.SocialNotificationListener.instance
        messages.value.forEach { message ->
            runCatching { listener?.cancelNotification(message.id) }
        }
        SocialRepository.clearAll()
        _selectedMessage.value = null
    }

    private companion object {
        const val TAG = "SocialHubViewModel"
    }
}
