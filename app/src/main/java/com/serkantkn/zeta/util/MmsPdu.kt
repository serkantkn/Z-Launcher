package com.serkantkn.zeta.util

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Picture messages as the carrier's server speaks them: the binary "PDUs" of OMA MMS 1.2,
 * written with WSP's encoding - a byte naming each header with its top bit set, then a value
 * whose shape the header decides.
 *
 * Android sends and fetches the bytes, but writing and reading them is the messaging app's job;
 * the platform's own codec is hidden from apps. This is the part of it the launcher needs: the
 * message it sends (send-req), the receipts it answers with (notifyresp-ind, read-rec-ind), and
 * the ones it reads - a fetched message (retrieve-conf), the server's answer to a send
 * (send-conf), and the reports that a message arrived or was read (delivery-ind, read-orig-ind).
 */
object MmsPdu {

    // ── Message types (X-Mms-Message-Type) ──
    const val TYPE_SEND_REQ = 0x80
    const val TYPE_SEND_CONF = 0x81
    const val TYPE_NOTIFICATION_IND = 0x82
    const val TYPE_NOTIFYRESP_IND = 0x83
    const val TYPE_RETRIEVE_CONF = 0x84
    const val TYPE_DELIVERY_IND = 0x86
    const val TYPE_READ_REC_IND = 0x87
    const val TYPE_READ_ORIG_IND = 0x88

    // ── Header fields ──
    private const val BCC = 0x81
    private const val CC = 0x82
    private const val CONTENT_TYPE = 0x84
    private const val DATE = 0x85
    private const val DELIVERY_REPORT = 0x86
    private const val FROM = 0x89
    private const val MESSAGE_CLASS = 0x8A
    private const val MESSAGE_ID = 0x8B
    private const val MESSAGE_TYPE = 0x8C
    private const val MMS_VERSION = 0x8D
    private const val PRIORITY = 0x8F
    private const val READ_REPORT = 0x90
    private const val REPORT_ALLOWED = 0x91
    private const val RESPONSE_STATUS = 0x92
    private const val STATUS = 0x95
    private const val SUBJECT = 0x96
    private const val TO = 0x97
    private const val TRANSACTION_ID = 0x98
    private const val READ_STATUS = 0x9B

    // ── Values ──
    const val YES = 0x80
    const val NO = 0x81
    private const val VERSION_1_2 = 0x92
    private const val CLASS_PERSONAL = 0x80
    private const val PRIORITY_NORMAL = 0x81
    private const val ADDRESS_PRESENT = 0x80
    private const val INSERT_ADDRESS = 0x81
    private const val CHARSET_UTF8 = 106

    /** X-Mms-Status: what became of a message sent - delivered, or why not. */
    const val STATUS_EXPIRED = 0x80
    const val STATUS_RETRIEVED = 0x81
    const val STATUS_REJECTED = 0x82
    const val STATUS_DEFERRED = 0x83
    const val STATUS_UNREACHABLE = 0x84

    /** X-Mms-Read-Status. */
    const val READ_STATUS_READ = 0x80
    const val READ_STATUS_DELETED = 0x81

    /** X-Mms-Response-Status: the server took the message. */
    const val RESPONSE_OK = 0x80

    // ── Content-Type parameters (WSP well-known parameter codes, version 1.1+) ──
    private const val PARAM_CHARSET = 0x81
    private const val PARAM_TYPE = 0x89
    private const val PARAM_START = 0x8A
    private const val PARAM_NAME = 0x85
    private const val PARAM_NAME_14 = 0x97

    // ── Part headers ──
    private const val PART_CONTENT_LOCATION = 0x8E
    private const val PART_CONTENT_ID = 0xC0

    /** The WSP numbers of the content types this deals in; anything else is written out in words. */
    private val WELL_KNOWN = mapOf(
        0x03 to "text/plain",
        0x1D to "image/gif",
        0x1E to "image/jpeg",
        0x20 to "image/png",
        0x21 to "image/vnd.wap.wbmp",
        0x23 to "application/vnd.wap.multipart.mixed",
        0x33 to "application/vnd.wap.multipart.related"
    )
    const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val SMIL = "application/smil"

    // ════════════════════════════════════════════════════════════
    // WHAT GOES IN AND COMES OUT
    // ════════════════════════════════════════════════════════════

    /** One piece of a message: a text, a picture, a clip, the SMIL that lays them out. */
    class Part(
        val contentType: String,
        val data: ByteArray,
        /** The name it is filed under in the message, and what the SMIL refers to it by. */
        val location: String,
        val contentId: String = location,
        val charset: Int = 0,
        val name: String = location
    ) {
        /** The text of a text part, in its own character set. */
        fun text(): String = String(data, charsetOf(charset))
    }

