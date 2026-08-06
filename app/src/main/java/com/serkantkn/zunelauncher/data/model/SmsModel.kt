package com.serkantkn.zunelauncher.data.model

data class SmsConversationModel(
    val threadId: Long,
    val address: String,
    val contactName: String,
    val snippet: String,
    val timestamp: Long,
    val isRead: Boolean,
    val photoUri: String? = null
)

data class SmsMessageModel(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isRead: Boolean
)
