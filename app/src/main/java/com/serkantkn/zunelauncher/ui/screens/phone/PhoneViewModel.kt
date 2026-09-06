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

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val callLogRepository = application.appContainer.callLogRepository
    private val contactRepository = application.appContainer.contactRepository

    private val _recentCalls = MutableStateFlow<List<CallLogModel>>(emptyList())
    val recentCalls: StateFlow<List<CallLogModel>> = _recentCalls.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _allContacts = MutableStateFlow<List<Pair<ContactModel, String>>>(emptyList())
    
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
