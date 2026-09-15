package com.serkantkn.zunelauncher.ui.screens.messaging

import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The arithmetic behind the message list: when something happened, and how much of a text message
 * a piece of writing actually is.
 *
 * All of it is plain functions over numbers and text so the rules can be checked without a screen
 * and without a phone — the counting in particular is easy to get wrong in a way nobody notices
 * until a message silently costs three times what it should.
 */

/** What kind of stamp a conversation's time should be shown as. */
enum class StampKind {
    /** Today: the clock time. */
    TIME,

    /** Yesterday: the word, which only the screen knows in the right language. */
    YESTERDAY,

    /** Within the last week: the day's name. */
    WEEKDAY,

    /** Older: the date. */
    DATE
}

data class ConversationStamp(val kind: StampKind, val text: String)

/**
 * How a conversation's time reads in the list.
 *
 * Windows Phone never wrote out a full date for something that happened an hour ago, and neither
 * does this: the further back a message is, the coarser the stamp.
 */
fun conversationStamp(now: Long, then: Long, locale: Locale = Locale.getDefault()): ConversationStamp {
    if (then <= 0L) return ConversationStamp(StampKind.DATE, "")
    val days = calendarDaysBetween(now, then)
    return when {
        days <= 0L -> ConversationStamp(StampKind.TIME, format("HH:mm", then, locale))
        days == 1L -> ConversationStamp(StampKind.YESTERDAY, "")
        days in 2..6 -> ConversationStamp(StampKind.WEEKDAY, format("EEEE", then, locale))
        else -> ConversationStamp(StampKind.DATE, format("dd.MM.yyyy", then, locale))
    }
}

/** The heading above a day's messages inside a conversation. */
fun dayHeading(now: Long, then: Long, locale: Locale = Locale.getDefault()): ConversationStamp {
    val days = calendarDaysBetween(now, then)
    return when {
        days <= 0L -> ConversationStamp(StampKind.TIME, "")
        days == 1L -> ConversationStamp(StampKind.YESTERDAY, "")
        days in 2..6 -> ConversationStamp(StampKind.WEEKDAY, format("EEEE", then, locale))
        else -> ConversationStamp(StampKind.DATE, format("dd MMMM yyyy", then, locale))
    }
}

/** How many midnights lie between two moments. Same day is 0, yesterday is 1. */
fun calendarDaysBetween(now: Long, then: Long): Long {
    val a = midnightOf(now)
    val b = midnightOf(then)
    return (a - b) / MILLIS_PER_DAY
}

private fun midnightOf(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun format(pattern: String, millis: Long, locale: Locale): String =
    SimpleDateFormat(pattern, locale).format(Date(millis))

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

// ════════════════════════════════════════════════════════════
// HOW MUCH OF A MESSAGE THIS IS
// ════════════════════════════════════════════════════════════

/**
 * What a piece of writing costs to send.
 *
 * A text message is 160 characters, unless it contains a character the message alphabet has no
 * room for — one Turkish ğ in an otherwise plain message drops the whole thing to 70 characters
 * per message. That is the surprise this counter exists to show before the send button is pressed.
 */
data class MessageCount(
    val characters: Int,
    val segments: Int,
    /** Characters left in the current message before it splits again. */
    val remaining: Int,
    /** Whether the message had to leave the text-message alphabet. */
    val isUnicode: Boolean
)

fun messageCount(text: String): MessageCount {
    val unicode = !isGsmEncodable(text)
    val weight = if (unicode) text.length else gsmWeight(text)
    val single = if (unicode) UNICODE_SINGLE else GSM_SINGLE
    val multi = if (unicode) UNICODE_MULTI else GSM_MULTI

    if (weight == 0) {
        return MessageCount(characters = 0, segments = 0, remaining = single, isUnicode = unicode)
    }
    if (weight <= single) {
        return MessageCount(text.length, 1, single - weight, unicode)
    }
    val segments = (weight + multi - 1) / multi
    return MessageCount(text.length, segments, segments * multi - weight, unicode)
}

/** Whether every character fits the text-message alphabet. */
fun isGsmEncodable(text: String): Boolean =
    text.all { it in GSM_BASIC || it in GSM_EXTENDED }

/** Characters the alphabet only reaches with an escape count twice. */
private fun gsmWeight(text: String): Int =
    text.sumOf { if (it in GSM_EXTENDED) 2 else 1 }

private const val GSM_SINGLE = 160
private const val GSM_MULTI = 153
private const val UNICODE_SINGLE = 70
private const val UNICODE_MULTI = 67

private val GSM_BASIC: Set<Char> = buildSet {
    addAll("@£\$¥èéùìòÇØøÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?".toSet())
    addAll("¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà".toSet())
    add('\n')
    add('\r')
}

private val GSM_EXTENDED: Set<Char> = setOf('^', '{', '}', '\\', '[', ']', '~', '|', '€', '')

// ════════════════════════════════════════════════════════════
// DAY SEPARATORS INSIDE A CONVERSATION
// ════════════════════════════════════════════════════════════

/** A day's worth of messages, in the order they were sent. */
data class MessageDay(val dayStart: Long, val messages: List<SmsMessageModel>)

/**
 * Splits a conversation into days, oldest first, so the screen can put a date between them.
 * Messages arriving out of order are sorted first: the phone's own store is not always in order.
 */
fun groupMessagesByDay(messages: List<SmsMessageModel>): List<MessageDay> =
    messages
        .sortedBy { it.timestamp }
        .groupBy { midnightOf(it.timestamp) }
        .toSortedMap()
        .map { (day, list) -> MessageDay(day, list) }
