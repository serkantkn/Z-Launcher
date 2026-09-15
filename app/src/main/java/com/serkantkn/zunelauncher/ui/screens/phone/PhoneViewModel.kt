package com.serkantkn.zunelauncher.ui.screens.phone

import com.serkantkn.zunelauncher.util.toUserMessage
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.CallLogModel
import com.serkantkn.zunelauncher.data.model.ContactModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.serkantkn.zunelauncher.data.model.SpeedDialEntry
import com.serkantkn.zunelauncher.util.BlockedNumber
import com.serkantkn.zunelauncher.util.BlockedNumbers
import com.serkantkn.zunelauncher.util.CallGroup
import com.serkantkn.zunelauncher.util.PhoneAccounts
import com.serkantkn.zunelauncher.util.PhoneNumbers
import com.serkantkn.zunelauncher.util.SimLine
import com.serkantkn.zunelauncher.util.Voicemail
import com.serkantkn.zunelauncher.util.groupCalls
import com.serkantkn.zunelauncher.util.missedCalls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val callLogRepository = application.appContainer.callLogRepository
    private val contactRepository = application.appContainer.contactRepository
    private val phoneDataStore = application.appContainer.phoneDataStore

    private val _recentCalls = MutableStateFlow<List<CallLogModel>>(emptyList())
    val recentCalls: StateFlow<List<CallLogModel>> = _recentCalls.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _allContacts = MutableStateFlow<List<Pair<ContactModel, String>>>(emptyList())
    /** Every contact with a number, for the speed dial picker. */
    val allContacts: StateFlow<List<Pair<ContactModel, String>>> = _allContacts.asStateFlow()

    private val _missedOnly = MutableStateFlow(false)
    val missedOnly: StateFlow<Boolean> = _missedOnly.asStateFlow()

    /**
     * The history as the hub shows it: runs of calls with the same person collapsed into one row,
     * and only the unanswered ones when the filter is on.
     */
    val callGroups: StateFlow<List<CallGroup>> = combine(_recentCalls, _missedOnly) { calls, missedOnly ->
        groupCalls(if (missedOnly) missedCalls(calls) else calls)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missedCount: StateFlow<Int> = _recentCalls
        .map { missedCalls(it).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val speedDial: StateFlow<List<SpeedDialEntry>> = phoneDataStore.speedDial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── The SIM a call goes out on ────────────────────────────────────────────────────────────

    private val _simLines = MutableStateFlow<List<SimLine>>(emptyList())
    /** The phone's lines. Empty or one long on every single-SIM phone, so nothing is ever asked. */
    val simLines: StateFlow<List<SimLine>> = _simLines.asStateFlow()

    val preferredSim: StateFlow<String?> = phoneDataStore.preferredSim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadSimLines() {
        viewModelScope.launch {
            _simLines.value = withContext(Dispatchers.IO) { PhoneAccounts.lines(getApplication()) }
        }
    }

    /** Null puts the choice back to being asked before every call. */
    fun setPreferredSim(key: String?) {
        viewModelScope.launch { phoneDataStore.setPreferredSim(key) }
    }

    // ── Voicemail ─────────────────────────────────────────────────────────────────────────────

    private val _voicemailWaiting = MutableStateFlow(0)
    /** How many voicemail messages the carrier says are waiting; nought hides the row. */
    val voicemailWaiting: StateFlow<Int> = _voicemailWaiting.asStateFlow()

    private val _hasVoicemail = MutableStateFlow(false)
    val hasVoicemail: StateFlow<Boolean> = _hasVoicemail.asStateFlow()

    fun loadVoicemail() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                Voicemail.exists(app) to Voicemail.waitingCount(app)
            }.let { (exists, waiting) ->
                _hasVoicemail.value = exists
                _voicemailWaiting.value = waiting
            }
        }
    }

    // ── Blocked numbers ───────────────────────────────────────────────────────────────────────

    private val _blockedNumbers = MutableStateFlow<List<BlockedNumber>>(emptyList())
    /** The phone's blocked list, newest first, for the page that shows it. */
    val blockedNumbers: StateFlow<List<BlockedNumber>> = _blockedNumbers.asStateFlow()

    /** The same list by match key, so a history row can tell at a glance. */
    val blockedKeys: StateFlow<Set<String>> = _blockedNumbers
        .map { list -> list.map { PhoneNumbers.matchKey(it.number) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _canBlock = MutableStateFlow(false)
    /** Android only lets the phone app touch its blocked list; false hides the button. */
    val canBlock: StateFlow<Boolean> = _canBlock.asStateFlow()

    fun loadBlocked() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val allowed = withContext(Dispatchers.IO) { BlockedNumbers.canBlock(app) }
            _canBlock.value = allowed
            _blockedNumbers.value = if (allowed) {
                withContext(Dispatchers.IO) { BlockedNumbers.all(app) }
            } else {
                emptyList()
            }
        }
    }

    fun isBlocked(number: String): Boolean =
        _blockedNumbers.value.any { PhoneNumbers.sameNumber(it.number, number) }

    /** Blocks a number, or lets it through again if it was already blocked. */
    fun toggleBlock(number: String, onDone: (blocked: Boolean, worked: Boolean) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val wasBlocked = isBlocked(number)
            val worked = withContext(Dispatchers.IO) {
                if (wasBlocked) BlockedNumbers.unblock(app, number) else BlockedNumbers.block(app, number)
            }
            loadBlocked()
            onDone(!wasBlocked, worked)
        }
    }

    fun toggleMissedOnly() {
        _missedOnly.value = !_missedOnly.value
    }

    fun addToSpeedDial(name: String, number: String, photoUri: String? = null) {
        viewModelScope.launch { phoneDataStore.add(SpeedDialEntry(number, name, photoUri)) }
    }

    fun removeFromSpeedDial(number: String) {
        viewModelScope.launch { phoneDataStore.remove(number) }
    }

    fun moveSpeedDial(from: Int, to: Int) {
        viewModelScope.launch { phoneDataStore.move(from, to) }
    }

    
    private val _matchingContacts = MutableStateFlow<List<Pair<ContactModel, String>>>(emptyList())
    val matchingContacts: StateFlow<List<Pair<ContactModel, String>>> = _matchingContacts.asStateFlow()

    fun loadRecentCalls() {
        viewModelScope.launch {
            try {
                _recentCalls.value = callLogRepository.getRecentCalls()
                _errorMessage.value = null
            } catch (e: Exception) {
                ZuneLog.e("PhoneViewModel", "loadRecentCalls failed", e)
                _errorMessage.value = e.toUserMessage(getApplication())
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            try {
                _allContacts.value = contactRepository.getContactsWithNumbers()
            } catch (e: Exception) {
                ZuneLog.e("PhoneViewModel", "loadContacts failed", e)
            }
        }
    }

    fun appendDigit(digit: String) {
        _dialedNumber.value += digit
        updateMatchingContacts()
    }

    fun backspaceDigit() {
        if (_dialedNumber.value.isNotEmpty()) {
            _dialedNumber.value = _dialedNumber.value.dropLast(1)
            updateMatchingContacts()
        }
    }

    fun clearDialedNumber() {
        _dialedNumber.value = ""
        updateMatchingContacts()
    }

    private fun updateMatchingContacts() {
        val query = _dialedNumber.value
        if (query.isEmpty()) {
            _matchingContacts.value = emptyList()
            return
        }

        val matches = _allContacts.value.filter { (contact, number) ->
            val cleanNumber = number.replace(Regex("[^0-9+]"), "")
            cleanNumber.contains(query) || matchesT9(contact.name, query)
        }
        
        _matchingContacts.value = matches.take(5) // Limit to 5 results
    }

    private fun matchesT9(name: String, query: String): Boolean {
        if (query.isEmpty() || name.isEmpty()) return false
        
        val t9Map = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
            'ç' to '2', 'ğ' to '4', 'ı' to '4', 'i' to '4', 
            'ö' to '6', 'ş' to '7', 'ü' to '8'
        )

        val nameT9 = name.lowercase().mapNotNull { t9Map[it] }.joinToString("")
        return nameT9.contains(query)
    }
}
