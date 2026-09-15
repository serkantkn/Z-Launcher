package com.serkantkn.zunelauncher.util

import java.nio.charset.Charset
import java.util.Locale

/**
 * The words a music file carries inside itself.
 *
 * ID3v2 has two frames for lyrics: `USLT`, a plain block, and `SYLT`, the same words with a time
 * against each one. Android's own [android.media.MediaMetadataRetriever] reads a dozen tags and
 * neither of these, so a file that already holds its lyrics looks empty to every player that only
 * asks Android. Reading the frames is a few dozen lines and means the words are there with no
 * network at all.
 *
 * Only the head of the file is needed - tags sit at the front - so callers read a few hundred
 * kilobytes rather than the whole track.
 */
fun readId3Lyrics(header: ByteArray): Lyrics? {
    if (header.size < 10) return null
    // "ID3", then the version, then flags, then the size as four 7-bit bytes.
    if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
        return null
    }
    val major = header[3].toInt() and 0xFF
    if (major < 2 || major > 4) return null

    val tagSize = synchSafe(header, 6)
    val end = minOf(header.size, 10 + tagSize)

    // v2.2 used three-character frame names and three-byte sizes; v2.3 and v2.4 use four of each.
    val nameLength = if (major == 2) 3 else 4
    val headerLength = if (major == 2) 6 else 10

    var offset = 10
    var plain: String? = null
    var synced: String? = null

    while (offset + headerLength <= end) {
        val name = String(header, offset, nameLength, Charsets.ISO_8859_1)
        if (name.isBlank() || name[0].code == 0) break

        val size = when (major) {
            2 -> ((header[offset + 3].toInt() and 0xFF) shl 16) or
                ((header[offset + 4].toInt() and 0xFF) shl 8) or
                (header[offset + 5].toInt() and 0xFF)
            // 2.4 sizes are 7-bit safe; 2.3 sizes are plain big-endian.
            4 -> synchSafe(header, offset + 4)
            else -> ((header[offset + 4].toInt() and 0xFF) shl 24) or
                ((header[offset + 5].toInt() and 0xFF) shl 16) or
                ((header[offset + 6].toInt() and 0xFF) shl 8) or
                (header[offset + 7].toInt() and 0xFF)
        }
        if (size <= 0 || offset + headerLength + size > end) break

        val body = header.copyOfRange(offset + headerLength, offset + headerLength + size)
        when (name) {
            "USLT", "ULT" -> if (plain == null) plain = readUnsynchronisedLyrics(body)
            "SYLT", "SLT" -> if (synced == null) synced = readSyncedLyrics(body)
        }
        offset += headerLength + size
    }

    val lrc = synced ?: plain ?: return null
    if (lrc.isBlank()) return null
    return parseLrc(lrc, LyricsSource.EMBEDDED_TAG)
}

/** `USLT`: an encoding byte, a three-letter language, a short description, then the words. */
private fun readUnsynchronisedLyrics(body: ByteArray): String? {
    if (body.size < 5) return null
    val charset = charsetOf(body[0].toInt() and 0xFF)
    val wide = charset == Charsets.UTF_16 || charset == Charsets.UTF_16BE
    // The description runs up to a terminator, two bytes wide for the UTF-16 encodings.
    val index = skipTerminator(body, 4, wide) ?: return null
    if (index >= body.size) return null
    return String(body, index, body.size - index, charset).trim().ifBlank { null }
}

/**
 * `SYLT`: the same, then pairs of text and timestamp.
 *
 * Only millisecond timestamps are read. The other unit the format allows is MPEG frames, which
 * cannot be turned into a time without decoding the audio - a file written that way is treated as
 * having no usable times rather than being given wrong ones.
 */
private fun readSyncedLyrics(body: ByteArray): String? {
    if (body.size < 7) return null
    val charset = charsetOf(body[0].toInt() and 0xFF)
    val wide = charset == Charsets.UTF_16 || charset == Charsets.UTF_16BE
    val timeUnit = body[4].toInt() and 0xFF
    if (timeUnit != 2) return null // 1 = MPEG frames, 2 = milliseconds

    var index = skipTerminator(body, 6, wide) ?: return null

    val out = StringBuilder()
    while (index < body.size) {
        val textEnd = findTerminator(body, index, wide) ?: break
        val text = String(body, index, textEnd - index, charset)
        index = textEnd + if (wide) 2 else 1
        if (index + 4 > body.size) break
        val millis = ((body[index].toInt() and 0xFF).toLong() shl 24) or
            ((body[index + 1].toInt() and 0xFF).toLong() shl 16) or
            ((body[index + 2].toInt() and 0xFF).toLong() shl 8) or
            (body[index + 3].toInt() and 0xFF).toLong()
        index += 4

        val minutes = millis / 60_000L
        val seconds = (millis % 60_000L) / 1_000L
        val hundredths = (millis % 1_000L) / 10L
        out.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]", minutes, seconds, hundredths))
        out.append(text.trim())
        out.append("\n")
    }
    return out.toString().ifBlank { null }
}

private fun charsetOf(encoding: Int): Charset = when (encoding) {
    1 -> Charsets.UTF_16
    2 -> Charsets.UTF_16BE
    3 -> Charsets.UTF_8
    else -> Charsets.ISO_8859_1
}

private fun findTerminator(body: ByteArray, from: Int, wide: Boolean): Int? {
    var index = from
    while (index < body.size) {
        if (!wide) {
            if (body[index].toInt() == 0) return index
            index++
        } else {
            if (index + 1 >= body.size) return null
            if (body[index].toInt() == 0 && body[index + 1].toInt() == 0) return index
            index += 2
        }
    }
    return null
}

private fun skipTerminator(body: ByteArray, from: Int, wide: Boolean): Int? {
    val end = findTerminator(body, from, wide) ?: return null
    return end + if (wide) 2 else 1
}

/** Four bytes carrying seven bits each, so a size can never look like a frame marker. */
private fun synchSafe(bytes: ByteArray, at: Int): Int {
    if (at + 3 >= bytes.size) return 0
    return ((bytes[at].toInt() and 0x7F) shl 21) or
        ((bytes[at + 1].toInt() and 0x7F) shl 14) or
        ((bytes[at + 2].toInt() and 0x7F) shl 7) or
        (bytes[at + 3].toInt() and 0x7F)
}
