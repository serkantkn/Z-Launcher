package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SocialRepository {
    private val _messages = MutableStateFlow<List<SocialMessageModel>>(emptyList())
    val messages: StateFlow<List<SocialMessageModel>> = _messages.asStateFlow()

    fun addOrUpdateMessage(message: SocialMessageModel) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == message.id }
        if (index != -1) {
            currentList[index] = message
        } else {
            currentList.add(message)
        }
        currentList.sortByDescending { it.timestamp }
        _messages.value = currentList
    }

    fun removeMessage(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
    }
    
    fun clearAll() {
        _messages.value = emptyList()
    }
}
