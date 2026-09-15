package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SocialApps
import com.serkantkn.zunelauncher.data.model.SocialKind
import com.serkantkn.zunelauncher.data.model.isSocialWorthy
import com.serkantkn.zunelauncher.data.model.looksSocial
import com.serkantkn.zunelauncher.util.HubDestination
import com.serkantkn.zunelauncher.util.hubFor
import com.serkantkn.zunelauncher.util.matchConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Social hub lets in.
 *
 * The whole point of the hub is that it is *not* the notification shade, so the two questions
 * worth testing are which apps it listens to at all, and which of their notifications count as
 * somebody saying something rather than the app talking about itself.
 */
class SocialTest {

    // ── Which apps ──────────────────────────────────────────────────────────

    @Test
    fun `the catalogue knows the usual suspects`() {
        assertTrue(SocialApps.isKnown("com.whatsapp"))
        assertTrue(SocialApps.isKnown("com.instagram.android"))
        assertTrue(SocialApps.isKnown("org.telegram.messenger"))
    }

    @Test
    fun `a bank and a game are not social apps`() {
        assertFalse(SocialApps.isKnown("com.example.bank"))
        assertFalse(SocialApps.isKnown("com.android.settings"))
    }

    @Test
    fun `messaging and networks are told apart`() {
        assertEquals(SocialKind.MESSAGING, SocialApps.kindOf("com.whatsapp"))
        assertEquals(SocialKind.NETWORK, SocialApps.kindOf("com.instagram.android"))
        assertEquals(null, SocialApps.kindOf("com.example.bank"))
    }

    @Test
    fun `the default sources are the catalogue apps that are actually installed`() {
        val installed = listOf("com.whatsapp", "com.example.bank", "com.instagram.android")
        assertEquals(setOf("com.whatsapp", "com.instagram.android"), SocialApps.defaultSources(installed))
    }

    @Test
    fun `a phone with no social apps starts with no sources`() {
        assertTrue(SocialApps.defaultSources(listOf("com.example.bank")).isEmpty())
    }

    @Test
    fun `a catalogue app that is not installed is not a source`() {
        assertFalse(SocialApps.defaultSources(listOf("com.whatsapp")).contains("com.instagram.android"))
    }

    // ── Which notifications ─────────────────────────────────────────────────

    @Test
    fun `a plain message gets in`() {
        assertTrue(worthy(category = "msg"))
    }

    @Test
    fun `an app that names no category still gets in`() {
        // Most apps name none, and refusing those would leave the hub empty.
        assertTrue(worthy(category = null))
    }

    @Test
    fun `a backup running is not a message`() {
        assertFalse(worthy(category = "progress"))
        assertFalse(worthy(category = "service"))
        assertFalse(worthy(isOngoing = true))
        assertFalse(worthy(hasProgress = true))
    }

    @Test
    fun `a music player is not a message`() {
        assertFalse(worthy(category = "transport"))
    }

    @Test
    fun `a call is not a message`() {
        assertFalse(worthy(category = "call"))
    }

    @Test
    fun `an advert is not a message`() {
        assertFalse(worthy(category = "promo"))
        assertFalse(worthy(category = "recommendation"))
    }

    @Test
    fun `the folded-up header of several messages is not itself one`() {
        assertFalse(worthy(isGroupSummary = true))
    }

    @Test
    fun `a notification with nothing written in it is not a message`() {
        assertFalse(worthy(hasText = false))
    }

    // ── Suggesting an app the catalogue has never heard of ──────────────────

    @Test
    fun `an app that says its notification is a message is worth suggesting`() {
        assertTrue(looksSocial("msg"))
        assertTrue(looksSocial("social"))
    }

    @Test
    fun `everything else is not suggested`() {
        assertFalse(looksSocial(null))
        assertFalse(looksSocial("email"))
        assertFalse(looksSocial("progress"))
    }

    // ── Which of the launcher's own hubs can open it ────────────────────────

    @Test
    fun `the phone's sms app goes to our messaging hub`() {
        assertEquals(
            HubDestination.MESSAGING,
            hubFor("com.example.sms", null, defaultSmsPackage = "com.example.sms", defaultDialerPackage = null)
        )
    }

    @Test
    fun `a well known sms app goes there too, whatever the phone's default is`() {
        assertEquals(
            HubDestination.MESSAGING,
            hubFor("com.google.android.apps.messaging", "msg", null, null)
        )
    }

    @Test
    fun `only a missed call sends the dialler to our phone hub`() {
        assertEquals(
            HubDestination.PHONE,
            hubFor("com.google.android.dialer", "missed_call", null, null)
        )
        // Voicemail, a call in progress, anything else: the dialler keeps it.
        assertEquals(null, hubFor("com.google.android.dialer", "call", null, null))
        assertEquals(null, hubFor("com.google.android.dialer", null, null, null))
    }

    @Test
    fun `an app we have no hub for is left to itself`() {
        assertEquals(null, hubFor("com.whatsapp", "msg", null, null))
        assertEquals(null, hubFor("com.instagram.android", "social", null, null))
        assertEquals(null, hubFor("", null, null, null))
    }

    // ── Which conversation a message notification is about ──────────────────

    @Test
    fun `a notification headed with a contact name finds their conversation`() {
        val found = matchConversation("Ayşe Yılmaz", conversations)
        assertEquals(1L, found?.threadId)
    }

    @Test
    fun `the name is matched the way it is typed, not the way it is spelled`() {
        assertEquals(1L, matchConversation("ayse yilmaz", conversations)?.threadId)
    }

    @Test
    fun `a notification headed with a number finds the line`() {
        assertEquals(2L, matchConversation("+90 555 111 22 33", conversations)?.threadId)
    }

    @Test
    fun `an unknown heading matches nothing rather than the wrong thing`() {
        assertEquals(null, matchConversation("Kargo", conversations))
        assertEquals(null, matchConversation("", conversations))
        assertEquals(null, matchConversation("Ayşe", emptyList()))
    }

    private val conversations = listOf(
        SmsConversationModel(
            threadId = 1L,
            address = "05329998877",
            contactName = "Ayşe Yılmaz",
            snippet = "",
            timestamp = 0L,
            isRead = true
        ),
        SmsConversationModel(
            threadId = 2L,
            address = "05551112233",
            contactName = "",
            snippet = "",
            timestamp = 0L,
            isRead = true
        )
    )

    private fun worthy(
        category: String? = null,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        hasProgress: Boolean = false,
        hasText: Boolean = true
    ) = isSocialWorthy(category, isOngoing, isGroupSummary, hasProgress, hasText)
}
