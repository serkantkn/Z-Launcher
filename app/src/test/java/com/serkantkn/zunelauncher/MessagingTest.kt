package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.ui.screens.messaging.StampKind
import com.serkantkn.zunelauncher.ui.screens.messaging.calendarDaysBetween
import com.serkantkn.zunelauncher.ui.screens.messaging.conversationStamp
import com.serkantkn.zunelauncher.ui.screens.messaging.groupMessagesByDay
import com.serkantkn.zunelauncher.ui.screens.messaging.isGsmEncodable
import com.serkantkn.zunelauncher.ui.screens.messaging.messageCount
import com.serkantkn.zunelauncher.util.parseSmsLink
import com.serkantkn.zunelauncher.util.percentDecode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * The parts of the Messaging hub that can be checked without a phone: how much of a text message a
 * piece of writing is, how a time reads in the list, and what an `sms:` link actually says.
 */
class MessagingTest {

    // ── How much of a message this is ───────────────────────────────────────

    @Test
    fun `a short plain message is one message`() {
        val count = messageCount("hello there")
        assertEquals(1, count.segments)
        assertEquals(11, count.characters)
        assertFalse(count.isUnicode)
        assertEquals(160 - 11, count.remaining)
    }

    @Test
    fun `nothing typed is no message at all`() {
        val count = messageCount("")
        assertEquals(0, count.segments)
        assertEquals(160, count.remaining)
    }

    @Test
    fun `exactly 160 plain characters still fit one message`() {
        val count = messageCount("a".repeat(160))
        assertEquals(1, count.segments)
        assertEquals(0, count.remaining)
    }

    @Test
    fun `one character more splits into two of 153`() {
        val count = messageCount("a".repeat(161))
        assertEquals(2, count.segments)
        assertEquals(153 * 2 - 161, count.remaining)
    }

    @Test
    fun `one turkish letter drops the whole message to 70 characters`() {
        val plain = messageCount("a".repeat(71))
        assertEquals(1, plain.segments)

        val turkish = messageCount("ğ" + "a".repeat(70))
        assertTrue(turkish.isUnicode)
        assertEquals(2, turkish.segments)
    }

    @Test
    fun `the turkish letters the message alphabet does have stay cheap`() {
        // Ç, Ö, Ü and ö, ü are in the text-message alphabet; ğ, ş, İ, ı are not.
        assertTrue(isGsmEncodable("ÇÖÜöü"))
        assertFalse(isGsmEncodable("ğ"))
        assertFalse(isGsmEncodable("ş"))
        assertFalse(isGsmEncodable("ı"))
    }

    @Test
    fun `a brace costs two characters because the alphabet has to escape it`() {
        val count = messageCount("{".repeat(80))
        assertFalse(count.isUnicode)
        assertEquals(1, count.segments)
        assertEquals(0, count.remaining)
    }

    @Test
    fun `an emoji is not a plain message`() {
        val count = messageCount("hello 🙂")
        assertTrue(count.isUnicode)
        assertEquals(1, count.segments)
    }

    // ── When it happened ────────────────────────────────────────────────────

    @Test
    fun `a message from this morning reads as a time`() {
        val now = at(2026, Calendar.MARCH, 12, 18, 40)
        val then = at(2026, Calendar.MARCH, 12, 9, 5)
        val stamp = conversationStamp(now, then, Locale.ENGLISH)
        assertEquals(StampKind.TIME, stamp.kind)
        assertEquals("09:05", stamp.text)
    }

    @Test
    fun `a message from last night reads as yesterday`() {
        val now = at(2026, Calendar.MARCH, 12, 9, 0)
        val then = at(2026, Calendar.MARCH, 11, 23, 30)
        assertEquals(StampKind.YESTERDAY, conversationStamp(now, then, Locale.ENGLISH).kind)
    }

    @Test
    fun `a message from earlier this week reads as a day name`() {
        val now = at(2026, Calendar.MARCH, 12, 9, 0)
        val then = at(2026, Calendar.MARCH, 9, 14, 0)
        val stamp = conversationStamp(now, then, Locale.ENGLISH)
        assertEquals(StampKind.WEEKDAY, stamp.kind)
        assertEquals("Monday", stamp.text)
    }

    @Test
    fun `an older message reads as a date`() {
        val now = at(2026, Calendar.MARCH, 12, 9, 0)
        val then = at(2026, Calendar.FEBRUARY, 2, 14, 0)
        val stamp = conversationStamp(now, then, Locale.ENGLISH)
        assertEquals(StampKind.DATE, stamp.kind)
        assertEquals("02.02.2026", stamp.text)
    }

