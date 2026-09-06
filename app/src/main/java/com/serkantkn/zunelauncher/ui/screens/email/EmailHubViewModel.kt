package com.serkantkn.zunelauncher.ui.screens.email

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailAddress
import com.serkantkn.zunelauncher.data.model.EmailAttachment
import com.serkantkn.zunelauncher.data.model.EmailDraft
import com.serkantkn.zunelauncher.data.model.EmailDraftKind
import com.serkantkn.zunelauncher.data.model.EmailFolder
import com.serkantkn.zunelauncher.data.model.EmailFolderType
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.data.repository.EmailBridge
import com.serkantkn.zunelauncher.data.repository.EmailException
import com.serkantkn.zunelauncher.data.repository.EmailRepository
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Selected mailbox: one account, or every inbox at once. */
const val UNIFIED_ACCOUNT_ID = ""

/**
 * Email Hub state. House ViewModel template: AndroidViewModel, repositories from
 * AppContainer, one StateFlow per field, one-shot results via callbacks. Screens observe the
 * EmailCache flows (through this class); the repository only writes to the cache.
 */
class EmailHubViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val emailDataStore = container.emailDataStore
    private val repository: EmailRepository = container.emailRepository
    private val cache = container.emailCache
    private val syncScheduler = container.emailSyncScheduler

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asState(default: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)

    // ── Accounts ──────────────────────────────────────────────────────────

    val accounts: StateFlow<List<EmailAccount>> = emailDataStore.accountsFlow.asState(emptyList())

    private val _isReady = MutableStateFlow(false)
    /** True once the stored accounts have been read; bridge requests wait for this. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    /** Account id, [UNIFIED_ACCOUNT_ID] for every inbox, null until accounts are known. */
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    val currentAccount: StateFlow<EmailAccount?> = combine(accounts, _selectedAccountId) { list, id ->
        list.firstOrNull { it.id == id }
    }.asState(null)

    val isUnified: StateFlow<Boolean> = combine(accounts, _selectedAccountId) { list, id ->
        id == UNIFIED_ACCOUNT_ID && list.size > 1
    }.asState(false)

    // ── Folder and list ───────────────────────────────────────────────────

    private val _currentFolder = MutableStateFlow(EmailFolder.INBOX_NAME)
    val currentFolder: StateFlow<String> = _currentFolder.asStateFlow()

    val folders: StateFlow<List<EmailFolder>> = combine(cache.folders, _selectedAccountId) { map, id ->
        if (id == null) emptyList() else map[id].orEmpty()
    }.asState(emptyList())

    val currentFolderModel: StateFlow<EmailFolder?> = combine(folders, _currentFolder) { list, name ->
        list.firstOrNull { it.fullName == name }
    }.asState(null)

    /** Headers of the selected folder (or every inbox), newest first. */
    val messages: StateFlow<List<EmailMessage>> = combine(cache.messages, accounts, _selectedAccountId, _currentFolder) { map, list, id, folder ->
        when {
            id == null -> emptyList()
            id == UNIFIED_ACCOUNT_ID -> list.flatMap { map[cache.listKey(it.id, EmailFolder.INBOX_NAME)].orEmpty() }.sortedByDescending { it.date }
            else -> map[cache.listKey(id, folder)].orEmpty()
        }
    }.asState(emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    /** Short banner text after an action (sent, moved, error). */
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Unread count over every inbox: the Start tile badge. */
    val inboxUnread: StateFlow<Int> = cache.inboxUnread

    private val lastSyncAt = mutableMapOf<String, Long>()

    // ── Search ────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _serverResults = MutableStateFlow<List<EmailMessage>?>(null)
    /** Server-side hits for the current query, or null when no server search ran. */
    val serverResults: StateFlow<List<EmailMessage>?> = _serverResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // ── Selection ─────────────────────────────────────────────────────────

    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // ── Reader ────────────────────────────────────────────────────────────

    private val _openMessage = MutableStateFlow<EmailMessage?>(null)
    val openMessage: StateFlow<EmailMessage?> = _openMessage.asStateFlow()

    private val _isLoadingBody = MutableStateFlow(false)
    val isLoadingBody: StateFlow<Boolean> = _isLoadingBody.asStateFlow()

    // ── Compose / drafts ──────────────────────────────────────────────────

    val drafts: StateFlow<List<EmailDraft>> = emailDataStore.draftsFlow.asState(emptyList())

    private val _composeDraft = MutableStateFlow<EmailDraft?>(null)
    val composeDraft: StateFlow<EmailDraft?> = _composeDraft.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    /** Addresses seen in cached mail, for recipient suggestions. */
    val knownAddresses: StateFlow<List<EmailAddress>> = cache.messages.map { map ->
        map.values.asSequence().flatten()
            .flatMap { sequenceOf(it.from) + it.to.asSequence() + it.cc.asSequence() }
            .filter { it.address.contains('@') }
            .distinctBy { it.address.lowercase() }
            .take(400)
            .toList()
    }.asState(emptyList())

    init {
        viewModelScope.launch {
            val saved = emailDataStore.selectedAccountIdFlow.first()
            val list = emailDataStore.accountsFlow.first()
            val initial = when {
                list.isEmpty() -> null
                saved == UNIFIED_ACCOUNT_ID && list.size > 1 -> UNIFIED_ACCOUNT_ID
                list.any { it.id == saved } -> saved
                else -> list.first().id
            }
            _selectedAccountId.value = initial
            list.forEach { cache.loadFolders(it.id); cache.loadMessages(it.id, EmailFolder.INBOX_NAME) }
            _isReady.value = true
            if (initial != null) sync()
        }
        // Keep the selection valid when accounts are added/removed.
        viewModelScope.launch {
            accounts.collect { list ->
                val id = _selectedAccountId.value
                if (list.isEmpty()) _selectedAccountId.value = null
                else if (id == null || (id != UNIFIED_ACCOUNT_ID && list.none { it.id == id }) || (id == UNIFIED_ACCOUNT_ID && list.size < 2)) {
                    _selectedAccountId.value = list.first().id
                    _currentFolder.value = EmailFolder.INBOX_NAME
                }
            }
        }
    }

    // ── Account actions ───────────────────────────────────────────────────

    /** Verifies the credentials, then stores the account. [onResult] receives an error text or null. */
    fun addOrUpdateAccount(account: EmailAccount, password: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val effectivePassword = if (account.isGoogleOAuth) null else {
                    password?.takeIf { it.isNotEmpty() } ?: container.secretStore.get(account.id)
                        ?: throw EmailException(R.string.email_error_no_password)
                }
                repository.testConnection(account, effectivePassword)
                if (effectivePassword != null) repository.savePassword(account.id, effectivePassword)
                emailDataStore.upsertAccount(account)
                emailDataStore.setSelectedAccountId(account.id)
                _selectedAccountId.value = account.id
                _currentFolder.value = EmailFolder.INBOX_NAME
                syncScheduler.reschedule()
                onResult(null)
                sync(force = true)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "addOrUpdateAccount failed", e)
                onResult(userText(e))
            }
        }
    }

    fun updateAccountSettings(account: EmailAccount) {
        viewModelScope.launch {
            emailDataStore.upsertAccount(account)
            syncScheduler.reschedule()
        }
    }

    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            emailDataStore.removeAccount(accountId)
            repository.forget(accountId)
            syncScheduler.reschedule()
            _openMessage.value = null
            _statusMessage.value = app().localizedString(R.string.email_account_removed)
        }
    }

    fun selectAccount(accountId: String) {
        if (_selectedAccountId.value == accountId) return
        _selectedAccountId.value = accountId
        _currentFolder.value = EmailFolder.INBOX_NAME
        exitSelectionMode()
        closeSearch()
        viewModelScope.launch { emailDataStore.setSelectedAccountId(accountId) }
        sync()
    }

    fun selectFolder(fullName: String) {
        if (_selectedAccountId.value == UNIFIED_ACCOUNT_ID) return
        _currentFolder.value = fullName
        exitSelectionMode()
        closeSearch()
        sync()
    }

    // ── Sync ──────────────────────────────────────────────────────────────

    private var syncJob: Job? = null

    /** Syncs the folder list (when unknown) and the visible folder(s). */
    fun sync(force: Boolean = false) {
        if (syncJob?.isActive == true) return
        val id = _selectedAccountId.value ?: return
        val targets: List<Pair<EmailAccount, String>> = if (id == UNIFIED_ACCOUNT_ID) {
            accounts.value.map { it to EmailFolder.INBOX_NAME }
        } else {
            val account = accounts.value.firstOrNull { it.id == id } ?: return
            listOf(account to _currentFolder.value)
        }
        syncJob = viewModelScope.launch {
            _isSyncing.value = true
            var failure: String? = null
            for ((account, folder) in targets) {
                val key = cache.listKey(account.id, folder)
                val fresh = (System.currentTimeMillis() - (lastSyncAt[key] ?: 0L)) < MIN_SYNC_INTERVAL_MS
                if (fresh && !force && cache.loadMessages(account.id, folder).isNotEmpty()) continue
                try {
                    if (cache.loadFolders(account.id).isEmpty() || force) repository.refreshFolders(account)
                    repository.syncFolder(account, folder)
                    lastSyncAt[key] = System.currentTimeMillis()
                } catch (e: Exception) {
                    ZuneLog.e(TAG, "sync failed for ${account.email}/$folder", e)
                    failure = userText(e)
                }
            }
            _isSyncing.value = false
            failure?.let { _statusMessage.value = it }
        }
    }

    fun loadMore() {
        val id = _selectedAccountId.value ?: return
        if (id == UNIFIED_ACCOUNT_ID || _isLoadingMore.value) return
        val account = accounts.value.firstOrNull { it.id == id } ?: return
        val oldest = messages.value.minByOrNull { it.uid } ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val added = repository.loadOlder(account, _currentFolder.value, oldest.uid)
                if (added == 0) _statusMessage.value = app().localizedString(R.string.email_no_more)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "loadMore failed", e)
                _statusMessage.value = userText(e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // ── Reader ────────────────────────────────────────────────────────────

    fun open(message: EmailMessage) {
        val account = accountOf(message) ?: return
        _openMessage.value = message
        viewModelScope.launch {
            _isLoadingBody.value = true
            try {
                val full = repository.fetchBody(account, message)
                if (_openMessage.value?.key == message.key) _openMessage.value = full
                if (!message.isRead) repository.setSeen(account, message.folder, setOf(message.uid), true)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "open failed", e)
                _statusMessage.value = userText(e)
            } finally {
                _isLoadingBody.value = false
            }
        }
    }

    /** Opens the message addressed by a notification, syncing the folder first if needed. */
    fun openByAddress(accountId: String, folder: String, uid: Long) {
        val account = accounts.value.firstOrNull { it.id == accountId } ?: return
        selectAccount(accountId)
        if (_currentFolder.value != folder) selectFolder(folder)
        viewModelScope.launch {
            var header = cache.loadMessages(accountId, folder).firstOrNull { it.uid == uid }
            if (header == null) {
                runCatching { repository.syncFolder(account, folder) }
                header = cache.loadMessages(accountId, folder).firstOrNull { it.uid == uid }
            }
            header?.let { open(it) }
        }
    }

    fun closeMessage() {
        _openMessage.value = null
    }

    fun downloadAttachment(message: EmailMessage, attachment: EmailAttachment, onReady: (File) -> Unit) {
        val account = accountOf(message) ?: return
        viewModelScope.launch {
            try {
                val file = repository.downloadAttachment(account, message, attachment)
                _openMessage.value?.let { open ->
                    if (open.key == message.key) _openMessage.value = open.copy(attachments = open.attachments.map { if (it.partPath == attachment.partPath) it.copy(localPath = file.absolutePath) else it })
                }
                onReady(file)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "downloadAttachment failed", e)
                _statusMessage.value = userText(e)
            }
        }
    }

    // ── Flags / move / delete ─────────────────────────────────────────────

    fun setRead(keys: Set<String>, read: Boolean) = perGroup(keys) { account, folder, uids ->
        repository.setSeen(account, folder, uids, read)
    }

    fun setFlagged(keys: Set<String>, flagged: Boolean) = perGroup(keys) { account, folder, uids ->
        repository.setFlagged(account, folder, uids, flagged)
    }

    fun delete(keys: Set<String>) = perGroup(keys, statusRes = R.string.email_deleted) { account, folder, uids ->
        repository.delete(account, folder, uids)
    }

    fun archive(keys: Set<String>) = perGroup(keys, statusRes = R.string.email_archived) { account, folder, uids ->
        val archive = cache.folder(account.id, EmailFolderType.ARCHIVE)
            ?: throw EmailException(R.string.email_error_no_archive)
        repository.move(account, folder, uids, archive.fullName)
    }

    fun markSpam(keys: Set<String>) = perGroup(keys, statusRes = R.string.email_marked_spam) { account, folder, uids ->
        val spam = cache.folder(account.id, EmailFolderType.SPAM)
            ?: throw EmailException(R.string.email_error_no_spam)
        repository.move(account, folder, uids, spam.fullName)
    }

    fun move(keys: Set<String>, targetFolder: String) = perGroup(keys, statusRes = R.string.email_moved) { account, folder, uids ->
        repository.move(account, folder, uids, targetFolder)
    }

    /** Runs [action] once per (account, folder) group of the given message keys. */
    private fun perGroup(
        keys: Set<String>,
        statusRes: Int? = null,
        action: suspend (EmailAccount, String, Set<Long>) -> Unit
    ) {
        if (keys.isEmpty()) return
        val grouped = keys.mapNotNull { key ->
            val parts = key.split('|'); if (parts.size != 3) null else Triple(parts[0], parts[1], parts[2].toLongOrNull() ?: return@mapNotNull null)
        }.groupBy({ it.first to it.second }, { it.third })
        val openKey = _openMessage.value?.key
        viewModelScope.launch {
            var failure: String? = null
            for ((group, uids) in grouped) {
                val account = accounts.value.firstOrNull { it.id == group.first } ?: continue
                try {
                    action(account, group.second, uids.toSet())
                } catch (e: Exception) {
                    ZuneLog.e(TAG, "perGroup action failed", e)
                    failure = userText(e)
                }
            }
            exitSelectionMode()
            if (openKey != null && openKey in keys && statusRes != null) _openMessage.value = null
            else if (openKey != null && openKey in keys) {
                // Refresh flags of the open message from the cache.
                val open = _openMessage.value ?: return@launch
                cache.loadMessages(open.accountId, open.folder).firstOrNull { it.uid == open.uid }?.let { h ->
                    _openMessage.value = open.copy(isRead = h.isRead, isFlagged = h.isFlagged, isAnswered = h.isAnswered)
                }
            }
            _statusMessage.value = failure ?: statusRes?.let { app().localizedString(it) }
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun enterSelectionMode(initialKey: String? = null) {
        _isSelectionMode.value = true
        _selectedKeys.value = initialKey?.let { setOf(it) } ?: emptySet()
    }

    fun toggleSelected(key: String) {
        _selectedKeys.value = _selectedKeys.value.let { if (key in it) it - key else it + key }
    }

    fun selectAll(keys: List<String>) {
        _selectedKeys.value = keys.toSet()
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedKeys.value = emptySet()
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun toggleSearch() {
        if (_isSearchOpen.value) closeSearch() else _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        _serverResults.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) _serverResults.value = null
    }

    /** Searches the current folder on the server (unified: every inbox). */
    fun searchOnServer() {
        val query = _searchQuery.value.trim()
        if (query.length < 2) return
        val id = _selectedAccountId.value ?: return
        val targets = if (id == UNIFIED_ACCOUNT_ID) accounts.value.map { it to EmailFolder.INBOX_NAME }
        else accounts.value.filter { it.id == id }.map { it to _currentFolder.value }
        viewModelScope.launch {
            _isSearching.value = true
            val hits = mutableListOf<EmailMessage>()
            var failure: String? = null
            for ((account, folder) in targets) {
                try {
                    hits += repository.search(account, folder, query)
                } catch (e: Exception) {
                    ZuneLog.e(TAG, "searchOnServer failed", e)
                    failure = userText(e)
                }
            }
            _serverResults.value = hits.sortedByDescending { it.date }
            _isSearching.value = false
            failure?.let { _statusMessage.value = it }
        }
    }

    // ── Compose ───────────────────────────────────────────────────────────

    fun newMessage(to: String = "", cc: String = "", bcc: String = "", subject: String = "", body: String = "", attachmentUris: List<String> = emptyList()) {
        val accountId = composeAccountId() ?: return
        _composeDraft.value = EmailDraft(accountId = accountId, to = to, cc = cc, bcc = bcc, subject = subject, body = body, attachmentUris = attachmentUris)
    }

    fun reply(message: EmailMessage, replyAll: Boolean) {
        val account = accountOf(message) ?: return
        val replyTarget = message.replyTo ?: message.from
        val others = if (replyAll) {
            (message.to + message.cc).filter { !it.address.equals(account.email, ignoreCase = true) && !it.address.equals(replyTarget.address, ignoreCase = true) }
        } else emptyList()
        _composeDraft.value = EmailDraft(
            accountId = account.id,
            to = replyTarget.toString(),
            cc = others.joinToString(", ") { it.toString() },
            subject = withPrefix(message.subject, "Re:"),
            quotedText = quoteOf(message),
            kind = if (replyAll) EmailDraftKind.REPLY_ALL else EmailDraftKind.REPLY,
            inReplyTo = message.messageId,
            references = listOfNotNull(message.references, message.messageId).joinToString(" ").ifBlank { null },
            repliedMessageKey = message.key
        )
    }

    fun forward(message: EmailMessage) {
        val account = accountOf(message) ?: return
        _composeDraft.value = EmailDraft(
            accountId = account.id,
            subject = withPrefix(message.subject, "Fwd:"),
            quotedText = forwardTextOf(message),
            kind = EmailDraftKind.FORWARD,
            forwardedAttachments = message.attachments.filter { it.contentId == null },
            forwardedMessageKey = message.key
        )
    }

    fun openDraft(draft: EmailDraft) {
        _composeDraft.value = draft
    }

    fun updateDraft(draft: EmailDraft) {
        _composeDraft.value = draft
    }

    /** Persists the open draft (dropped when empty) and closes the composer. */
    fun saveDraftAndClose() {
        val draft = _composeDraft.value ?: return
        _composeDraft.value = null
        viewModelScope.launch {
            if (draft.isBlank) emailDataStore.removeDraft(draft.id)
            else {
                emailDataStore.upsertDraft(draft.copy(updatedAt = System.currentTimeMillis()))
                _statusMessage.value = app().localizedString(R.string.email_draft_saved)
            }
        }
    }

    fun discardDraft() {
        val draft = _composeDraft.value ?: return
        _composeDraft.value = null
        viewModelScope.launch { emailDataStore.removeDraft(draft.id) }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch { emailDataStore.removeDraft(draftId) }
    }

    fun send(onDone: (Boolean) -> Unit) {
        val draft = _composeDraft.value ?: return
        val account = accounts.value.firstOrNull { it.id == draft.accountId } ?: return
        val recipients = EmailAddress.parseList(draft.to) + EmailAddress.parseList(draft.cc) + EmailAddress.parseList(draft.bcc)
        if (recipients.isEmpty()) { _statusMessage.value = app().localizedString(R.string.email_error_no_recipient); onDone(false); return }
        recipients.firstOrNull { !EmailAddress.isValid(it.address) }?.let {
            _statusMessage.value = app().localizedString(R.string.email_error_bad_address, it.address); onDone(false); return
        }
        viewModelScope.launch {
            _isSending.value = true
            try {
                repository.send(account, draft)
                emailDataStore.removeDraft(draft.id)
                _composeDraft.value = null
                _statusMessage.value = app().localizedString(R.string.email_sent)
                onDone(true)
                // Refresh the Sent folder if it is the one on screen.
                cache.folder(account.id, EmailFolderType.SENT)?.let { sent ->
                    if (_currentFolder.value == sent.fullName) runCatching { repository.syncFolder(account, sent.fullName) }
                }
            } catch (e: Exception) {
                ZuneLog.e(TAG, "send failed", e)
                _statusMessage.value = userText(e)
                onDone(false)
            } finally {
                _isSending.value = false
            }
        }
    }

    // ── Bridge ────────────────────────────────────────────────────────────

    /** Applies a pending cross-hub request; returns true when the composer should open. */
    fun consumeBridgeRequest(): EmailBridge.Request? {
        val request = EmailBridge.consume() ?: return null
        when (request) {
            is EmailBridge.Request.Open -> openByAddress(request.accountId, request.folder, request.uid)
            is EmailBridge.Request.Inbox -> selectAccount(request.accountId ?: accounts.value.firstOrNull()?.id ?: return request)
            is EmailBridge.Request.Compose -> newMessage(request.to, request.cc, request.bcc, request.subject, request.body, request.attachmentUris)
        }
        return request
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun showStatus(text: String) {
        _statusMessage.value = text
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    fun accountOf(message: EmailMessage): EmailAccount? = accounts.value.firstOrNull { it.id == message.accountId }

    private fun composeAccountId(): String? {
        val id = _selectedAccountId.value
        return if (id == null || id == UNIFIED_ACCOUNT_ID) accounts.value.firstOrNull()?.id else id
    }

    private fun withPrefix(subject: String, prefix: String): String =
        if (subject.trim().startsWith(prefix, ignoreCase = true)) subject.trim() else "$prefix ${subject.trim()}".trim()

    private fun quoteOf(message: EmailMessage): String {
        val header = app().localizedString(R.string.email_quote_header, formatDateTime(message.date), message.from.toString())
        val body = message.bodyText ?: message.bodyHtml?.let(EmailRepository::htmlToText) ?: message.snippet
        return header + "\n" + body.lines().joinToString("\n") { "> $it" }
    }

    private fun forwardTextOf(message: EmailMessage): String {
        val app = app()
        val body = message.bodyText ?: message.bodyHtml?.let(EmailRepository::htmlToText) ?: message.snippet
        return buildString {
            append(app.localizedString(R.string.email_forward_header)).append('\n')
            append(app.localizedString(R.string.email_from_label)).append(": ").append(message.from).append('\n')
            append(app.localizedString(R.string.email_date_label)).append(": ").append(formatDateTime(message.date)).append('\n')
            append(app.localizedString(R.string.email_subject_label)).append(": ").append(message.subject).append('\n')
            append(app.localizedString(R.string.email_to_label)).append(": ").append(message.to.joinToString(", ")).append("\n\n")
            append(body)
        }
    }

    private fun formatDateTime(millis: Long): String =
        SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    private fun userText(e: Exception): String = when (e) {
        is EmailException -> app().localizedString(e.messageRes)
        else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: app().localizedString(R.string.error_unknown)
    }

    private fun app(): Application = getApplication()

    private companion object {
        const val TAG = "EmailHubViewModel"
        const val MIN_SYNC_INTERVAL_MS = 90_000L
    }
}
