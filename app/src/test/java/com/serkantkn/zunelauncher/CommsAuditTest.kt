package com.serkantkn.zunelauncher

import android.provider.CallLog
import com.serkantkn.zunelauncher.data.model.CallLogModel
import com.serkantkn.zunelauncher.data.repository.escapeLike
import com.serkantkn.zunelauncher.data.service.MessageNotifier
import com.serkantkn.zunelauncher.util.MmsNotificationParser
import com.serkantkn.zunelauncher.util.RequestCodes
import com.serkantkn.zunelauncher.util.groupCalls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure parts of the audit fixes across phone, messaging and mail: ids that must not
 * collide, a search that must not treat "%" as a wildcard, a picture-message push read
 * back out of its bytes, and a withheld number that must not crash the history.
 */
class CommsAuditTest {

    // ── Request codes ─────────────────────────────────────────────────────────────────────────

    @Test
    fun theSameKeyAlwaysGivesTheSameCode() {
        assertEquals(
            RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, "acc", "INBOX", 42L),
            RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, "acc", "INBOX", 42L)
        )
    }

    @Test
    fun differentKindsNeverShareACodeWhateverTheirKeys() {
        val sms = RequestCodes.of(RequestCodes.Kind.SMS_OPEN, 7L)
        val mail = RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, 7L)
        val reply = RequestCodes.of(RequestCodes.Kind.SMS_REPLY, 7L)
        assertNotEquals(sms, mail)
        assertNotEquals(sms, reply)
        // The kind lives in the top bits, so it can be read back off the code.
        assertEquals(RequestCodes.Kind.EMAIL_OPEN.ordinal, mail ushr 28)
        assertEquals(RequestCodes.Kind.SMS_OPEN.ordinal, sms ushr 28)
    }

    @Test
    fun twoMailsFromOneAccountGetTwoCodes() {
        val a = RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, "acc", "INBOX", 1L)
        val b = RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, "acc", "INBOX", 2L)
        val c = RequestCodes.of(RequestCodes.Kind.EMAIL_OPEN, "acc", "Sent", 1L)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
    }

    // ── Message notification ids ──────────────────────────────────────────────────────────────

    @Test
    fun conversationsAThousandApartNoLongerShareANotification() {
        // The old `threadId % 1000` put these two on one id.
        assertNotEquals(MessageNotifier.notificationId(12L), MessageNotifier.notificationId(1012L))
        val ids = (1L..5000L).map { MessageNotifier.notificationId(it) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun aNotificationIdStaysOutOfTheRangeOtherNotifiersUse() {
        // Calls use 4201/4202 and the old message base was 5200; neither can be reached now.
        assertTrue(MessageNotifier.notificationId(0L) > 10_000)
        assertTrue(MessageNotifier.notificationId(999_999_999L) > 0)
    }

    // ── Search escaping ───────────────────────────────────────────────────────────────────────

    @Test
    fun wildcardsAndTheEscapeItselfAreEscapedForLike() {
        assertEquals("100\\%", escapeLike("100%"))
        assertEquals("a\\_b", escapeLike("a_b"))
        assertEquals("c:\\\\temp", escapeLike("c:\\temp"))
        assertEquals("plain", escapeLike("plain"))
    }

    // ── A picture message's push ──────────────────────────────────────────────────────────────

    private fun notificationInd(
        from: String = "+905551112233/TYPE=PLMN",
        subject: String? = "hello",
        expiryRelative: Long = 3600L
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun text(s: String) { out.write(s.toByteArray()); out.write(0) }
        out.write(0x8C); out.write(0x82)                         // message type: notification-ind
        out.write(0x98); text("tx-1")                            // transaction id
        out.write(0x8D); out.write(0x92)                         // MMS version 1.2 (short integer)
        // From: value-length, address-present token, encoded string
        val fromBytes = java.io.ByteArrayOutputStream().apply { write(0x80); write(from.toByteArray()); write(0) }.toByteArray()
        out.write(0x89); out.write(fromBytes.size); out.write(fromBytes)
        if (subject != null) { out.write(0x96); text(subject) }
        out.write(0x8A); out.write(0x80)                         // class: personal
        out.write(0x8E); out.write(2); out.write(0x12); out.write(0x34) // size: long integer 0x1234
        // Expiry: value-length, relative token, long integer (2 bytes)
        val exp = java.io.ByteArrayOutputStream().apply {
            write(0x81); write(2); write((expiryRelative shr 8).toInt() and 0xFF); write((expiryRelative and 0xFF).toInt())
        }.toByteArray()
        out.write(0x88); out.write(exp.size); out.write(exp)
        out.write(0x83); text("http://mms.example.com/m/1")     // content location
        return out.toByteArray()
    }

    @Test
    fun aNotificationIndReadsBackItsSenderSizeAndLocation() {
        val push = MmsNotificationParser.parse(notificationInd(), nowMillis = 1_000_000L)
        assertNotNull(push)
        push!!
        assertEquals("tx-1", push.transactionId)
        assertEquals("http://mms.example.com/m/1", push.contentLocation)
        assertEquals("+905551112233", push.from)
        assertEquals("hello", push.subject)
        assertEquals(0x1234L, push.messageSize)
        assertEquals("personal", push.messageClass)
        assertEquals(1_000L + 3600L, push.expirySeconds)
    }

    @Test
    fun aPushWithoutASubjectIsStillAPush() {
        val push = MmsNotificationParser.parse(notificationInd(subject = null))
        assertNotNull(push)
        assertEquals("", push!!.subject)
    }

    @Test
    fun somethingThatIsNotANotificationIsNotMistakenForOne() {
        assertNull(MmsNotificationParser.parse(null))
        assertNull(MmsNotificationParser.parse(byteArrayOf()))
        // A delivery report has another message type.
        assertNull(MmsNotificationParser.parse(byteArrayOf(0x8C.toByte(), 0x86.toByte(), 0x98.toByte(), 'a'.code.toByte(), 0)))
        // Cut off before the content location: not enough to file.
        assertNull(MmsNotificationParser.parse(notificationInd().copyOf(8)))
    }

    // ── A withheld number in the history ──────────────────────────────────────────────────────

    private fun call(id: String, number: String, presentation: Int = CallLog.Calls.PRESENTATION_ALLOWED) =
        CallLogModel(id, null, number, 1_000L * id.toLong(), 0L, CallLog.Calls.MISSED_TYPE, presentation)

    @Test
    fun aWithheldNumberIsOneRowAndSaysSo() {
        val groups = groupCalls(
            listOf(
                call("3", "", CallLog.Calls.PRESENTATION_RESTRICTED),
                call("2", "", CallLog.Calls.PRESENTATION_RESTRICTED),
                call("1", "5551234567")
            )
        )
        assertEquals(2, groups.size)
        assertTrue(groups[0].isPrivate)
        assertEquals(2, groups[0].count)
        assertTrue(!groups[1].isPrivate)
    }

    @Test
    fun anEmptyNumberWithoutAPresentationIsStillPrivate() {
        assertTrue(call("1", "").isPrivate)
        assertTrue(!call("1", "112").isPrivate)
    }
}