    @Test
    fun `a message with no date at all does not pretend to have one`() {
        val stamp = conversationStamp(at(2026, Calendar.MARCH, 12, 9, 0), 0L, Locale.ENGLISH)
        assertEquals("", stamp.text)
    }

    @Test
    fun `midnight to one minute past is still one day apart`() {
        val now = at(2026, Calendar.MARCH, 12, 0, 1)
        val then = at(2026, Calendar.MARCH, 11, 23, 59)
        assertEquals(1L, calendarDaysBetween(now, then))
    }

    // ── Days inside a conversation ──────────────────────────────────────────

    @Test
    fun `messages are grouped by day, oldest day first`() {
        val messages = listOf(
            message(id = 3, at = at(2026, Calendar.MARCH, 12, 9, 0)),
            message(id = 1, at = at(2026, Calendar.MARCH, 10, 20, 0)),
            message(id = 2, at = at(2026, Calendar.MARCH, 12, 8, 0))
        )
        val days = groupMessagesByDay(messages)
        assertEquals(2, days.size)
        assertEquals(listOf(1L), days[0].messages.map { it.id })
        assertEquals(listOf(2L, 3L), days[1].messages.map { it.id })
    }

    @Test
    fun `an empty conversation has no days`() {
        assertTrue(groupMessagesByDay(emptyList()).isEmpty())
    }

    // ── sms: links ──────────────────────────────────────────────────────────

    @Test
    fun `a plain sms link is just a number`() {
        val target = parseSmsLink("+905551112233")
        assertEquals("+905551112233", target.address)
        assertEquals("", target.body)
    }

    @Test
    fun `a link can carry the message with it`() {
        val target = parseSmsLink("05551112233?body=merhaba%20d%C3%BCnya")
        assertEquals("05551112233", target.address)
        assertEquals("merhaba dünya", target.body)
    }

    @Test
    fun `a link to several people opens the first`() {
        assertEquals("111", parseSmsLink("111,222,333?body=hi").address)
    }

    @Test
    fun `a link with no body leaves the message empty`() {
        val target = parseSmsLink("555?subject=hello")
        assertEquals("555", target.address)
        assertEquals("", target.body)
    }

    @Test
    fun `an empty link asks for nobody`() {
        assertEquals(SmsLinkEmpty, parseSmsLink("").address to parseSmsLink("").body)
    }

    @Test
    fun `a stray percent is kept rather than swallowed`() {
        assertEquals("100% sure", percentDecode("100% sure"))
    }

    @Test
    fun `a plus in a query is a space`() {
        assertEquals("see you soon", percentDecode("see+you+soon"))
    }

    // ── Pinned conversations ────────────────────────────────────────────────

    @Test
    fun `a conversation pinned to start remembers which conversation it is`() {
        val tile = StartTileItem.fromThread(42L, "  Ayşe  ")
        assertTrue(tile.isThread)
        assertEquals(42L, tile.smsThreadId)
        assertEquals("Ayşe", tile.name)
    }

    @Test
    fun `a person tile is not a conversation tile`() {
        val person = StartTileItem.fromPerson("7", "Ayşe")
        assertFalse(person.isThread)
        assertEquals(null, person.smsThreadId)
    }

    @Test
    fun `a conversation tile survives being written down and read back`() {
        val tile = StartTileItem.fromThread(9L, "Mehmet", span = 4)
        val back = StartTileItem.fromJson(tile.toJson())
        assertEquals(tile, back)
        assertEquals(9L, back.smsThreadId)
    }

    // ── What a conversation calls itself ────────────────────────────────────

    @Test
    fun `a conversation with a nameless number shows the number`() {
        val conversation = conversation(name = "", address = "+905551112233")
        assertEquals("+905551112233", conversation.title)
    }

    @Test
    fun `a conversation with more than one number is a group`() {
        assertTrue(conversation(recipients = listOf("111", "222")).isGroup)
        assertFalse(conversation(recipients = listOf("111")).isGroup)
    }

    @Test
    fun `whitespace alone is not a draft`() {
        assertFalse(conversation(draft = "   ").hasDraft)
        assertTrue(conversation(draft = "yarın").hasDraft)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private val SmsLinkEmpty = "" to ""

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun message(id: Long, at: Long) = SmsMessageModel(
        id = id,
        threadId = 1L,
        address = "555",
        body = "hi",
        timestamp = at,
        isOutgoing = false,
        isRead = true
    )

    private fun conversation(
        name: String = "Ayşe",
        address: String = "555",
        draft: String = "",
        recipients: List<String> = listOf("555")
    ) = SmsConversationModel(
        threadId = 1L,
        address = address,
        contactName = name,
        snippet = "",
        timestamp = 0L,
        isRead = true,
        draft = draft,
        recipients = recipients
    )
}
