package com.serkantkn.zunelauncher.data.repository

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.serkantkn.zunelauncher.data.receiver.MmsResultReceiver
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.MmsPdu
import com.serkantkn.zunelauncher.util.PhoneNumbers
import com.serkantkn.zunelauncher.util.SmsSender
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Picture messages, sent and fetched.
 *
 * Android moves the bytes - it brings up the carrier's MMS connection and talks to its server -
 * but everything around that is the messaging app's: writing the message, filing it in the
 * phone's message store before and after, fetching one that has been announced and filing what
 * came, and keeping the reports that it arrived or was read. A message is filed first and sent
 * after, so it shows in the conversation as going while it goes.
 */
object MmsTransport {

    private const val TAG = "MmsTransport"

    /** The phone's message boxes, as the store numbers them. */
    private const val BOX_INBOX = 1
    private const val BOX_SENT = 2
    private const val BOX_OUTBOX = 4
    private const val BOX_FAILED = 5

    /** m_type: what kind of PDU a row of the store holds. */
    private const val M_TYPE_SEND_REQ = 128
    private const val M_TYPE_NOTIFICATION_IND = 130
    private const val M_TYPE_RETRIEVE_CONF = 132

    private const val ADDR_FROM = 137
    private const val ADDR_TO = 151
    private const val ADDR_CC = 130
    private const val CHARSET_UTF8 = 106

    /** What a carrier takes when it does not say: the usual 300 KB. */
    private const val DEFAULT_MAX_BYTES = 300 * 1024

    private val MMS_URI: Uri = Uri.parse("content://mms")

    private val _downloading = MutableStateFlow<Set<Long>>(emptySet())

    /** Store rows of announced messages being fetched right now. */
    val downloading: StateFlow<Set<Long>> = _downloading.asStateFlow()

    sealed interface SendResult {
        data object Queued : SendResult
        data object NoPermission : SendResult
        /** A clip or a picture could not be made to fit in what the carrier takes. */
        data object TooBig : SendResult
        data object Failed : SendResult
    }

    // ════════════════════════════════════════════════════════════
    // SENDING
    // ════════════════════════════════════════════════════════════

    /**
     * Sends [text] and [attachments] to [recipients] as one picture message, filed under
     * [threadId] (or the thread for those recipients).
     */
    suspend fun send(
        context: Context,
        recipients: List<String>,
        text: String,
        attachments: List<Uri>,
        threadId: Long,
        subscriptionId: Int
    ): SendResult = withContext(Dispatchers.IO) {
        if (!SmsSender.canSend(context)) return@withContext SendResult.NoPermission
        val numbers = recipients.map { PhoneNumbers.toDialable(it) }.filter { it.isNotBlank() }.distinct()
        if (numbers.isEmpty()) return@withContext SendResult.Failed
        val manager = SmsSender.smsManager(context, subscriptionId) ?: return@withContext SendResult.Failed
        val maxBytes = maxMessageBytes(manager)

        // The pictures shrink to fit what is left once the words are in; a clip cannot be shrunk.
        val textPart = text.trim().takeIf { it.isNotEmpty() }?.let {
            MmsPdu.Part("text/plain", it.toByteArray(Charsets.UTF_8), location = "text_0.txt", charset = CHARSET_UTF8)
        }
        val budget = (maxBytes - (textPart?.data?.size ?: 0) - 2048).coerceAtLeast(16 * 1024)
        val perAttachment = if (attachments.isEmpty()) budget else budget / attachments.size
        val media = attachments.mapIndexedNotNull { index, uri ->
            mediaPart(context, uri, index, perAttachment) ?: return@withContext SendResult.TooBig
        }
        val parts = buildList {
            add(MmsPdu.Part(MmsPdu.SMIL, smilFor(media, textPart).toByteArray(Charsets.UTF_8), location = "smil.xml", contentId = "smil", charset = CHARSET_UTF8))
            addAll(media)
            textPart?.let { add(it) }
        }
        val settings = context.appContainer.settingsDataStore
        val request = MmsPdu.SendRequest(
            transactionId = "T" + UUID.randomUUID().toString().replace("-", "").take(16),
            to = numbers,
            subject = "",
            parts = parts,
            deliveryReport = settings.smsDeliveryReports.first(),
            readReport = settings.mmsAskReadReports.first(),
            dateSeconds = System.currentTimeMillis() / 1000L
        )
        val pdu = MmsPdu.encodeSendRequest(request)
        if (pdu.size > maxBytes + 4096) return@withContext SendResult.TooBig

        val thread = if (threadId > 0L) threadId else runCatching {
            Telephony.Threads.getOrCreateThreadId(context, numbers.toSet())
        }.getOrDefault(0L)
        val row = file(context, request, thread, subscriptionId, BOX_OUTBOX, M_TYPE_SEND_REQ, from = "", to = numbers)
            ?: return@withContext SendResult.Failed
        MessagingBridge.messagesChanged()

        val file = pduFile(context, "send_$row.pdu").apply { writeBytes(pdu) }
        try {
            manager.sendMultimediaMessage(
                context,
                contentUriOf(context, file),
                null,
                null,
                resultIntent(context, MmsResultReceiver.ACTION_SENT, row, file)
            )
            SendResult.Queued
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the picture message would not go out", e)
            setBox(context, row, BOX_FAILED)
            file.delete()
            MessagingBridge.messagesChanged()
            SendResult.Failed
        }
    }

