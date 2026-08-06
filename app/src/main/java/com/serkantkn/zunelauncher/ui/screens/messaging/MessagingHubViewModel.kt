package com.serkantkn.zunelauncher.ui.screens.messaging

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.data.repository.ContactRepository
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagingHubViewModel : ViewModel() {

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

    fun checkPermissionAndLoad(context: Context) {
        val hasRead = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val hasSend = ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val granted = hasRead && hasSend
        _hasSmsPermission.value = granted

        if (granted) {
            loadConversations(context)
            if (hasContacts) {
                loadContacts(context)
            }
        }
    }

    fun loadConversations(context: Context) {
        viewModelScope.launch {
            val list = SmsRepository.getConversations(context)
            _conversations.value = list
        }
    }

    fun loadContacts(context: Context) {
        viewModelScope.launch {
            val contactList = ContactRepository(context).getContactsWithNumbers()
            _contacts.value = contactList
        }
    }

    fun openConversation(context: Context, conversation: SmsConversationModel) {
        _selectedConversation.value = conversation
        viewModelScope.launch {
            if (conversation.threadId > 0) {
                val msgs = SmsRepository.getMessagesForThread(context, conversation.threadId)
                _threadMessages.value = msgs
            } else {
                _threadMessages.value = emptyList()
            }
        }
    }

    fun openConversationWithContact(context: Context, contactName: String, phoneNumber: String) {
        val cleanNumber = phoneNumber.replace(" ", "").replace("-", "")
        val existing = _conversations.value.find { 
            it.address.replace(" ", "").replace("-", "").endsWith(cleanNumber.takeLast(9)) 
        }

        if (existing != null) {
            openConversation(context, existing)
        } else {
            val newConv = SmsConversationModel(
                threadId = -1L,
                address = phoneNumber,
                contactName = contactName,
                snippet = "",
                timestamp = System.currentTimeMillis(),
                isRead = true
            )
            openConversation(context, newConv)
        }
    }

    fun closeConversation() {
        _selectedConversation.value = null
        _threadMessages.value = emptyList()
    }

    fun sendSms(context: Context, recipientNumber: String, messageText: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (recipientNumber.isNotBlank() && messageText.isNotBlank()) {
                val success = SmsRepository.sendSms(recipientNumber, messageText)
                if (success) {
                    loadConversations(context)
                    _selectedConversation.value?.let { conv ->
                        openConversation(context, conv)
                    }
                }
                onComplete(success)
            } else {
                onComplete(false)
            }
        }
    }
}
