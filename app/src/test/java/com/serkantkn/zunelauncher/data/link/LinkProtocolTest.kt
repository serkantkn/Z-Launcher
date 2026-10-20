package com.serkantkn.zunelauncher.data.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkProtocolTest {

    private val phoneId = LinkIdentity("phone-1", "Ayşe'nin telefonu")
    private val tvId = LinkIdentity("tv-1", "Salon TV")

    private fun pair(phoneCode: String, tvCode: String): Pair<Result<PairingHandshake.Result>, Result<PairingHandshake.Result>> {
        val phone = PairingHandshake(phoneId, phoneCode, phone = true)
        val tv = PairingHandshake(tvId, tvCode, phone = false)
        val phoneHello = phone.hello()
        val tvHello = tv.hello()
        tv.receiveHello(phoneHello)
        phone.receiveHello(tvHello)
        val tvResult = runCatching { tv.check(phone.proof()) }
        val phoneResult = runCatching { phone.check(tv.proof()) }
        return phoneResult to tvResult
    }

    @Test
    fun theSameCodeGivesBothSidesTheSameKey() {
        val (phone, tv) = pair("482913", "482913")
        assertTrue(phone.isSuccess && tv.isSuccess)
        assertArrayEquals(phone.getOrThrow().key, tv.getOrThrow().key)
        assertEquals("tv-1", phone.getOrThrow().peer.id)
        assertEquals("phone-1", tv.getOrThrow().peer.id)
    }

    @Test
    fun aWrongCodeIsRefusedOnBothSides() {
        val (phone, tv) = pair("482913", "482914")
        assertTrue(tv.exceptionOrNull() is PairingHandshake.WrongCode)
        assertTrue(phone.exceptionOrNull() is PairingHandshake.WrongCode)
    }

    @Test
    fun aNoticeOpensOnlyWithItsKeyAndItsPhone() {
        val key = pair("111111", "111111").first.getOrThrow().key
        val notice = LinkNotice(LinkNotice.Kind.SMS, "Ayşe", "+905551112233", text = "Akşam geliyor musun?")
        val frame = NoticeSeal.seal("phone-1", key, notice)
        assertEquals(notice, NoticeSeal.open(frame, key))
        // Another key, or the frame claiming to be another phone, does not open.
        assertNull(NoticeSeal.open(frame, LinkCrypto.randomBytes(32)))
        assertNull(NoticeSeal.open(frame.put("id", "phone-2"), key))
    }

    @Test
    fun aNoticeWithoutItsTextCarriesNone() {
        val notice = LinkNotice(LinkNotice.Kind.CALL, "Ayşe", "+905551112233")
        val back = LinkNotice.fromJson(notice.toJson())
        assertNull(back.text)
        assertNull(back.app)
        assertEquals(notice, back)
    }
}