    /**
     * Sends a picture message that failed again, as it was: its parts and addresses are read
     * back out of the store, and the same row goes back to the outbox, so it does not appear twice.
     */
    suspend fun resend(context: Context, row: Long): SendResult = withContext(Dispatchers.IO) {
        if (!SmsSender.canSend(context)) return@withContext SendResult.NoPermission
        val resolver = context.contentResolver
        val parts = mutableListOf<MmsPdu.Part>()
        runCatching {
            resolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("_id", "ct", "cl", "cid", "name", "text", "chset"),
                "mid = ?", arrayOf(row.toString()), "seq ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(1).orEmpty()
                    val location = cursor.getString(2).orEmpty().ifBlank { "part_${cursor.position}" }
                    val id = cursor.getString(3).orEmpty().removePrefix("<").removeSuffix(">").ifBlank { location }
                    val data = if (type == "text/plain" || type == MmsPdu.SMIL) {
                        cursor.getString(5).orEmpty().toByteArray(Charsets.UTF_8)
                    } else {
                        resolver.openInputStream(Uri.parse("content://mms/part/${cursor.getLong(0)}"))?.use { it.readBytes() } ?: ByteArray(0)
                    }
                    parts += MmsPdu.Part(type, data, location = location, contentId = id, charset = if (type.startsWith("text/") || type == MmsPdu.SMIL) CHARSET_UTF8 else 0, name = cursor.getString(4).orEmpty().ifBlank { location })
                }
            }
        }
        val to = mutableListOf<String>()
        runCatching {
            resolver.query(Uri.parse("content://mms/$row/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS), "${Telephony.Mms.Addr.TYPE} = $ADDR_TO", null, null)
                ?.use { while (it.moveToNext()) to += it.getString(0).orEmpty() }
        }
        if (parts.none { it.contentType != MmsPdu.SMIL } || to.isEmpty()) return@withContext SendResult.Failed
        val sub = subscriptionOf(context, row)
        val manager = SmsSender.smsManager(context, sub) ?: return@withContext SendResult.Failed
        val settings = context.appContainer.settingsDataStore
        val request = MmsPdu.SendRequest(
            transactionId = "T" + UUID.randomUUID().toString().replace("-", "").take(16),
            to = to.filter { it.isNotBlank() },
            subject = "",
            parts = parts.sortedBy { if (it.contentType == MmsPdu.SMIL) 0 else 1 },
            deliveryReport = settings.smsDeliveryReports.first(),
            readReport = settings.mmsAskReadReports.first(),
            dateSeconds = System.currentTimeMillis() / 1000L
        )
        update(context, row, ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, BOX_OUTBOX)
            put(Telephony.Mms.TRANSACTION_ID, request.transactionId)
            put(Telephony.Mms.DATE, request.dateSeconds)
        })
        MessagingBridge.messagesChanged()
        val file = pduFile(context, "send_$row.pdu").apply { writeBytes(MmsPdu.encodeSendRequest(request)) }
        try {
            manager.sendMultimediaMessage(context, contentUriOf(context, file), null, null, resultIntent(context, MmsResultReceiver.ACTION_SENT, row, file))
            SendResult.Queued
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the picture message would not go out again", e)
            setBox(context, row, BOX_FAILED)
            file.delete()
            MessagingBridge.messagesChanged()
            SendResult.Failed
        }
    }

    /** The server's answer to a send: the message is sent - with the id its reports will carry - or it failed. */
    fun onSent(context: Context, row: Long, resultCode: Int, response: ByteArray?, file: File?) {
        file?.delete()
        val conf = MmsPdu.parse(response)
        val ok = resultCode == Activity.RESULT_OK && (conf == null || conf.responseStatus == 0 || conf.responseStatus == MmsPdu.RESPONSE_OK)
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, if (ok) BOX_SENT else BOX_FAILED)
            conf?.messageId?.takeIf { it.isNotBlank() }?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            if (conf != null && conf.responseStatus != 0) put(Telephony.Mms.RESPONSE_STATUS, conf.responseStatus)
        }
        update(context, row, values)
        if (!ok) ZuneLog.w(TAG, "the picture message was refused (code $resultCode, status ${conf?.responseStatus})")
        MessagingBridge.messagesChanged()
    }

    // ════════════════════════════════════════════════════════════
    // FETCHING
    // ════════════════════════════════════════════════════════════

    /** Fetches the announced message filed at [row]. */
    fun download(context: Context, row: Long, contentLocation: String, subscriptionId: Int) {
        if (contentLocation.isBlank() || row in _downloading.value) return
        val manager = SmsSender.smsManager(context, subscriptionId) ?: return
        val file = pduFile(context, "retrieve_$row.pdu").apply { delete(); createNewFile() }
        _downloading.update { it + row }
        MessagingBridge.messagesChanged()
        try {
            manager.downloadMultimediaMessage(
                context,
                contentLocation,
                contentUriOf(context, file),
                null,
                resultIntent(context, MmsResultReceiver.ACTION_DOWNLOADED, row, file)
            )
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the picture message could not be fetched", e)
            _downloading.update { it - row }
            file.delete()
            MessagingBridge.messagesChanged()
        }
    }

    /** Fetches the announced message at [row], reading where from out of the store. */
    suspend fun downloadRow(context: Context, row: Long) = withContext(Dispatchers.IO) {
        val (location, sub) = runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(MMS_URI, row),
                arrayOf(Telephony.Mms.CONTENT_LOCATION, Telephony.Mms.SUBSCRIPTION_ID),
                null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0).orEmpty() to it.getInt(1) else null }
        }.getOrNull() ?: return@withContext
        download(context, row, location, sub)
    }

    /**
     * What was fetched for [row]: the announcement in the store becomes the message itself, with
     * its parts; the server is told it was fetched; a read report it asked for is remembered.
     */
    suspend fun onDownloaded(context: Context, row: Long, resultCode: Int, file: File?) = withContext(Dispatchers.IO) {
        try {
            val conf = if (resultCode == Activity.RESULT_OK) MmsPdu.parse(file?.takeIf { it.exists() }?.readBytes()) else null
            if (conf == null || conf.type != MmsPdu.TYPE_RETRIEVE_CONF) {
                ZuneLog.w(TAG, "the picture message did not come (code $resultCode)")
                return@withContext
            }
            val transactionId = transactionIdOf(context, row)
            update(context, row, ContentValues().apply {
                put(Telephony.Mms.MESSAGE_TYPE, M_TYPE_RETRIEVE_CONF)
                put(Telephony.Mms.MESSAGE_BOX, BOX_INBOX)
                put(Telephony.Mms.CONTENT_TYPE, conf.contentType)
                if (conf.messageId.isNotBlank()) put(Telephony.Mms.MESSAGE_ID, conf.messageId)
                if (conf.subject.isNotBlank()) {
                    put(Telephony.Mms.SUBJECT, conf.subject)
                    put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
                }
                put(Telephony.Mms.READ_REPORT, if (conf.readReportWanted) MmsPdu.YES else MmsPdu.NO)
                putNull(Telephony.Mms.CONTENT_LOCATION)
            })
            writeParts(context, row, conf.parts)
            // The others in a group, so the conversation knows who else it went to.
            (conf.to + conf.cc).filter { it.isNotBlank() }.forEach { insertAddress(context, row, it, if (it in conf.cc) ADDR_CC else ADDR_TO) }
            if (transactionId.isNotBlank()) {
                val subscription = subscriptionOf(context, row)
                runCatching {
                    SmsSender.smsManager(context, subscription)?.let { sendQuietly(context, it, MmsPdu.encodeNotifyResponse(transactionId), "notifyresp_$row.pdu") }
                }
            }
            SmsRepository.clearContactCache()
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the fetched picture message could not be filed", e)
        } finally {
            file?.delete()
            _downloading.update { it - row }
            MessagingBridge.messagesChanged()
        }
    }

    // ════════════════════════════════════════════════════════════
    // REPORTS
    // ════════════════════════════════════════════════════════════

    /** A report from the server about a message sent: it arrived, or it was read. */
    fun onReport(context: Context, report: MmsPdu.Parsed) {
        if (report.messageId.isBlank()) return
        val values = ContentValues().apply {
            when (report.type) {
                MmsPdu.TYPE_DELIVERY_IND -> put(Telephony.Mms.STATUS, report.status)
                MmsPdu.TYPE_READ_ORIG_IND -> put(Telephony.Mms.READ_STATUS, report.readStatus)
                else -> return
            }
        }
        try {
            context.contentResolver.update(MMS_URI, values, "${Telephony.Mms.MESSAGE_ID} = ? AND ${Telephony.Mms.MESSAGE_BOX} = $BOX_SENT", arrayOf(report.messageId))
        } catch (e: Exception) {
            ZuneLog.w(TAG, "a report could not be filed", e)
        }
        MessagingBridge.messagesChanged()
    }

    /**
     * Tells the senders who asked that their picture messages in [threadId] have been read - for
     * the ones still unread, so a message is answered for once. Only while the user allows it.
     */
    suspend fun sendReadReports(context: Context, threadId: Long) = withContext(Dispatchers.IO) {
        if (!context.appContainer.settingsDataStore.mmsSendReadReports.first()) return@withContext
        val wanted = mutableListOf<Triple<Long, String, Int>>()
        runCatching {
            context.contentResolver.query(
                MMS_URI,
                arrayOf("_id", Telephony.Mms.MESSAGE_ID, Telephony.Mms.SUBSCRIPTION_ID),
                "thread_id = ? AND read = 0 AND ${Telephony.Mms.READ_REPORT} = ${MmsPdu.YES} AND ${Telephony.Mms.MESSAGE_TYPE} = $M_TYPE_RETRIEVE_CONF",
                arrayOf(threadId.toString()),
                null
            )?.use { while (it.moveToNext()) wanted += Triple(it.getLong(0), it.getString(1).orEmpty(), it.getInt(2)) }
        }
        wanted.filter { it.second.isNotBlank() }.forEach { (row, messageId, sub) ->
            val sender = senderOf(context, row).ifBlank { return@forEach }
            val manager = SmsSender.smsManager(context, sub) ?: return@forEach
            sendQuietly(context, manager, MmsPdu.encodeReadReport(messageId, sender, System.currentTimeMillis() / 1000L), "readrec_$row.pdu")
            // Answered: it does not ask again.
            update(context, row, ContentValues().apply { put(Telephony.Mms.READ_REPORT, MmsPdu.NO) })
        }
    }

    // ════════════════════════════════════════════════════════════
    // THE STORE
    // ════════════════════════════════════════════════════════════

    private fun file(
        context: Context,
        request: MmsPdu.SendRequest,
        threadId: Long,
        subscriptionId: Int,
        box: Int,
        type: Int,
        from: String,
        to: List<String>
    ): Long? = try {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.MESSAGE_TYPE, type)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.TRANSACTION_ID, request.transactionId)
            put(Telephony.Mms.CONTENT_TYPE, MmsPdu.MULTIPART_RELATED)
            put(Telephony.Mms.DATE, request.dateSeconds)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.DELIVERY_REPORT, if (request.deliveryReport) MmsPdu.YES else MmsPdu.NO)
            put(Telephony.Mms.READ_REPORT, if (request.readReport) MmsPdu.YES else MmsPdu.NO)
            if (threadId > 0L) put(Telephony.Mms.THREAD_ID, threadId)
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val uri = context.contentResolver.insert(MMS_URI, values)
        val row = uri?.let { ContentUris.parseId(it) }
        if (row != null) {
            writeParts(context, row, request.parts)
            if (from.isNotBlank()) insertAddress(context, row, from, ADDR_FROM)
            to.forEach { insertAddress(context, row, it, ADDR_TO) }
        }
        row
    } catch (e: Exception) {
        ZuneLog.e(TAG, "the picture message could not be filed", e)
        null
    }

    private fun writeParts(context: Context, row: Long, parts: List<MmsPdu.Part>) {
        val partsUri = Uri.parse("content://mms/$row/part")
        parts.forEachIndexed { index, part ->
            val isText = part.contentType == "text/plain" || part.contentType == MmsPdu.SMIL
            val values = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, row)
                put(Telephony.Mms.Part.SEQ, if (part.contentType == MmsPdu.SMIL) -1 else index)
                put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
                put(Telephony.Mms.Part.CONTENT_ID, "<${part.contentId}>")
                put(Telephony.Mms.Part.CONTENT_LOCATION, part.location)
                put(Telephony.Mms.Part.NAME, part.name)
                if (isText) {
                    put(Telephony.Mms.Part.CHARSET, CHARSET_UTF8)
                    put(Telephony.Mms.Part.TEXT, part.text())
                }
            }
            val partUri = runCatching { context.contentResolver.insert(partsUri, values) }.getOrNull() ?: return@forEachIndexed
            if (!isText) {
                runCatching {
                    context.contentResolver.openOutputStream(partUri)?.use { it.write(part.data) }
                }.onFailure { ZuneLog.w(TAG, "a part of a picture message could not be kept", it) }
            }
        }
    }

    private fun insertAddress(context: Context, row: Long, address: String, type: Int) {
        runCatching {
            context.contentResolver.insert(
                Uri.parse("content://mms/$row/addr"),
                ContentValues().apply {
                    put(Telephony.Mms.Addr.MSG_ID, row)
                    put(Telephony.Mms.Addr.ADDRESS, address)
                    put(Telephony.Mms.Addr.TYPE, type)
                    put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF8)
                }
            )
        }
    }

    private fun update(context: Context, row: Long, values: ContentValues) {
        try {
            context.contentResolver.update(ContentUris.withAppendedId(MMS_URI, row), values, null, null)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "a picture message's state could not be written", e)
        }
    }

    private fun setBox(context: Context, row: Long, box: Int) =
        update(context, row, ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) })

    private fun transactionIdOf(context: Context, row: Long): String = runCatching {
        context.contentResolver.query(ContentUris.withAppendedId(MMS_URI, row), arrayOf(Telephony.Mms.TRANSACTION_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
    }.getOrNull().orEmpty()

    private fun subscriptionOf(context: Context, row: Long): Int = runCatching {
        context.contentResolver.query(ContentUris.withAppendedId(MMS_URI, row), arrayOf(Telephony.Mms.SUBSCRIPTION_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getInt(0) else -1 }
    }.getOrNull() ?: -1

    private fun senderOf(context: Context, row: Long): String = runCatching {
        context.contentResolver.query(Uri.parse("content://mms/$row/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS), "${Telephony.Mms.Addr.TYPE} = $ADDR_FROM", null, null)
            ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
    }.getOrNull().orEmpty()

    // ════════════════════════════════════════════════════════════
    // MAKING THE MESSAGE
    // ════════════════════════════════════════════════════════════

    private fun maxMessageBytes(manager: SmsManager): Int = runCatching {
        manager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, DEFAULT_MAX_BYTES)
    }.getOrDefault(DEFAULT_MAX_BYTES).takeIf { it > 0 } ?: DEFAULT_MAX_BYTES

    /**
     * A picture made small enough for [budget] - a JPEG, shrunk and squeezed until it fits - or a
     * clip, a sound or anything else as it is, when it already fits; null when it will not.
     */
    private fun mediaPart(context: Context, uri: Uri, index: Int, budget: Int): MmsPdu.Part? {
        val type = context.contentResolver.getType(uri).orEmpty()
        if (type.startsWith("image/") && type != "image/gif") {
            val bitmap = decode(context, uri) ?: return null
            val bytes = squeeze(bitmap, budget) ?: return null
            return MmsPdu.Part("image/jpeg", bytes, location = "image_$index.jpg")
        }
        val data = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        if (data.size > budget) return null
        val extension = type.substringAfter('/', "bin").substringBefore(';')
        val kind = type.substringBefore('/').ifBlank { "file" }
        return MmsPdu.Part(type.ifBlank { "application/octet-stream" }, data, location = "${kind}_$index.$extension")
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                // Nothing a carrier takes needs more than this; decoding the whole photo first
                // would only fill the memory to throw it away.
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    /** A JPEG of [bitmap] under [budget] bytes: lower quality first, then smaller, as far as it takes. */
    internal fun squeeze(bitmap: Bitmap, budget: Int): ByteArray? {
        var current = bitmap
        repeat(6) {
            for (quality in intArrayOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= budget) return out.toByteArray()
            }
            if (current.width < 160 || current.height < 160) return null
            current = Bitmap.createScaledBitmap(current, current.width * 3 / 4, current.height * 3 / 4, true)
        }
        return null
    }

    private const val MAX_SIDE = 1280

    /** The layout that says which picture goes with which words; some phones show nothing without it. */
    internal fun smilFor(media: List<MmsPdu.Part>, text: MmsPdu.Part?): String = buildString {
        append("<smil><head><layout><root-layout/>")
        append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
        append("</layout></head><body>")
        media.forEach { part ->
            append("<par dur=\"5000ms\">")
            val tag = when {
                part.contentType.startsWith("image/") -> "img"
                part.contentType.startsWith("video/") -> "video"
                part.contentType.startsWith("audio/") -> "audio"
                else -> "ref"
            }
            append("<$tag src=\"${part.location}\" region=\"Image\"/>")
            append("</par>")
        }
        if (text != null) append("<par dur=\"5000ms\"><text src=\"${text.location}\" region=\"Text\"/></par>")
        append("</body></smil>")
    }

    // ════════════════════════════════════════════════════════════
    // HANDING BYTES TO THE PHONE
    // ════════════════════════════════════════════════════════════

    private fun pduFile(context: Context, name: String): File =
        File(File(context.cacheDir, "mms_pdu").apply { mkdirs() }, name)

    private fun contentUriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun resultIntent(context: Context, action: String, row: Long, file: File): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action + row).hashCode(),
            Intent(context, MmsResultReceiver::class.java)
                .setAction(action)
                .putExtra(MmsResultReceiver.EXTRA_ROW, row)
                .putExtra(MmsResultReceiver.EXTRA_FILE, file.absolutePath),
            // Mutable: the phone writes the server's answer into it.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    /** A receipt the server is owed: sent, and not filed or followed up. */
    private fun sendQuietly(context: Context, manager: SmsManager, pdu: ByteArray, name: String) {
        val file = pduFile(context, name).apply { writeBytes(pdu) }
        runCatching {
            manager.sendMultimediaMessage(
                context,
                contentUriOf(context, file),
                null,
                null,
                PendingIntent.getBroadcast(
                    context,
                    name.hashCode(),
                    Intent(context, MmsResultReceiver::class.java)
                        .setAction(MmsResultReceiver.ACTION_RECEIPT_SENT)
                        .putExtra(MmsResultReceiver.EXTRA_FILE, file.absolutePath),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
        }.onFailure { ZuneLog.w(TAG, "a receipt could not be sent", it); file.delete() }
    }
}
