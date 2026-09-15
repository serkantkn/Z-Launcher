package com.serkantkn.zunelauncher.ui.screens.messaging

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.data.service.MessageNotifier
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.BlockedNumbers
import com.serkantkn.zunelauncher.util.PhoneNumbers
import com.serkantkn.zunelauncher.util.SendOutcome
import com.serkantkn.zunelauncher.util.SimCard
import com.serkantkn.zunelauncher.util.SmsSender
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The pivots the conversation list is split across. */
enum class MessageTab { ALL, UNREAD, DRAFTS }

class MessagingHubViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = application.appContainer.settingsDataStore

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** A line of text across the top saying what just happened. Clears itself. */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _conversations = MutableStateFlow<List<SmsConversationModel>>(emptyList())
    val conversations: StateFlow<List<SmsConversationModel>> = _conversations.asStateFlow()

    private val _contacts = MutableStateFlow<List<Pair<ContactModel, String>>>(emptyList())
    val contacts: StateFlow<List<Pair<ContactModel, String>>> = _contacts.asStateFlow()

    private val _selectedConversation = MutableStateFlow<SmsConversationModel?>(null)
    val selectedConversation: StateFlow<SmsConversationModel?> = _selectedConversation.asStateFlow()

    private val _threadMessages = MutableStateFlow<List<SmsMessageModel>>(emptyList())
    val threadMessages: StateFlow<List<SmsMessageModel>> = _threadMessages.asStateFlow()

    private val _hasSmsPermission = MutableStateFlow(false)
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

    /** Whether the launcher is the phone's messaging app — which decides what it may change. */
    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Messages whose text matches the search, from every conversation. */
    private val _matchingMessages = MutableStateFlow<List<SmsMessageModel>>(emptyList())
    val matchingMessages: StateFlow<List<SmsMessageModel>> = _matchingMessages.asStateFlow()

    /** The conversations pinned to the Start screen, so the menu can say pin or unpin. */
    private val _pinnedThreads = MutableStateFlow<Set<Long>>(emptySet())
    val pinnedThreads: StateFlow<Set<Long>> = _pinnedThreads.asStateFlow()

    /** The SIMs a message can go out on. Empty or single means there is no choice to offer. */
    private val _simLines = MutableStateFlow<List<SimCard>>(emptyList())
    val simLines: StateFlow<List<SimCard>> = _simLines.asStateFlow()

    private val _selectedSim = MutableStateFlow<Int>(-1)
    val selectedSim: StateFlow<Int> = _selectedSim.asStateFlow()

    /** Whether the person in the open conversation is on the blocked list. */
    private val _selectedBlocked = MutableStateFlow(false)
    val selectedBlocked: StateFlow<Boolean> = _selectedBlocked.asStateFlow()

    private var searchJob: Job? = null
    private var statusJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.startTiles.collect { tiles ->
                _pinnedThreads.value = tiles.mapNotNull { it.smsThreadId }.toSet()
            }
        }
        // Anything that changes the store — a message arriving, one going out, a status coming
        // back — ticks the bridge, and the hub follows.
        viewModelScope.launch {
            MessagingBridge.changes.collect { tick ->
                if (tick > 0L && _hasSmsPermission.value) refresh()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        MessagingBridge.visibleThreadId = null
    }

    // ════════════════════════════════════════════════════════════
    // LOADING
    // ════════════════════════════════════════════════════════════

    fun checkPermissionAndLoad(context: Context) {
        val hasRead = granted(context, android.Manifest.permission.READ_SMS)
        val hasSend = granted(context, android.Manifest.permission.SEND_SMS)
        val hasContacts = granted(context, android.Manifest.permission.READ_CONTACTS)
        val allowed = hasRead && hasSend
        _hasSmsPermission.value = allowed
        _isDefaultSmsApp.value = SmsRepository.isDefaultSmsApp(context)
        _simLines.value = SmsSender.sims(context)

        if (allowed) {
            loadConversations(context)
            if (hasContacts) loadContacts(context)
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun loadConversations(context: Context) {
        viewModelScope.launch {
            try {
                _conversations.value = SmsRepository.getConversations(context)
                _errorMessage.value = null
            } catch (e: Exception) {
                ZuneLog.e(TAG, "loadConversations failed", e)
                _errorMessage.value = e.toUserMessage(context)
            }
        }
    }

    fun loadContacts(context: Context) {
        viewModelScope.launch {
            try {
                _contacts.value = context.appContainer.contactRepository.getContactsWithNumbers()
            } catch (e: Exception) {
                ZuneLog.e(TAG, "loadContacts failed", e)
            }
        }
    }

    /** Reloads the list, and the open conversation with it. */
    private fun refresh() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _conversations.value = runCatching { SmsRepository.getConversations(context) }
                .getOrDefault(_conversations.value)
            val open = _selectedConversation.value ?: return@launch
            if (open.threadId > 0L) {
                _threadMessages.value = runCatching {
                    SmsRepository.getMessagesForThread(context, open.threadId)
                }.getOrDefault(_threadMessages.value)
                _selectedConversation.value =
                    _conversations.value.firstOrNull { it.threadId == open.threadId } ?: open
            }
        }
    }

    /** Which conversations belong on a pivot. */
    fun conversationsFor(tab: MessageTab): List<SmsConversationModel> = when (tab) {
        MessageTab.ALL -> _conversations.value
        MessageTab.UNREAD -> _conversations.value.filter { it.unreadCount > 0 || !it.isRead }
        MessageTab.DRAFTS -> _conversations.value.filter { it.hasDraft }
    }

    // ════════════════════════════════════════════════════════════
    // OPENING A CONVERSATION
    // ════════════════════════════════════════════════════════════

    fun openConversation(context: Context, conversation: SmsConversationModel) {
        _selectedConversation.value = conversation
        MessagingBridge.visibleThreadId = conversation.threadId.takeIf { it > 0L }
        _selectedBlocked.value = conversation.address.isNotBlank() &&
            BlockedNumbers.isBlocked(context, conversation.address)

        viewModelScope.launch {
            if (conversation.threadId > 0L) {
                _threadMessages.value = try {
                    SmsRepository.getMessagesForThread(context, conversation.threadId)
                } catch (e: Exception) {
                    ZuneLog.e(TAG, "openConversation failed", e)
                    _errorMessage.value = e.toUserMessage(context)
                    emptyList()
                }
                // Opening a conversation is reading it.
                if (conversation.unreadCount > 0 || !conversation.isRead) {
                    SmsRepository.markThreadRead(context, conversation.threadId)
                    MessageNotifier.clear(context, conversation.threadId)
                    _conversations.value = _conversations.value.map {
                        if (it.threadId == conversation.threadId) {
                            it.copy(isRead = true, unreadCount = 0)
                        } else {
                            it
                        }
                    }
                }
            } else {
                _threadMessages.value = emptyList()
            }
        }
    }

    /** Opens the conversation with a person, starting one when there is none yet. */
    fun openConversationWithContact(context: Context, contactName: String, phoneNumber: String) {
        val existing = _conversations.value.firstOrNull {
            PhoneNumbers.sameNumber(it.address, phoneNumber)
        }
        if (existing != null) {
            openConversation(context, existing.copy(contactName = contactName.ifBlank { existing.contactName }))
            return
        }
        openConversation(
            context,
            SmsConversationModel(
                threadId = SmsConversationModel.NEW_THREAD_ID,
                address = phoneNumber,
                contactName = contactName.ifBlank { phoneNumber },
                snippet = "",
                timestamp = System.currentTimeMillis(),
                isRead = true,
                recipients = listOf(phoneNumber)
            )
        )
    }

    /** Opens a conversation by its thread number — a pinned tile, or a notification being tapped. */
    fun openThread(context: Context, threadId: Long) {
        viewModelScope.launch {
            if (_conversations.value.isEmpty()) {
                _conversations.value = runCatching { SmsRepository.getConversations(context) }
                    .getOrDefault(emptyList())
            }
            _conversations.value.firstOrNull { it.threadId == threadId }
                ?.let { openConversation(context, it) }
        }
    }

    fun closeConversation() {
        _selectedConversation.value = null
        _threadMessages.value = emptyList()
        _selectedBlocked.value = false
        MessagingBridge.visibleThreadId = null
    }

    /** Keeps unsent text under the conversation instead of throwing it away. */
    fun saveDraft(context: Context, text: String) {
        val conversation = _selectedConversation.value ?: return
        if (conversation.threadId <= 0L) return
        viewModelScope.launch {
            // Only say the text was kept if the store actually kept it.
            val kept = SmsRepository.saveDraft(context, conversation.threadId, conversation.address, text)
            if (!kept) return@launch
            _conversations.value = _conversations.value.map {
                if (it.threadId == conversation.threadId) it.copy(draft = text) else it
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // SENDING
    // ════════════════════════════════════════════════════════════

    fun selectSim(subscriptionId: Int) {
        _selectedSim.value = subscriptionId
    }

    fun sendSms(context: Context, recipientNumber: String, messageText: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val conversation = _selectedConversation.value
            val outcome = SmsSender.send(
                context = context,
                address = recipientNumber,
                text = messageText,
                threadId = conversation?.threadId?.takeIf { it > 0L } ?: 0L,
                subscriptionId = _selectedSim.value
            )
            announce(context, outcome)

            if (outcome == SendOutcome.HANDED_OVER) {
                _conversations.value = runCatching { SmsRepository.getConversations(context) }
                    .getOrDefault(_conversations.value)
                // A brand new conversation only gets its thread number once the message is filed.
                val landed = _conversations.value.firstOrNull {
                    PhoneNumbers.sameNumber(it.address, recipientNumber)
                }
                if (landed != null) {
                    _selectedConversation.value = landed
                    MessagingBridge.visibleThreadId = landed.threadId
                    _threadMessages.value = runCatching {
                        SmsRepository.getMessagesForThread(context, landed.threadId)
                    }.getOrDefault(emptyList())
                }
            }
            onComplete(outcome == SendOutcome.HANDED_OVER)
        }
    }

    /** Sends a failed message again, and takes the failed copy out of the conversation. */
    fun resend(context: Context, message: SmsMessageModel) {
        viewModelScope.launch {
            val conversation = _selectedConversation.value
            val outcome = SmsSender.resend(
                context = context,
                messageId = message.id,
                address = message.address.ifBlank { conversation?.address.orEmpty() },
                text = message.body,
                threadId = message.threadId
            )
            announce(context, outcome)
            refresh()
        }
    }

    private fun announce(context: Context, outcome: SendOutcome) {
        val text = when (outcome) {
            SendOutcome.HANDED_OVER -> null
            SendOutcome.NO_PERMISSION -> context.getString(com.serkantkn.zunelauncher.R.string.msg_send_no_permission)
            SendOutcome.BAD_NUMBER -> context.getString(com.serkantkn.zunelauncher.R.string.msg_send_bad_number)
            SendOutcome.EMPTY -> null
            SendOutcome.FAILED -> context.getString(com.serkantkn.zunelauncher.R.string.msg_send_failed)
        }
        if (text != null) showStatus(text)
    }

    fun showStatus(text: String) {
        _statusMessage.value = text
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            delay(STATUS_MILLIS)
            _statusMessage.value = null
        }
    }

    fun clearStatus() {
        statusJob?.cancel()
        _statusMessage.value = null
    }

    // ════════════════════════════════════════════════════════════
    // CHANGING WHAT IS THERE
    // ════════════════════════════════════════════════════════════

    fun deleteThread(context: Context, conversation: SmsConversationModel) {
        viewModelScope.launch {
            val done = SmsRepository.deleteThread(context, conversation.threadId)
            if (done) {
                _conversations.value = _conversations.value.filterNot { it.threadId == conversation.threadId }
                if (_selectedConversation.value?.threadId == conversation.threadId) closeConversation()
                MessageNotifier.clear(context, conversation.threadId)
            } else {
                showStatus(context.getString(com.serkantkn.zunelauncher.R.string.msg_needs_default_app))
            }
        }
    }

    fun deleteMessage(context: Context, message: SmsMessageModel) {
        viewModelScope.launch {
            val done = SmsRepository.deleteMessage(context, message)
            if (done) {
                _threadMessages.value = _threadMessages.value.filterNot { it.id == message.id }
                _conversations.value = runCatching { SmsRepository.getConversations(context) }
                    .getOrDefault(_conversations.value)
            } else {
                showStatus(context.getString(com.serkantkn.zunelauncher.R.string.msg_needs_default_app))
            }
        }
    }

    fun markThreadRead(context: Context, conversation: SmsConversationModel) {
        viewModelScope.launch {
            val done = SmsRepository.markThreadRead(context, conversation.threadId)
            if (done) {
                MessageNotifier.clear(context, conversation.threadId)
                _conversations.value = _conversations.value.map {
                    if (it.threadId == conversation.threadId) it.copy(isRead = true, unreadCount = 0) else it
                }
            } else {
                showStatus(context.getString(com.serkantkn.zunelauncher.R.string.msg_needs_default_app))
            }
        }
    }

    /** Blocks or unblocks the number a conversation is with. */
    fun toggleBlocked(context: Context, conversation: SmsConversationModel) {
        val number = conversation.address
        if (number.isBlank()) return
        viewModelScope.launch {
            val blocked = BlockedNumbers.isBlocked(context, number)
            val done = if (blocked) {
                BlockedNumbers.unblock(context, number)
            } else {
                BlockedNumbers.block(context, number)
            }
            if (done) {
                _selectedBlocked.value = !blocked
                showStatus(
                    context.getString(
                        if (blocked) {
                            com.serkantkn.zunelauncher.R.string.msg_unblocked
                        } else {
                            com.serkantkn.zunelauncher.R.string.msg_blocked
                        }
                    )
                )
            } else {
                showStatus(context.getString(com.serkantkn.zunelauncher.R.string.msg_block_failed))
            }
        }
    }

    /** Puts a conversation on the Start screen, or takes it off again. */
    fun togglePinToStart(conversation: SmsConversationModel) {
        if (conversation.threadId <= 0L) return
        viewModelScope.launch {
            val tiles = settingsDataStore.startTiles.first()
            val id = "${StartTileItem.SMS_PREFIX}${conversation.threadId}"
            val updated = if (tiles.any { it.id == id }) {
                StartFolders.removeTile(tiles, id)
            } else {
                tiles + StartTileItem.fromThread(conversation.threadId, conversation.title)
            }
            settingsDataStore.setStartTiles(updated)
        }
    }

    // ════════════════════════════════════════════════════════════
    // SEARCH
    // ════════════════════════════════════════════════════════════

    fun setSearchQuery(context: Context, query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _matchingMessages.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _matchingMessages.value = SmsRepository.searchMessages(context, query)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _matchingMessages.value = emptyList()
    }

    /** The conversation a matching message belongs to, for tapping a search result. */
    fun conversationOf(message: SmsMessageModel): SmsConversationModel? =
        _conversations.value.firstOrNull { it.threadId == message.threadId }

    private companion object {
        const val TAG = "MessagingHubViewModel"
        const val SEARCH_DEBOUNCE_MILLIS = 220L
        const val STATUS_MILLIS = 2600L
    }
}
