package com.serkantkn.zunelauncher.ui.screens.people

import com.serkantkn.zunelauncher.util.toUserMessage
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.ContactDetailModel
import com.serkantkn.zunelauncher.data.model.ContactModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PeopleHubViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository = application.appContainer.contactRepository

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allContacts = MutableStateFlow<List<ContactModel>>(emptyList())
    
    // Derived flows
    private val _groupedContacts = MutableStateFlow<Map<Char, List<ContactModel>>>(emptyMap())
    val groupedContacts: StateFlow<Map<Char, List<ContactModel>>> = _groupedContacts.asStateFlow()

    private val _favoriteContacts = MutableStateFlow<List<ContactModel>>(emptyList())
    val favoriteContacts: StateFlow<List<ContactModel>> = _favoriteContacts.asStateFlow()

    private val _recentContacts = MutableStateFlow<List<ContactModel>>(emptyList())
    val recentContacts: StateFlow<List<ContactModel>> = _recentContacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedContactDetail = MutableStateFlow<ContactDetailModel?>(null)
    val selectedContactDetail: StateFlow<ContactDetailModel?> = _selectedContactDetail.asStateFlow()

    init {
        if (_hasPermission.value) {
            loadContacts()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            loadContacts()
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun loadContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val contacts = contactRepository.getContacts()
                _allContacts.value = contacts
                updateGroupedContacts(contacts, _searchQuery.value)
                _favoriteContacts.value = contacts.filter { it.isFavorite }
                _recentContacts.value = contacts.filter { it.lastTimeContacted > 0 }
                    .sortedByDescending { it.lastTimeContacted }
                    .take(20) // Show top 20 recent
                _errorMessage.value = null
            } catch (e: Exception) {
                ZuneLog.e("PeopleHubViewModel", "loadContacts failed", e)
                _errorMessage.value = e.toUserMessage(getApplication())
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        updateGroupedContacts(_allContacts.value, query)
    }

    private fun updateGroupedContacts(contacts: List<ContactModel>, query: String) {
        val filtered = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter { it.name.contains(query, ignoreCase = true) }
        }

        val grouped = filtered.groupBy { contact ->
            val firstChar = contact.name.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toSortedMap()
        
        _groupedContacts.value = grouped
    }

    fun selectContact(contact: ContactModel?) {
        if (contact == null) {
            _selectedContactDetail.value = null
            return
        }
        
        viewModelScope.launch {
            val phoneNumbers = contactRepository.getPhoneNumbers(contact.id)
            _selectedContactDetail.value = ContactDetailModel(
                contact = contact,
                phoneNumbers = phoneNumbers
            )
        }
    }

    fun createContact(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        email: String,
        saveToGoogle: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = contactRepository.saveContact(firstName, lastName, phoneNumber, email, saveToGoogle)
            if (success) {
                loadContacts()
            }
            onComplete(success)
        }
    }

    fun deleteContact(contactId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = contactRepository.deleteContact(contactId)
            if (success) {
                _selectedContactDetail.value = null
                loadContacts()
            }
            onComplete(success)
        }
    }

    fun updateContact(
        contactId: String,
        firstName: String,
        lastName: String,
        phoneNumber: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = contactRepository.updateContact(contactId, firstName, lastName, phoneNumber)
            if (success) {
                val updatedName = "$firstName $lastName".trim()
                val updatedModel = ContactModel(
                    id = contactId,
                    name = updatedName,
                    photoUri = _selectedContactDetail.value?.contact?.photoUri,
                    isFavorite = _selectedContactDetail.value?.contact?.isFavorite ?: false,
                    lastTimeContacted = _selectedContactDetail.value?.contact?.lastTimeContacted ?: 0,
                    hasPhoneNumber = phoneNumber.isNotBlank()
                )
                _selectedContactDetail.value = ContactDetailModel(
                    contact = updatedModel,
                    phoneNumbers = if (phoneNumber.isNotBlank()) listOf(phoneNumber) else emptyList()
                )
                loadContacts()
            }
            onComplete(success)
        }
    }
}
