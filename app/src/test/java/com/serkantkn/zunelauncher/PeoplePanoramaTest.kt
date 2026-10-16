package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.ui.components.forwardStepsToFirst
import com.serkantkn.zunelauncher.ui.screens.people.newsFromPeople
import com.serkantkn.zunelauncher.util.ContactTouch
import com.serkantkn.zunelauncher.util.recentContacts
import org.junit.Assert.assertEquals
import org.junit.Test

class PeoplePanoramaTest {

    private fun person(id: String, name: String) =
        ContactModel(id = id, name = name, photoUri = null, isFavorite = false, lastTimeContacted = 0L, hasPhoneNumber = true)

    private fun message(id: String, from: String, at: Long) = SocialMessageModel(
        id = id, packageName = "com.whatsapp", appName = "WhatsApp", title = from, text = "selam",
        timestamp = at, icon = null, replyAction = null, openIntent = null
    )

    @Test
    fun theGlideGoesForwardRoundTheEnd() {
        // "recent" is the last of five: one swipe forward reaches "all".
        assertEquals(1, forwardStepsToFirst(from = 4, pageCount = 5, looping = true))
        assertEquals(3, forwardStepsToFirst(from = 2, pageCount = 5, looping = true))
        assertEquals(0, forwardStepsToFirst(from = 0, pageCount = 5, looping = true))
        // Without the loop there is no way round, only back.
        assertEquals(-4, forwardStepsToFirst(from = 4, pageCount = 5, looping = false))
    }

    @Test
    fun whatsNewIsWhatPeopleInTheAddressBookSaid() {
        val contacts = listOf(person("1", "Ayşe Demir"), person("2", "Annem"))
        val messages = listOf(
            message("a", "ayşe  demir", at = 100),
            message("b", "Bir Yabancı", at = 300),
            message("c", "Annem", at = 200)
        )
        val news = newsFromPeople(messages, contacts)
        assertEquals(listOf("c", "a"), news.map { it.message.id })
        assertEquals(listOf("Annem", "Ayşe Demir"), news.map { it.contact.name })
    }

    @Test
    fun someoneLookedAtIsRecentToo() {
        val contacts = listOf(person("1", "Annem"), person("2", "Babam"), person("3", "Can"))
        val numbers = mapOf("1" to listOf("05321112233"))
        val recent = recentContacts(
            contacts, numbers,
            touches = listOf(ContactTouch("+90 532 111 22 33", at = 100)),
            viewed = mapOf("3" to 300L, "1" to 50L)
        )
        // Can was looked at last; Annem's call is later than her being looked at.
        assertEquals(listOf("Can", "Annem"), recent.map { it.name })
    }

    @Test
    fun withNobodyCalledTheOnesLookedAtStillShow() {
        val contacts = listOf(person("1", "Annem"), person("2", "Babam"))
        assertEquals(listOf("Babam"), recentContacts(contacts, emptyMap(), emptyList(), viewed = mapOf("2" to 5L)).map { it.name })
    }
}
