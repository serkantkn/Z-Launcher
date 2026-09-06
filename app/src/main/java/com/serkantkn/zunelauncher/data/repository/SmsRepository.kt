package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsRepository {

    suspend fun getConversations(context: Context): List<SmsConversationModel> = withContext(Dispatchers.IO) {
        val conversations = mutableListOf<SmsConversationModel>()
        try {
            val uri = Uri.parse("content://sms/")
            val projection = arrayOf("thread_id", "address", "body", "date", "read")
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            val seenThreads = mutableSetOf<Long>()

            cursor?.use {
                val threadIdIdx = it.getColumnIndex("thread_id")
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val readIdx = it.getColumnIndex("read")

                while (it.moveToNext()) {
                    val threadId = if (threadIdIdx != -1) it.getLong(threadIdIdx) else 0L
                    if (threadId != 0L && seenThreads.contains(threadId)) continue
                    
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()
                    val read = if (readIdx != -1) it.getInt(readIdx) == 1 else true

                    if (threadId != 0L) {
                        seenThreads.add(threadId)
                    }

                    val contactName = getContactNameByNumber(context, address)
                        .ifBlank { address }

                    conversations.add(
                        SmsConversationModel(
                            threadId = threadId,
                            address = address,
                            contactName = contactName,
                            snippet = body,
                            timestamp = date,
                            isRead = read
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ZuneLog.e("SmsRepository", "getConversations failed", e)
            throw e
        }
        conversations
    }

    suspend fun getMessagesForThread(context: Context, threadId: Long): List<SmsMessageModel> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessageModel>()
        try {
            val uri = Uri.parse("content://sms/")
            val selection = "thread_id = ?"
            val selectionArgs = arrayOf(threadId.toString())
            val cursor = context.contentResolver.query(
                uri,
                null,
                selection,
                selectionArgs,
                "date ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val typeIdx = it.getColumnIndex("type")
                val readIdx = it.getColumnIndex("read")

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getLong(idIdx) else 0L
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()
                    val type = if (typeIdx != -1) it.getInt(typeIdx) else 1
                    val read = if (readIdx != -1) it.getInt(readIdx) == 1 else true

                    val isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX

                    messages.add(
                        SmsMessageModel(
                            id = id,
                            threadId = threadId,
                            address = address,
                            body = body,
                            timestamp = date,
                            isOutgoing = isOutgoing,
                            isRead = read
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ZuneLog.e("SmsRepository", "getMessagesForThread failed", e)
            throw e
        }
        messages
    }

    fun sendSms(phoneNumber: String, messageText: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
            true
        } catch (e: Exception) {
            ZuneLog.e("SmsRepository", "sendSms failed", e)
            false
        }
    }

    private fun getContactNameByNumber(context: Context, phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: ""
                } else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
