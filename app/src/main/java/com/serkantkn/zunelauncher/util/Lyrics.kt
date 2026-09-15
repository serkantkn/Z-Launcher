package com.serkantkn.zunelauncher.util

import java.util.Locale

/**
 * Words to a song, and when each of them is sung.
 *
 * Two shapes come back from everywhere lyrics live: a plain block of text, and the LRC format —
 * the same text with a timestamp in front of every line. LRC is what makes lyrics scroll in time
 * with the music, so it is what this is built around; a plain block is simply LRC with no times.
 *
 * All of it is string work, so none of it needs Android and all of it can be tested.
 */

/** One line, and the moment it is sung. [timeMillis] is -1 when the line has no time of its own. */
data class LyricLine(val timeMillis: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    /** Where it came from, for the line under the words. */
    val source: LyricsSource = LyricsSource.NONE
) {
    /** Whether the words follow the music, or are just there to read. */
    val isSynced: Boolean get() = lines.any { it.timeMillis >= 0L }

    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }

    val plainText: String get() = lines.joinToString("\n") { it.text }
}

enum class LyricsSource { NONE, SIDECAR_FILE, EMBEDDED_TAG, ONLINE }

/**
 * Reads LRC.
 *
 * The format is loose and everybody writes it slightly differently, so this is forgiving: a line
 * can carry several timestamps (a chorus written once and pointed at from several places), the
 * hundredths can be two digits or three, the separator can be a dot or a colon, and anything that
 * is not a timestamp at the start of a line is treated as a plain line of words rather than thrown
 * away. Metadata tags — `[ar:…]`, `[ti:…]`, `[by:…]` — are dropped; `[offset:…]` is obeyed, since
 * a file that says its times are 300 ms early means it.
 */
fun parseLrc(text: String, source: LyricsSource = LyricsSource.NONE): Lyrics {
    if (text.isBlank()) return Lyrics(emptyList(), source)

    var offsetMillis = 0L
    val lines = mutableListOf<LyricLine>()

    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach

        val stamps = mutableListOf<Long>()
        var rest = line

        // Timestamps only count at the very start, one after another.
        while (true) {
            val match = TIMESTAMP.find(rest) ?: break
            if (match.range.first != 0) break
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3]
            val fractionMillis = when (fraction.length) {
                0 -> 0L
                1 -> (fraction.toLongOrNull() ?: 0L) * 100L
                2 -> (fraction.toLongOrNull() ?: 0L) * 10L
                else -> fraction.take(3).toLongOrNull() ?: 0L
            }
            stamps += minutes * 60_000L + seconds * 1_000L + fractionMillis
            rest = rest.substring(match.range.last + 1)
        }

        if (stamps.isEmpty()) {
            val tag = METADATA.matchEntire(line)
            if (tag != null) {
                if (tag.groupValues[1].lowercase(Locale.ROOT) == "offset") {
                    offsetMillis = tag.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                return@forEach
            }
            lines += LyricLine(-1L, line)
            return@forEach
        }

        val words = rest.trim()
        stamps.forEach { at -> lines += LyricLine((at - offsetMillis).coerceAtLeast(0L), words) }
    }

    // A chorus pointed at from several places arrives out of order; the words are read in time.
    val ordered = if (lines.any { it.timeMillis >= 0L }) {
        lines.sortedBy { if (it.timeMillis < 0L) Long.MAX_VALUE else it.timeMillis }
    } else {
        lines
    }
    return Lyrics(ordered, source)
}

/**
 * Which line is being sung at [positionMillis], or -1 before the first one.
 *
 * The line that is up is the last one whose time has passed, not the nearest one: a long
 * instrumental break should leave the previous line standing rather than jumping ahead to words
 * nobody is singing yet.
 */
fun currentLyricIndex(lines: List<LyricLine>, positionMillis: Long): Int {
    var found = -1
    lines.forEachIndexed { index, line ->
        if (line.timeMillis in 0..positionMillis) found = index
    }
    return found
}

private val TIMESTAMP = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val METADATA = Regex("""^\[([a-zA-Z]+):(.*)]$""")