    /** A message being sent. */
    class SendRequest(
        val transactionId: String,
        val to: List<String>,
        val subject: String,
        val parts: List<Part>,
        val deliveryReport: Boolean,
        val readReport: Boolean,
        val dateSeconds: Long
    )

    /** Whatever was read out of a PDU; which fields are filled depends on its [type]. */
    class Parsed(
        val type: Int,
        val transactionId: String = "",
        val messageId: String = "",
        val from: String = "",
        val to: List<String> = emptyList(),
        val cc: List<String> = emptyList(),
        val subject: String = "",
        val dateSeconds: Long = 0L,
        val status: Int = 0,
        val readStatus: Int = 0,
        val responseStatus: Int = 0,
        val readReportWanted: Boolean = false,
        val contentType: String = "",
        val parts: List<Part> = emptyList()
    )

    // ════════════════════════════════════════════════════════════
    // WRITING
    // ════════════════════════════════════════════════════════════

    /** M-Send.req: the message, to every address in [SendRequest.to]. */
    fun encodeSendRequest(request: SendRequest): ByteArray {
        val out = Writer()
        out.header(MESSAGE_TYPE); out.byte(TYPE_SEND_REQ)
        out.header(TRANSACTION_ID); out.text(request.transactionId)
        out.header(MMS_VERSION); out.byte(VERSION_1_2)
        out.header(DATE); out.longInteger(request.dateSeconds)
        // "Insert address": the server writes in the sender's own number.
        out.header(FROM); out.valueLength(1); out.byte(INSERT_ADDRESS)
        request.to.forEach { address ->
            out.header(TO); out.text(addressForServer(address))
        }
        if (request.subject.isNotBlank()) {
            out.header(SUBJECT); out.encodedString(request.subject)
        }
        out.header(MESSAGE_CLASS); out.byte(CLASS_PERSONAL)
        out.header(PRIORITY); out.byte(PRIORITY_NORMAL)
        out.header(DELIVERY_REPORT); out.byte(if (request.deliveryReport) YES else NO)
        out.header(READ_REPORT); out.byte(if (request.readReport) YES else NO)
        // The content type comes last; the body follows it.
        val smil = request.parts.firstOrNull { it.contentType == SMIL }
        out.header(CONTENT_TYPE)
        out.contentType(
            MULTIPART_RELATED,
            buildList {
                if (smil != null) {
                    add(PARAM_START to "<${smil.contentId}>")
                    add(PARAM_TYPE to SMIL)
                }
            }
        )
        out.multipart(request.parts)
        return out.bytes()
    }

    /** M-NotifyResp.ind: the message announced as [transactionId] has been fetched (or put off). */
    fun encodeNotifyResponse(transactionId: String, status: Int = STATUS_RETRIEVED, reportAllowed: Boolean = true): ByteArray {
        val out = Writer()
        out.header(MESSAGE_TYPE); out.byte(TYPE_NOTIFYRESP_IND)
        out.header(TRANSACTION_ID); out.text(transactionId)
        out.header(MMS_VERSION); out.byte(VERSION_1_2)
        out.header(STATUS); out.byte(status)
        out.header(REPORT_ALLOWED); out.byte(if (reportAllowed) YES else NO)
        return out.bytes()
    }

    /** M-Read-Rec.ind: tells [to], who asked, that their message [messageId] has been read. */
    fun encodeReadReport(messageId: String, to: String, dateSeconds: Long): ByteArray {
        val out = Writer()
        out.header(MESSAGE_TYPE); out.byte(TYPE_READ_REC_IND)
        out.header(MMS_VERSION); out.byte(VERSION_1_2)
        out.header(MESSAGE_ID); out.text(messageId)
        out.header(TO); out.text(addressForServer(to))
        out.header(FROM); out.valueLength(1); out.byte(INSERT_ADDRESS)
        out.header(DATE); out.longInteger(dateSeconds)
        out.header(READ_STATUS); out.byte(READ_STATUS_READ)
        return out.bytes()
    }

    /** A phone number as an MMS server wants it: "+905551234567/TYPE=PLMN"; an e-mail address as it is. */
    fun addressForServer(address: String): String {
        val trimmed = address.trim()
        if (trimmed.contains('@') || trimmed.contains("/TYPE=")) return trimmed
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        return "$digits/TYPE=PLMN"
    }

    /** "+905551234567/TYPE=PLMN" back to "+905551234567". */
    fun addressFromServer(address: String): String = address.substringBefore("/TYPE=").trim()

    // ════════════════════════════════════════════════════════════
    // READING
    // ════════════════════════════════════════════════════════════

