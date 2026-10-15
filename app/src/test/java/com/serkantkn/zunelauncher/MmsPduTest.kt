package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.MmsPdu
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPduTest {

    private val picture = ByteArray(300) { (it * 7).toByte() }

    private fun request(subject: String = "") = MmsPdu.SendRequest(
        transactionId = "T1234",
        to = listOf("+905551112233", "0532 444 55 66"),
        subject = subject,
        parts = listOf(
            MmsPdu.Part(MmsPdu.SMIL, "<smil/>".toByteArray(), location = "smil.xml", contentId = "smil"),
            MmsPdu.Part("text/plain", "Akşam görüşürüz".toByteArray(), location = "text_0.txt", charset = 106),
            MmsPdu.Part("image/jpeg", picture, location = "image_0.jpg")
        ),
        deliveryReport = true,
        readReport = false,
        dateSeconds = 1_790_000_000L
    )

    @Test
    fun aMessageSentReadsBackAsItWasWritten() {
        val bytes = MmsPdu.encodeSendRequest(request(subject = "Tatil"))
        // The three headers a server reads first, in the order it reads them.
        assertEquals(0x8C, bytes[0].toInt() and 0xFF)
        assertEquals(MmsPdu.TYPE_SEND_REQ, bytes[1].toInt() and 0xFF)
        assertEquals(0x98, bytes[2].toInt() and 0xFF)

        val parsed = MmsPdu.parse(bytes)!!
        assertEquals(MmsPdu.TYPE_SEND_REQ, parsed.type)
        assertEquals("T1234", parsed.transactionId)
        assertEquals(listOf("+905551112233", "05324445566"), parsed.to)
        assertEquals("Tatil", parsed.subject)
        assertEquals(1_790_000_000L, parsed.dateSeconds)
        assertEquals(MmsPdu.MULTIPART_RELATED, parsed.contentType)
        assertEquals(3, parsed.parts.size)
        assertEquals("Akşam görüşürüz", parsed.parts[1].text())
        assertEquals("image/jpeg", parsed.parts[2].contentType)
        assertEquals("image_0.jpg", parsed.parts[2].location)
        assertArrayEquals(picture, parsed.parts[2].data)
    }

    @Test
    fun aTurkishSubjectKeepsItsLetters() {
        assertEquals("Doğum günü", MmsPdu.parse(MmsPdu.encodeSendRequest(request(subject = "Doğum günü")))!!.subject)
    }

    @Test
    fun aLongPartAnnouncesItsLengthInSevenBitSteps() {
        val big = ByteArray(200_000) { 1 }
        val req = MmsPdu.SendRequest("T", listOf("+90"), "", listOf(MmsPdu.Part("video/mp4", big, location = "v.mp4")), false, false, 1L)
        assertEquals(200_000, MmsPdu.parse(MmsPdu.encodeSendRequest(req))!!.parts.single().data.size)
    }

    @Test
    fun receiptsCarryWhatTheServerMatchesThemBy() {
        val notify = MmsPdu.parse(MmsPdu.encodeNotifyResponse("TX9"))!!
        assertEquals(MmsPdu.TYPE_NOTIFYRESP_IND, notify.type)
        assertEquals("TX9", notify.transactionId)
        assertEquals(MmsPdu.STATUS_RETRIEVED, notify.status)

        val read = MmsPdu.parse(MmsPdu.encodeReadReport("MSG-1", "+905551112233", 5L))!!
        assertEquals(MmsPdu.TYPE_READ_REC_IND, read.type)
        assertEquals("MSG-1", read.messageId)
        assertEquals(listOf("+905551112233"), read.to)
        assertEquals(MmsPdu.READ_STATUS_READ, read.readStatus)
    }

    @Test
    fun aDeliveryReportFromTheServerIsRead() {
        // M-Delivery.ind as a server writes it: type, version, message id, to, date, status.
        val pdu = byteArrayOf(
            0x8C.toByte(), 0x86.toByte(), 0x8D.toByte(), 0x92.toByte(),
            0x8B.toByte(), *"abc@mms".toByteArray(), 0,
            0x97.toByte(), *"+905551112233/TYPE=PLMN".toByteArray(), 0,
            0x85.toByte(), 4, 0x6A, 0xB0.toByte(), 0x5E, 0x00,
            0x95.toByte(), 0x81.toByte()
        )
        val parsed = MmsPdu.parse(pdu)!!
        assertEquals(MmsPdu.TYPE_DELIVERY_IND, parsed.type)
        assertEquals("abc@mms", parsed.messageId)
        assertEquals(listOf("+905551112233"), parsed.to)
        assertEquals(MmsPdu.STATUS_RETRIEVED, parsed.status)
    }

    @Test
    fun aFetchedMessageGivesItsSenderAndParts() {
        // M-Retrieve.conf: the sender in "address present" form, a read report asked for, and a
        // multipart body with one text part named by a well-known content type.
        val text = "Selam".toByteArray()
        val part = byteArrayOf(
            1,                       // one part
            9, text.size.toByte(),   // headers length, data length
            0x83.toByte(),           // text/plain, short form
            0x8E.toByte(), *"t.txt".toByteArray(), 0,
            0xC0.toByte(), 0x22, '<'.code.toByte(), 'a'.code.toByte(), '>'.code.toByte(), 0
        )
        // The headers' length in the part above counts: content type (1) + location (7) + id (6) = 14.
        part[1] = 14
        val from = "+905551112233/TYPE=PLMN".toByteArray()
        val pdu = byteArrayOf(
            0x8C.toByte(), 0x84.toByte(), 0x8D.toByte(), 0x92.toByte(),
            0x8B.toByte(), *"id-7".toByteArray(), 0,
            0x89.toByte(), (from.size + 2).toByte(), 0x80.toByte(), *from, 0,
            0x90.toByte(), 0x80.toByte(),
            0x84.toByte(), 0xA3.toByte(),
            *part, *text
        )
        val parsed = MmsPdu.parse(pdu)!!
        assertEquals(MmsPdu.TYPE_RETRIEVE_CONF, parsed.type)
        assertEquals("+905551112233", parsed.from)
        assertTrue(parsed.readReportWanted)
        assertEquals("Selam", parsed.parts.single().text())
        assertEquals("t.txt", parsed.parts.single().location)
    }

    @Test
    fun numbersAreWrittenAsTheServerWantsThem() {
        assertEquals("+905551112233/TYPE=PLMN", MmsPdu.addressForServer("+90 555 111 22 33"))
        assertEquals("ali@example.com", MmsPdu.addressForServer("ali@example.com"))
        assertEquals("+905551112233", MmsPdu.addressFromServer("+905551112233/TYPE=PLMN"))
    }

    @Test
    fun nothingIsNotAMessage() {
        assertNull(MmsPdu.parse(ByteArray(0)))
        assertNull(MmsPdu.parse(byteArrayOf(0x8C.toByte())))
    }
}
