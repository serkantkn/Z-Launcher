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
import com.serkantkn.zunelauncher.data.model.ContactEmail
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.ContactEdit
import com.serkantkn.zunelauncher.data.model.ContactGroup
import com.serkantkn.zunelauncher.data.model.ContactNumber
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.util.ContactTouch
import com.serkantkn.zunelauncher.util.recentContacts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PeopleHubViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository = application.appContainer.contactRepository
    private val callLogRepository = application.appContainer.callLogRepository
    private val settingsDataStore = application.appContainer.settingsDataStore

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

    private val _groups = MutableStateFlow<List<ContactGroup>>(emptyList())
    val groups: StateFlow<List<ContactGroup>> = _groups.asStateFlow()

    private val _openGroup = MutableStateFlow<ContactGroup?>(null)
    /** The group being looked inside, or null while the list of them is on show. */
    val openGroup: StateFlow<ContactGroup?> = _openGroup.asStateFlow()

    private val _groupMembers = MutableStateFlow<List<ContactModel>>(emptyList())
    val groupMembers: StateFlow<List<ContactModel>> = _groupMembers.asStateFlow()

    /** People who already have a tile of their own on the start screen. */
    private val _pinnedContactIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedContactIds: StateFlow<Set<String>> = _pinnedContactIds.asStateFlow()

    init {
        if (_hasPermission.value) {
            loadContacts()
        }
        loadGroups()
        viewModelScope.launch {
            settingsDataStore.startTiles.collect { tiles ->
                _pinnedContactIds.value = tiles.mapNotNull { it.contactId }.toSet()
            }
        }
    }

    /**
     * Puts somebody on the start screen as a tile of their own, or takes them off again.
     *
     * A face on the board is the tile Windows Phone was best known for, and on a launcher it is
     * the most natural thing the people hub can offer.
     */
    fun togglePinToStart(contact: ContactModel) {
        viewModelScope.launch {
            val tiles = settingsDataStore.startTiles.first()
            val updated = if (tiles.any { it.contactId == contact.id }) {
                StartFolders.removeTile(tiles, "${StartTileItem.PERSON_PREFIX}${contact.id}")
            } else {
                tiles + StartTileItem.fromPerson(contact.id, contact.name)
            }
            settingsDataStore.setStartTiles(updated)
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
                numbersByContact = contactRepository.getAllNumbersByContact()
                updateGroupedContacts(contacts, _searchQuery.value)
                _favoriteContacts.value = contacts.filter { it.isFavorite }
                _recentContacts.value = loadRecent(contacts)
                _errorMessage.value = null
            } catch (e: Exception) {
                ZuneLog.e("PeopleHubViewModel", "loadContacts failed", e)
                _errorMessage.value = e.toUserMessage(getApplication())
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * The recent page, worked out rather than read off the contact.
     *
     * Android's own "last time contacted" has read nought for everybody since Android 10, so this
     * asks the call log and the message list who has actually been in touch and matches the
     * numbers back to people.
     */
    private suspend fun loadRecent(contacts: List<ContactModel>): List<ContactModel> = try {
        val touches = mutableListOf<ContactTouch>()
        callLogRepository.getRecentCalls().forEach { call ->
            touches += ContactTouch(call.number, call.dateMillis)
        }
        runCatching {
            com.serkantkn.zunelauncher.data.repository.SmsRepository
                .getConversations(getApplication())
        }.getOrDefault(emptyList()).forEach { conversation ->
            touches += ContactTouch(conversation.address, conversation.timestamp)
        }
        recentContacts(contacts, numbersByContact, touches)
    } catch (e: Exception) {
        ZuneLog.w("PeopleHubViewModel", "could not work out who was in touch", e)
        emptyList()
    }

    /** Stars or unstars somebody; the favourites page is made of exactly this. */
    fun toggleFavorite(contact: ContactModel) {
        viewModelScope.launch {
            val wanted = !contact.isFavorite
            if (!contactRepository.setFavorite(contact.id, wanted)) return@launch
            _selectedContactDetail.update { detail ->
                if (detail?.contact?.id != contact.id) detail
                else detail.copy(contact = detail.contact.copy(isFavorite = wanted))
            }
            loadContacts()
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
            // Typing digits should find the person those digits belong to, not nothing.
            val digits = query.filter { it.isDigit() }
            contacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                    (digits.length >= 3 && numbersOf(contact.id).any { it.contains(digits) })
            }
        }

        val grouped = filtered.groupBy { contact ->
            val firstChar = contact.name.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toSortedMap()
        
        _groupedContacts.value = grouped
    }

    /** Numbers by contact, kept so a search over digits does not query for every keystroke. */
    private var numbersByContact: Map<String, List<String>> = emptyMap()

    private fun numbersOf(contactId: String): List<String> =
        numbersByContact[contactId].orEmpty().map { it.filter { ch -> ch.isDigit() } }

    /** Finds somebody by id, for a tile on the start board that names them. */
    suspend fun contactById(contactId: String): ContactModel? =
        _allContacts.value.firstOrNull { it.id == contactId }
            ?: runCatching { contactRepository.getContacts() }.getOrDefault(emptyList())
                .firstOrNull { it.id == contactId }

    fun selectContact(contact: ContactModel?) {
        if (contact == null) {
            _selectedContactDetail.value = null
            return
        }

        viewModelScope.launch {
            _selectedContactDetail.value = contactRepository.getDetail(contact)
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

    fun updateContact(contactId: String, edit: ContactEdit, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = contactRepository.updateContact(contactId, edit)
            if (success) {
                val existing = _selectedContactDetail.value?.contact
                val updatedModel = ContactModel(
                    id = contactId,
                    name = "${edit.firstName} ${edit.lastName}".trim(),
                    photoUri = existing?.photoUri,
                    isFavorite = existing?.isFavorite ?: false,
                    lastTimeContacted = existing?.lastTimeContacted ?: 0,
                    hasPhoneNumber = edit.numbers.any { it.number.isNotBlank() }
                )
                // Read back rather than guessing: new rows have ids now, deleted ones are gone.
                _selectedContactDetail.value = contactRepository.getDetail(updatedModel)
                loadContacts()
            }
            onComplete(success)
        }
    }

    /** Gives the person whose card is open a photograph. */
    fun setPhoto(photo: android.graphics.Bitmap, onComplete: (Boolean) -> Unit = {}) {
        val detail = _selectedContactDetail.value ?: return
        viewModelScope.launch {
            val success = contactRepository.setPhoto(detail.contact.id, photo)
            if (success) refreshOpenContact(detail.contact.id, expectPhotoChange = true)
            onComplete(success)
        }
    }

    fun removePhoto(onComplete: (Boolean) -> Unit = {}) {
        val detail = _selectedContactDetail.value ?: return
        viewModelScope.launch {
            val success = contactRepository.removePhoto(detail.contact.id)
            if (success) refreshOpenContact(detail.contact.id, expectPhotoChange = true)
            onComplete(success)
        }
    }

    /**
     * Reads the person back off the phone after their picture changed.
     *
     * The address book does not publish the new address for a photograph the instant the file is
     * written — it makes its own thumbnail first — so reading straight back can still see the old
     * one and leave the list showing an initial where there is now a face. This waits for the
     * address to actually change, but only for a moment: if it never does, what was read is shown
     * rather than spinning.
     */
    private suspend fun refreshOpenContact(contactId: String, expectPhotoChange: Boolean = false) {
        val before = _allContacts.value.firstOrNull { it.id == contactId }?.photoUri

        var contacts = emptyList<ContactModel>()
        repeat(PHOTO_READ_ATTEMPTS) { attempt ->
            contacts = runCatching { contactRepository.getContacts() }.getOrDefault(emptyList())
            val now = contacts.firstOrNull { it.id == contactId }?.photoUri
            if (!expectPhotoChange || now != before) return@repeat
            if (attempt < PHOTO_READ_ATTEMPTS - 1) kotlinx.coroutines.delay(PHOTO_READ_WAIT_MILLIS)
        }

        _allContacts.value = contacts
        numbersByContact = contactRepository.getAllNumbersByContact()
        contacts.firstOrNull { it.id == contactId }?.let { fresh ->
            _selectedContactDetail.value = contactRepository.getDetail(fresh)
        }
        updateGroupedContacts(contacts, _searchQuery.value)
        _favoriteContacts.value = contacts.filter { it.isFavorite }
        _recentContacts.value = loadRecent(contacts)
    }

    private companion object {
        const val PHOTO_READ_ATTEMPTS = 6
        const val PHOTO_READ_WAIT_MILLIS = 250L
    }

    // ── Groups ────────────────────────────────────────────────────────────────────────────────

    fun loadGroups() {
        viewModelScope.launch { _groups.value = contactRepository.getGroups() }
    }

    fun openGroup(group: ContactGroup?) {
        _openGroup.value = group
        if (group == null) {
            _groupMembers.value = emptyList()
            return
        }
        viewModelScope.launch {
            val memberIds = contactRepository.getGroupMembers(group.id).toSet()
            _groupMembers.value = _allContacts.value.filter { it.id in memberIds }
        }
    }

    fun createGroup(title: String) {
        viewModelScope.launch {
            if (contactRepository.createGroup(title)) loadGroups()
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            if (contactRepository.deleteGroup(groupId)) {
                if (_openGroup.value?.id == groupId) openGroup(null)
                loadGroups()
            }
        }
    }

    /** Puts the person whose card is open into a group, or takes them out of it. */
    fun toggleGroupMembership(group: ContactGroup) {
        val detail = _selectedContactDetail.value ?: return
        viewModelScope.launch {
            val member = group.id in detail.groupIds
            if (!contactRepository.setGroupMembership(detail.contact.id, group.id, !member)) return@launch
            _selectedContactDetail.value = contactRepository.getDetail(detail.contact)
            loadGroups()
            _openGroup.value?.let { openGroup(it) }
        }
    }

    // ── Duplicates ────────────────────────────────────────────────────────────────────────────

    /**
     * Joins the open contact with somebody else, so the phone treats them as one person — for the
     * same person saved twice, once from the SIM and once from an account.
     */
    fun linkWith(other: ContactModel, onComplete: (Boolean) -> Unit = {}) {
        val detail = _selectedContactDetail.value ?: return
        viewModelScope.launch {
            val success = contactRepository.linkContacts(detail.contact.id, other.id)
            if (success) {
                _selectedContactDetail.value = contactRepository.getDetail(detail.contact)
                loadContacts()
            }
            onComplete(success)
        }
    }

    fun unlinkOpenContact(onComplete: (Boolean) -> Unit = {}) {
        val detail = _selectedContactDetail.value ?: return
        viewModelScope.launch {
            val success = contactRepository.unlinkContact(detail.contact.id)
            if (success) {
                _selectedContactDetail.value = null
                loadContacts()
            }
            onComplete(success)
        }
    }
}