    /** Reads the headers of any PDU, and the parts of one that carries them; null if it is not one. */
    fun parse(data: ByteArray?): Parsed? {
        if (data == null || data.isEmpty()) return null
        val reader = Reader(data)
        var type = -1
        var transactionId = ""
        var messageId = ""
        var from = ""
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject = ""
        var date = 0L
        var status = 0
        var readStatus = 0
        var responseStatus = 0
        var readReport = false
        var contentType = ""
        var parts: List<Part> = emptyList()
        return try {
            while (reader.hasMore()) {
                val field = reader.byte()
                if (field and 0x80 == 0) {
                    // An application header ("X-Something: value") written out in words.
                    reader.back(); reader.text(); reader.text(); continue
                }
                when (field) {
                    MESSAGE_TYPE -> type = reader.byte()
                    TRANSACTION_ID -> transactionId = reader.text()
                    MESSAGE_ID -> messageId = reader.text()
                    FROM -> from = reader.fromValue()
                    TO -> to += addressFromServer(reader.encodedString())
                    CC -> cc += addressFromServer(reader.encodedString())
                    SUBJECT -> subject = reader.encodedString()
                    DATE -> date = reader.longInteger()
                    STATUS -> status = reader.byte()
                    READ_STATUS -> readStatus = reader.byte()
                    RESPONSE_STATUS -> responseStatus = reader.byte()
                    READ_REPORT -> readReport = reader.byte() == YES
                    CONTENT_TYPE -> {
                        contentType = reader.contentType().first
                        // The body follows the content type, and nothing else does.
                        if (contentType.contains("multipart")) parts = reader.multipart()
                        break
                    }
                    else -> reader.skipValue()
                }
            }
            if (type < 0) null else Parsed(
                type = type,
                transactionId = transactionId,
                messageId = messageId,
                from = addressFromServer(from),
                to = to,
                cc = cc,
                subject = subject,
                dateSeconds = date,
                status = status,
                readStatus = readStatus,
                responseStatus = responseStatus,
                readReportWanted = readReport,
                contentType = contentType,
                parts = parts
            )
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    internal fun charsetOf(code: Int): Charset = when (code) {
        3 -> Charsets.US_ASCII
        4 -> Charsets.ISO_8859_1
        1000 -> Charsets.UTF_16BE
        1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    // ════════════════════════════════════════════════════════════
    // THE ENCODING
    // ════════════════════════════════════════════════════════════

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun bytes(): ByteArray = out.toByteArray()
        fun byte(value: Int) = out.write(value and 0xFF)
        fun raw(data: ByteArray) = out.write(data, 0, data.size)
        fun header(field: Int) = byte(field)

        /** A text string: its bytes and a zero, quoted when its first byte would read as a code. */
        fun text(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) byte(0x7F)
            raw(bytes); byte(0)
        }

        /** A text that may not be plain ASCII: its length, its character set, then the text. */
        fun encodedString(value: String) {
            if (value.all { it.code < 0x80 }) { text(value); return }
            val body = Writer().apply { shortInteger(CHARSET_UTF8); text(value) }.bytes()
            valueLength(body.size); raw(body)
        }

        fun shortInteger(value: Int) = byte(value or 0x80)

        /** A number as long as it needs to be: how many bytes, then the bytes, highest first. */
        fun longInteger(value: Long) {
            var v = value
            val digits = ArrayDeque<Int>()
            do { digits.addFirst((v and 0xFF).toInt()); v = v ushr 8 } while (v != 0L)
            byte(digits.size); digits.forEach { byte(it) }
        }

        fun integer(value: Long) = if (value in 0..127) shortInteger(value.toInt()) else longInteger(value)

        /** Seven bits to a byte, highest first, the top bit saying more follow. */
        fun uintvar(value: Long) {
            var v = value
            val groups = ArrayDeque<Int>()
            groups.addFirst((v and 0x7F).toInt()); v = v ushr 7
            while (v != 0L) { groups.addFirst(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }
            groups.forEach { byte(it) }
        }

        fun valueLength(length: Int) {
            if (length < 31) byte(length) else { byte(31); uintvar(length.toLong()) }
        }

        /** A content type and its parameters, in WSP's general form. */
        fun contentType(type: String, params: List<Pair<Int, String>>, charset: Int = 0) {
            val body = Writer().apply {
                val known = WELL_KNOWN.entries.firstOrNull { it.value == type }?.key
                if (known != null) shortInteger(known) else text(type)
                if (charset > 0) { byte(PARAM_CHARSET); integer(charset.toLong()) }
                params.forEach { (code, value) -> byte(code); text(value) }
            }.bytes()
            valueLength(body.size); raw(body)
        }

        fun multipart(parts: List<Part>) {
            uintvar(parts.size.toLong())
            parts.forEach { part ->
                val type = Writer().apply {
                    contentType(
                        part.contentType,
                        listOf(PARAM_NAME to part.name),
                        charset = if (part.contentType.startsWith("text/") || part.contentType == SMIL) CHARSET_UTF8 else 0
                    )
                }.bytes()
                val headers = Writer().apply {
                    byte(PART_CONTENT_LOCATION); text(part.location)
                    byte(PART_CONTENT_ID); byte(0x22); raw("<${part.contentId}>".toByteArray(Charsets.UTF_8)); byte(0)
                }.bytes()
                uintvar((type.size + headers.size).toLong())
                uintvar(part.data.size.toLong())
                raw(type); raw(headers); raw(part.data)
            }
        }
    }

    private class Reader(private val data: ByteArray) {
        private var at = 0
        fun hasMore() = at < data.size
        fun back() { at-- }
        fun byte(): Int = data[at++].toInt() and 0xFF
        fun peek(): Int = data[at].toInt() and 0xFF
        fun bytes(count: Int): ByteArray = data.copyOfRange(at, at + count).also { at += count }

        fun text(): String {
            if (peek() == 0x7F || peek() == 0x22) at++
            val start = at
            while (data[at].toInt() != 0) at++
            return String(data, start, at++ - start, Charsets.UTF_8)
        }

        fun uintvar(): Long {
            var value = 0L
            while (true) {
                val b = byte()
                value = (value shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return value
            }
        }

        /** The length a value announces, when it announces one; -1 when the value is not of that kind. */
        fun valueLength(): Int {
            val first = peek()
            return when {
                first < 31 -> byte()
                first == 31 -> { at++; uintvar().toInt() }
                else -> -1
            }
        }

        fun longInteger(): Long {
            val count = byte()
            var value = 0L
            repeat(count) { value = (value shl 8) or byte().toLong() }
            return value
        }

        fun integer(): Long = if (peek() >= 0x80) (byte() and 0x7F).toLong() else longInteger()

        fun encodedString(): String {
            val length = valueLength()
            if (length < 0) return text()
            val end = at + length
            val charset = integer().toInt()
            val bytes = data.copyOfRange(at, end).let { if (it.isNotEmpty() && (it[0].toInt() and 0xFF) == 0x7F) it.copyOfRange(1, it.size) else it }
            at = end
            val trimmed = if (bytes.isNotEmpty() && bytes.last().toInt() == 0) bytes.copyOf(bytes.size - 1) else bytes
            return String(trimmed, charsetOf(charset))
        }

        fun fromValue(): String {
            val length = valueLength()
            val end = at + length
            val token = byte()
            val address = if (token == ADDRESS_PRESENT && at < end) encodedString() else ""
            at = end
            return address
        }

        /** Steps over a value of any header, by the general rule of its first byte. */
        fun skipValue() {
            val first = peek()
            when {
                first < 31 -> at += byte()
                first == 31 -> { at++; at += uintvar().toInt() }
                first < 128 -> text()
                else -> at++
            }
        }

        /** A content type and its name, charset and so on. */
        fun contentType(): Triple<String, Int, String> {
            val first = peek()
            if (first >= 0x80) return Triple(WELL_KNOWN[byte() and 0x7F] ?: "application/octet-stream", 0, "")
            if (first >= 32) return Triple(text(), 0, "")
            val length = valueLength()
            val end = at + length
            val type = if (peek() >= 0x80) WELL_KNOWN[byte() and 0x7F] ?: "application/octet-stream" else text()
            var charset = 0
            var name = ""
            while (at < end) {
                when (val param = byte()) {
                    PARAM_CHARSET -> charset = integer().toInt()
                    PARAM_NAME, PARAM_NAME_14 -> name = text()
                    else -> {
                        if (param and 0x80 == 0) { back(); text() }
                        skipValue()
                    }
                }
            }
            at = end
            return Triple(type, charset, name)
        }

        fun multipart(): List<Part> {
            val count = uintvar().toInt()
            val parts = mutableListOf<Part>()
            repeat(count) {
                val headersLength = uintvar().toInt()
                val dataLength = uintvar().toInt()
                val headersEnd = at + headersLength
                val (type, charset, name) = contentType()
                var location = ""
                var contentId = ""
                while (at < headersEnd) {
                    when (val header = byte()) {
                        PART_CONTENT_LOCATION -> location = text()
                        PART_CONTENT_ID -> contentId = text().removePrefix("<").removeSuffix(">")
                        else -> {
                            if (header and 0x80 == 0) { back(); text(); text() } else skipValue()
                        }
                    }
                }
                at = headersEnd
                val body = bytes(dataLength)
                val label = location.ifBlank { name }.ifBlank { contentId }
                parts += Part(type, body, location = label, contentId = contentId.ifBlank { label }, charset = charset, name = name.ifBlank { label })
            }
            return parts
        }
    }
}
