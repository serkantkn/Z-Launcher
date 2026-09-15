package com.serkantkn.zunelauncher.data.repository

import android.app.role.RoleManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import com.serkantkn.zunelauncher.data.model.MessageDelivery
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.util.PhoneNumbers
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's own message store, read the way the messages app reads it.
 *
 * Conversations come from the phone's thread list rather than from a scan of every message, which
 * is both faster and the only way a picture message shows up at all — the thread list is where the
 * phone joins text and picture messages into one conversation.
 *
 * Writing is a different matter. Android lets only the app that owns messaging change the store:
 * marking a conversation read, deleting it, keeping a draft. Everything here that writes says so
 * by returning false when the launcher is not that app, rather than failing quietly.
 */
object SmsRepository {

    private const val TAG = "SmsRepository"

    /** The phone's thread list. "simple" asks for the threads themselves, not their messages. */
    private val THREADS_URI: Uri = Telephony.Threads.CONTENT_URI
        .buildUpon()
        .appendQueryParameter("simple", "true")
        .build()

    private val CANONICAL_ADDRESSES_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")
    private val MMS_URI: Uri = Uri.parse("content://mms")
    private val MMS_PART_URI: Uri = Uri.parse("content://mms/part")

    /**
     * Whether the launcher is the app Android lets change the message store.
     *
     * Asked two ways on purpose. The old question reads a setting that a phone may leave unwritten
     * while the newer role system has already handed messaging over — and a launcher that believed
     * only the old answer would refuse to mark a message read on a phone where it plainly may.
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the phone would not say who owns messaging", e)
            false
        }
    }

    // ════════════════════════════════════════════════════════════
    // READING
    // ════════════════════════════════════════════════════════════

    suspend fun getConversations(context: Context): List<SmsConversationModel> =
        withContext(Dispatchers.IO) {
            val fromThreads = runCatching { readThreads(context) }
                .onFailure { ZuneLog.w(TAG, "the thread list would not open", it) }
                .getOrDefault(emptyList())

            // Some phones keep the thread list to themselves. A scan of the messages is coarser —
            // it cannot see picture-only conversations — but it is better than an empty hub.
            val conversations = fromThreads.ifEmpty { readThreadsByScanning(context) }
            if (conversations.isEmpty()) return@withContext emptyList()

            val unread = unreadCounts(context)
            val drafts = drafts(context)
            conversations.map { conversation ->
                conversation.copy(
                    unreadCount = unread[conversation.threadId] ?: 0,
                    isRead = conversation.isRead && (unread[conversation.threadId] ?: 0) == 0,
                    draft = drafts[conversation.threadId].orEmpty()
                )
            }.sortedByDescending { it.timestamp }
        }

    private fun readThreads(context: Context): List<SmsConversationModel> {
        val addresses = canonicalAddresses(context)
        val conversations = mutableListOf<SmsConversationModel>()
        context.contentResolver.query(THREADS_URI, null, null, null, "date DESC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex("_id")
            val dateIdx = cursor.getColumnIndex("date")
            val snippetIdx = cursor.getColumnIndex("snippet")
            val readIdx = cursor.getColumnIndex("read")
            val countIdx = cursor.getColumnIndex("message_count")
            val recipientIdx = cursor.getColumnIndex("recipient_ids")

            while (cursor.moveToNext()) {
                val threadId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                val messageCount = if (countIdx >= 0) cursor.getInt(countIdx) else 0
                // The phone keeps emptied threads around; they are not conversations any more.
                if (messageCount == 0) continue

                val recipientIds = if (recipientIdx >= 0) {
                    cursor.getString(recipientIdx).orEmpty().split(' ').filter { it.isNotBlank() }
                } else {
                    emptyList()
                }
                val numbers = recipientIds.mapNotNull { addresses[it] }
                val address = numbers.firstOrNull().orEmpty()
                val person = lookUpContact(context, address)

                conversations += SmsConversationModel(
                    threadId = threadId,
                    address = address,
                    contactName = person?.name.orEmpty().ifBlank { address },
                    snippet = if (snippetIdx >= 0) cursor.getString(snippetIdx).orEmpty() else "",
                    timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                    isRead = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true,
                    photoUri = person?.photoUri,
                    messageCount = messageCount,
                    recipients = numbers,
                    contactId = person?.id
                )
            }
        }
        return conversations
    }

    /** The fallback: one row per thread, taken from the newest message in each. */
    private fun readThreadsByScanning(context: Context): List<SmsConversationModel> {
        val conversations = mutableListOf<SmsConversationModel>()
        val seen = mutableSetOf<Long>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    if (!seen.add(threadId)) continue
                    val address = cursor.getString(1).orEmpty()
                    val person = lookUpContact(context, address)
                    conversations += SmsConversationModel(
                        threadId = threadId,
                        address = address,
                        contactName = person?.name.orEmpty().ifBlank { address },
                        snippet = cursor.getString(2).orEmpty(),
                        timestamp = cursor.getLong(3),
                        isRead = cursor.getInt(4) == 1,
                        photoUri = person?.photoUri,
                        recipients = listOfNotNull(address.takeIf { it.isNotBlank() }),
                        contactId = person?.id
                    )
                }
            }
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the messages would not open", e)
            throw e
        }
        return conversations
    }

    /** Thread number to number, as the provider files them. */
    private fun canonicalAddresses(context: Context): Map<String, String> {
        val addresses = mutableMapOf<String, String>()
        try {
            context.contentResolver.query(
                CANONICAL_ADDRESSES_URI,
                arrayOf("_id", "address"),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    addresses[cursor.getString(0)] = cursor.getString(1).orEmpty()
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the address table would not open", e)
        }
        return addresses
    }

    /** How many unread messages each conversation holds. Unread rows are few, so this is cheap. */
    private fun unreadCounts(context: Context): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    counts[threadId] = (counts[threadId] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the unread messages would not count", e)
        }
        return counts
    }

    /** Text typed and left unsent, by conversation. */
    private fun drafts(context: Context): Map<Long, String> {
        val drafts = mutableMapOf<Long, String>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Draft.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    val body = cursor.getString(1).orEmpty()
                    if (body.isNotBlank() && threadId !in drafts) drafts[threadId] = body
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the drafts would not open", e)
        }
        return drafts
    }

    suspend fun getMessagesForThread(context: Context, threadId: Long): List<SmsMessageModel> =
        withContext(Dispatchers.IO) {
            val messages = mutableListOf<SmsMessageModel>()
            try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                    "${Telephony.Sms.DATE} ASC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                    val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                    val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)
                    val subIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                    while (cursor.moveToNext()) {
                        val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else Telephony.Sms.STATUS_NONE
                        messages += SmsMessageModel(
                            id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                            threadId = threadId,
                            address = if (addressIdx >= 0) cursor.getString(addressIdx).orEmpty() else "",
                            body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else "",
                            timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                            isOutgoing = isOutgoing(type),
                            isRead = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true,
                            type = type,
                            delivery = deliveryOf(type, status),
                            subscriptionId = if (subIdx >= 0) cursor.getInt(subIdx) else -1
                        )
                    }
                }
                messages += readMmsForThread(context, threadId)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the conversation would not open", e)
                throw e
            }
            messages.sortedBy { it.timestamp }
        }

    /**
     * The text of a thread's picture messages.
     *
     * The launcher does not show the pictures themselves — it says one came, and leaves opening it
     * to an app built for it — but a picture message with a caption is still a message, and a
     * conversation that hid them would be a conversation with holes in it.
     */
    private fun readMmsForThread(context: Context, threadId: Long): List<SmsMessageModel> {
        val messages = mutableListOf<SmsMessageModel>()
        try {
            context.contentResolver.query(
                MMS_URI,
                arrayOf("_id", "date", "msg_box", "read", "sub_id"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(0)
                    // Picture messages are dated in seconds where text messages are dated in
                    // thousandths; mixing the two puts every picture in 1970.
                    val date = cursor.getLong(1) * 1000L
                    val box = cursor.getInt(2)
                    val parts = mmsParts(context, mmsId)
                    messages += SmsMessageModel(
                        id = MMS_ID_OFFSET + mmsId,
                        threadId = threadId,
                        address = "",
                        body = parts.text,
                        timestamp = date,
                        isOutgoing = box == MMS_BOX_SENT || box == MMS_BOX_OUTBOX,
                        isRead = cursor.getInt(3) == 1,
                        type = if (box == MMS_BOX_SENT) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX,
                        delivery = MessageDelivery.NONE,
                        isMms = true,
                        hasAttachment = parts.hasAttachment,
                        subscriptionId = cursor.getInt(4)
                    )
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the picture messages would not open", e)
        }
        return messages
    }

    private data class MmsParts(val text: String, val hasAttachment: Boolean)

    private fun mmsParts(context: Context, mmsId: Long): MmsParts {
        val text = StringBuilder()
        var hasAttachment = false
        try {
            context.contentResolver.query(
                MMS_PART_URI,
                arrayOf("_id", "ct", "text"),
                "mid = ?",
                arrayOf(mmsId.toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    when (val contentType = cursor.getString(1).orEmpty()) {
                        "text/plain" -> cursor.getString(2)?.let {
                            if (text.isNotEmpty()) text.append('\n')
                            text.append(it)
                        }
                        "application/smil" -> Unit // Layout, not content.
                        else -> if (contentType.isNotBlank()) hasAttachment = true
                    }
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "a picture message would not open", e)
        }
        return MmsParts(text.toString(), hasAttachment)
    }

    /**
     * Every message whose text contains [query], newest first, across all conversations.
     * This is what makes the search box find a message rather than only a conversation's last line.
     */
    suspend fun searchMessages(context: Context, query: String, limit: Int = SEARCH_LIMIT): List<SmsMessageModel> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val messages = mutableListOf<SmsMessageModel>()
            try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.THREAD_ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.READ
                    ),
                    "${Telephony.Sms.BODY} LIKE ?",
                    arrayOf("%${query.replace("%", "\\%")}%"),
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext() && messages.size < limit) {
                        val type = cursor.getInt(5)
                        messages += SmsMessageModel(
                            id = cursor.getLong(0),
                            threadId = cursor.getLong(1),
                            address = cursor.getString(2).orEmpty(),
                            body = cursor.getString(3).orEmpty(),
                            timestamp = cursor.getLong(4),
                            isOutgoing = isOutgoing(type),
                            isRead = cursor.getInt(6) == 1,
                            type = type
                        )
                    }
                }
            } catch (e: Exception) {
                ZuneLog.w(TAG, "the search would not run", e)
            }
            messages
        }

    /**
     * The thread the phone keeps for [numbers], starting one if there is none.
     *
     * Starting one is how Android wants it done — a message has to belong to a thread before it is
     * sent — and an empty thread never reaches the list, which shows only conversations that
     * actually hold messages.
     */
    suspend fun getOrCreateThreadId(context: Context, numbers: Set<String>): Long? =
        withContext(Dispatchers.IO) {
            if (numbers.isEmpty()) return@withContext null
            try {
                Telephony.Threads.getOrCreateThreadId(context, numbers).takeIf { it > 0 }
            } catch (e: Exception) {
                ZuneLog.w(TAG, "no thread for these numbers", e)
                null
            }
        }

    // ════════════════════════════════════════════════════════════
    // WRITING (only as the phone's messaging app)
    // ════════════════════════════════════════════════════════════

    /**
     * Marks everything in a conversation read, and returns whether the store took it.
     *
     * "seen" as well as "read": the two are different flags and leaving "seen" alone is what makes
     * a notification reappear for a message already read.
     */
    suspend fun markThreadRead(context: Context, threadId: Long): Boolean = withContext(Dispatchers.IO) {
        if (threadId <= 0L || !isDefaultSmsApp(context)) return@withContext false
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            val changed = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
            runCatching {
                context.contentResolver.update(
                    MMS_URI,
                    ContentValues().apply { put("read", 1); put("seen", 1) },
                    "thread_id = ? AND read = 0",
                    arrayOf(threadId.toString())
                )
            }
            changed >= 0
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the conversation would not be marked read", e)
            false
        }
    }

    /** Deletes a whole conversation. */
    suspend fun deleteThread(context: Context, threadId: Long): Boolean = withContext(Dispatchers.IO) {
        if (threadId <= 0L || !isDefaultSmsApp(context)) return@withContext false
        try {
            val uri = ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the conversation would not be deleted", e)
            false
        }
    }

    /** Deletes one message. Picture messages carry an offset id, which is undone here. */
    suspend fun deleteMessage(context: Context, message: SmsMessageModel): Boolean =
        withContext(Dispatchers.IO) {
            if (!isDefaultSmsApp(context)) return@withContext false
            try {
                val uri = if (message.isMms) {
                    ContentUris.withAppendedId(MMS_URI, message.id - MMS_ID_OFFSET)
                } else {
                    ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.id)
                }
                context.contentResolver.delete(uri, null, null) > 0
            } catch (e: Exception) {
                ZuneLog.w(TAG, "the message would not be deleted", e)
                false
            }
        }

    /**
     * Keeps unsent text under a conversation, so leaving the hub mid-sentence does not lose it.
     * A blank draft deletes whatever was there instead of filing an empty one.
     */
    suspend fun saveDraft(context: Context, threadId: Long, address: String, body: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isDefaultSmsApp(context)) return@withContext false
            try {
                clearDraftsOf(context, threadId)
                if (body.isBlank()) return@withContext true
                context.contentResolver.insert(
                    Telephony.Sms.Draft.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 1)
                        if (threadId > 0L) put(Telephony.Sms.THREAD_ID, threadId)
                    }
                ) != null
            } catch (e: Exception) {
                ZuneLog.w(TAG, "the draft would not be kept", e)
                false
            }
        }

    /**
     * Removes a conversation's old draft, one row at a time.
     *
     * The message store will not take a delete addressed to the whole table or to the drafts
     * folder — it answers "unknown URL" to both — so each draft is found first and then deleted by
     * its own address, which is the one form the provider does accept.
     */
    private fun clearDraftsOf(context: Context, threadId: Long) {
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            Telephony.Sms.Draft.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        ids.forEach { id ->
            runCatching {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                    null,
                    null
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // WHO A NUMBER BELONGS TO
    // ════════════════════════════════════════════════════════════

    data class ContactMatch(val id: String, val name: String, val photoUri: String?)

    /**
     * Whose number this is, if the address book knows.
     *
     * The answer is kept: a conversation list of thirty threads would otherwise ask the address
     * book thirty times for the same handful of people, on every refresh.
     */
    private val contactCache = mutableMapOf<String, ContactMatch?>()

    fun clearContactCache() {
        synchronized(contactCache) { contactCache.clear() }
    }

    /** Whose number this is, for anything outside the hub that needs a name rather than digits. */
    fun contactNameFor(context: Context, number: String): String =
        lookUpContact(context, number)?.name.orEmpty().ifBlank { number }

    private fun lookUpContact(context: Context, number: String): ContactMatch? {
        if (number.isBlank()) return null
        val key = PhoneNumbers.matchKey(number)
        synchronized(contactCache) {
            if (contactCache.containsKey(key)) return contactCache[key]
        }
        val match = try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContactMatch(
                        id = cursor.getString(0).orEmpty(),
                        name = cursor.getString(1).orEmpty(),
                        photoUri = cursor.getString(2)
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
        synchronized(contactCache) { contactCache[key] = match }
        return match
    }

    // ════════════════════════════════════════════════════════════
    // SMALL TRANSLATIONS
    // ════════════════════════════════════════════════════════════

    private fun isOutgoing(type: Int): Boolean =
        type == Telephony.Sms.MESSAGE_TYPE_SENT ||
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
            type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
            type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
            type == Telephony.Sms.MESSAGE_TYPE_DRAFT

    /** What the store's box and delivery status together say about a message we sent. */
    internal fun deliveryOf(type: Int, status: Int): MessageDelivery = when {
        type == Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageDelivery.FAILED
        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED ->
            MessageDelivery.SENDING
        type != Telephony.Sms.MESSAGE_TYPE_SENT -> MessageDelivery.NONE
        status == Telephony.Sms.STATUS_COMPLETE -> MessageDelivery.DELIVERED
        status == Telephony.Sms.STATUS_FAILED -> MessageDelivery.FAILED
        status == Telephony.Sms.STATUS_PENDING -> MessageDelivery.SENDING
        else -> MessageDelivery.SENT
    }

    /**
     * Text and picture messages are numbered separately, and both start at one. Shifting picture
     * messages up keeps a conversation's ids unique when the two are shown as one list.
     */
    internal const val MMS_ID_OFFSET = 1_000_000_000L

    private const val MMS_BOX_SENT = 2
    private const val MMS_BOX_OUTBOX = 4
    private const val SEARCH_LIMIT = 200
}
