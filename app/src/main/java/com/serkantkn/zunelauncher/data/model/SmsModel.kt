package com.serkantkn.zunelauncher.data.model

import android.provider.Telephony

/**
 * One conversation in the message list.
 *
 * [threadId] is the phone's own thread number, which is what makes two messages to and from the
 * same person one conversation. A conversation that does not exist yet — one being started from a
 * contact or a typed number — carries [NEW_THREAD_ID] until the first message gives it a real one.
 */
data class SmsConversationModel(
    val threadId: Long,
    val address: String,
    val contactName: String,
    val snippet: String,
    val timestamp: Long,
    val isRead: Boolean,
    val photoUri: String? = null,
    /** How many messages in this conversation have not been read. */
    val unreadCount: Int = 0,
    /** How many messages the conversation holds altogether. */
    val messageCount: Int = 0,
    /** Text typed but never sent, kept by the phone under this thread. */
    val draft: String = "",
    /** Every number the conversation goes to. More than one means a group message. */
    val recipients: List<String> = emptyList(),
    /** The contact this conversation belongs to, when the number is in the address book. */
    val contactId: String? = null
) {
    val isGroup: Boolean get() = recipients.size > 1

    val hasDraft: Boolean get() = draft.isNotBlank()

    /** What to show as the conversation's name: the person, or failing that the number. */
    val title: String get() = contactName.ifBlank { address }

    companion object {
        /** A conversation the phone has no thread for yet. */
        const val NEW_THREAD_ID = -1L
    }
}

/** Where a message is on its way from us to the other phone. */
enum class MessageDelivery {
    /** Still with the radio. */
    SENDING,

    /** Handed to the network. */
    SENT,

    /** The network says the other phone has it. */
    DELIVERED,

    /** It did not go out. */
    FAILED,

    /** Nothing to report: this message came in. */
    NONE
}

data class SmsMessageModel(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    /** The phone's own message box: inbox, sent, outbox, draft, failed. */
    val type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
    /** The delivery report, for messages we sent. */
    val delivery: MessageDelivery = MessageDelivery.NONE,
    /** Picture messages: the launcher shows their text and says that a file came with it. */
    val isMms: Boolean = false,
    val hasAttachment: Boolean = false,
    /** Which SIM it went out on, or -1 when the phone did not say. */
    val subscriptionId: Int = -1
) {
    val isDraft: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_DRAFT

    val isFailed: Boolean get() = delivery == MessageDelivery.FAILED

    val isPending: Boolean get() = delivery == MessageDelivery.SENDING
}
