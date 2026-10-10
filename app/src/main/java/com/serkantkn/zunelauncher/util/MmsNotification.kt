package com.serkantkn.zunelauncher.util

/**
 * The little a WAP push has to say about a picture message that is waiting on the carrier.
 *
 * What arrives over the air is not the picture: it is a *notification-ind*, a few header fields
 * saying who sent something, how big it is, where it can be fetched from and until when. The
 * picture itself is fetched later over the data connection, which this launcher does not do.
 * What it does do is keep these fields, so the message is not lost and a fuller messaging app
 * can still fetch it.
 */
data class MmsNotification(
    val transactionId: String,
    val contentLocation: String,
    val from: String,
    val subject: String,
    val messageSize: Long,
    /** Seconds since the epoch, or 0 when the push did not say. */
    val expirySeconds: Long,
    val messageClass: String
)

/**
 * Reads a notification-ind out of the bytes a WAP push carries.
 *
 * The encoding is WSP's: each field is one byte naming it (with the top bit set) followed by a
 * value whose shape depends on the field — a text string ending in a zero byte, a short integer
 * with its top bit set, or a long integer announced by its length. Fields are taken as they come
 * and anything unknown is skipped by its own length rule, so a carrier's extra header does not
 * throw the whole message away.
 */
object MmsNotificationParser {

    private const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
    private const val MESSAGE_CLASS_PERSONAL = 0x80
    private const val MESSAGE_CLASS_ADVERTISEMENT = 0x81
    private const val MESSAGE_CLASS_INFORMATIONAL = 0x82
    private const val MESSAGE_CLASS_AUTO = 0x83

    // The field codes, as PduHeaders names them.
    private const val CONTENT_LOCATION = 0x83
    private const val EXPIRY = 0x88
    private const val FROM = 0x89
    private const val MESSAGE_CLASS = 0x8A
    private const val MESSAGE_TYPE = 0x8C
    private const val MESSAGE_SIZE = 0x8E
    private const val SUBJECT = 0x96
    private const val TRANSACTION_ID = 0x98

    private const val EXPIRY_ABSOLUTE = 0x80
    private const val FROM_ADDRESS_PRESENT = 0x80

    fun parse(data: ByteArray?, nowMillis: Long = System.currentTimeMillis()): MmsNotification? {
        if (data == null || data.isEmpty()) return null
        val reader = Reader(data)
        var messageType = -1
        var transactionId = ""
        var contentLocation = ""
        var from = ""
        var subject = ""
        var size = 0L
        var expiry = 0L
        var messageClass = "personal"

        try {
            while (reader.hasMore()) {
                val field = reader.readByte()
                if (field and 0x80 == 0) {
                    // A text-string field name (an unknown application header): skip its value.
                    reader.skipToNul()
                    reader.readTextString()
                    continue
                }
                when (field) {
                    MESSAGE_TYPE -> messageType = reader.readByte()
                    TRANSACTION_ID -> transactionId = reader.readTextString()
                    CONTENT_LOCATION -> contentLocation = reader.readTextString()
                    SUBJECT -> subject = reader.readEncodedString()
                    FROM -> from = reader.readFrom()
                    MESSAGE_SIZE -> size = reader.readLongInteger()
                    EXPIRY -> expiry = reader.readExpiry(nowMillis)
                    MESSAGE_CLASS -> messageClass = reader.readMessageClass()
                    else -> reader.skipValue()
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            // A push cut short still names what it managed to; what follows decides if it is enough.
        }

        if (messageType != MESSAGE_TYPE_NOTIFICATION_IND) return null
        if (transactionId.isBlank() || contentLocation.isBlank()) return null
        return MmsNotification(
            transactionId = transactionId,
            contentLocation = contentLocation,
            from = from,
            subject = subject,
            messageSize = size,
            expirySeconds = expiry,
            messageClass = messageClass
        )
    }

    private class Reader(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        fun readByte(): Int = data[pos++].toInt() and 0xFF

        fun peek(): Int = data[pos].toInt() and 0xFF

        fun skipToNul() {
            while (readByte() != 0) Unit
        }

        /** Text-String: bytes up to a zero, with a leading 0x7F quote dropped. */
        fun readTextString(): String {
            if (peek() == 0x7F) pos++
            val start = pos
            while (readByte() != 0) Unit
            return String(data, start, pos - 1 - start, Charsets.UTF_8)
        }

        /** Short-integer (one byte, top bit set) or long-integer (length, then big-endian bytes). */
        fun readLongInteger(): Long {
            val first = peek()
            if (first and 0x80 != 0) {
                pos++
                return (first and 0x7F).toLong()
            }
            val length = readByte()
            var value = 0L
            repeat(length) { value = (value shl 8) or readByte().toLong() }
            return value
        }

        /** Value-length: a byte under 31, or 31 followed by a uintvar. */
        fun readValueLength(): Int {
            val first = readByte()
            if (first < 31) return first
            var value = 0
            while (true) {
                val b = readByte()
                value = (value shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) return value
            }
        }

        /** Encoded-string-value: a text string, or a value-length, a charset and then the text. */
        fun readEncodedString(): String {
            val first = peek()
            if (first >= 32 && first < 128 || first == 0) return readTextString()
            val length = readValueLength()
            val end = pos + length
            readLongInteger() // charset; the text is read as UTF-8 either way
            val text = if (pos < end) readTextString() else ""
            pos = end
            return text
        }

        /** From-value: a length, then either "address present" + the address, or "insert address". */
        fun readFrom(): String {
            val length = readValueLength()
            val end = pos + length
            val token = readByte()
            val address = if (token == FROM_ADDRESS_PRESENT && pos < end) readEncodedString() else ""
            pos = end
            // "+905551112233/TYPE=PLMN" is how carriers write a number; the type is not part of it.
            return address.substringBefore("/TYPE=")
        }

        /** Expiry-value: a length, then absolute (a date) or relative (seconds from now). */
        fun readExpiry(nowMillis: Long): Long {
            val length = readValueLength()
            val end = pos + length
            val token = readByte()
            val value = readLongInteger()
            pos = end
            return if (token == EXPIRY_ABSOLUTE) value else nowMillis / 1000L + value
        }

        fun readMessageClass(): String {
            val first = peek()
            return if (first and 0x80 != 0) {
                pos++
                when (first) {
                    MESSAGE_CLASS_PERSONAL -> "personal"
                    MESSAGE_CLASS_ADVERTISEMENT -> "advertisement"
                    MESSAGE_CLASS_INFORMATIONAL -> "informational"
                    MESSAGE_CLASS_AUTO -> "auto"
                    else -> "personal"
                }
            } else {
                readTextString()
            }
        }

        /** Skips a value whose field this parser does not know, by WSP's general rules. */
        fun skipValue() {
            val first = peek()
            when {
                first and 0x80 != 0 -> pos++ // short integer
                first <= 30 -> pos += readValueLength() // value-length, then that many bytes
                first == 31 -> pos += readValueLength()
                else -> readTextString()
            }
        }
    }
}
