package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.ContactModel

/** One moment of being in touch with somebody: a call placed or taken, or a message either way. */
data class ContactTouch(val number: String, val at: Long)

/**
 * Who has been in touch lately.
 *
 * The hub used to take this from the contacts database's own "last time contacted" column, which
 * Android stopped keeping up to date in Android 10 — it reads nought for everybody, so the recent
 * page was permanently empty however much you called and texted. This works it out from the call
 * log and the message list instead, matching each number back to the person it belongs to.
 */
internal fun recentContacts(
    contacts: List<ContactModel>,
    numbersByContact: Map<String, List<String>>,
    touches: List<ContactTouch>,
    limit: Int = 20
): List<ContactModel> {
    if (contacts.isEmpty() || touches.isEmpty()) return emptyList()

    // One lookup from "a number as anyone might write it" to the person who owns it.
    val ownerOfNumber = HashMap<String, String>()
    numbersByContact.forEach { (contactId, numbers) ->
        numbers.forEach { number ->
            val key = PhoneNumbers.matchKey(number)
            if (key.isNotEmpty()) ownerOfNumber.putIfAbsent(key, contactId)
        }
    }

    val lastTouch = HashMap<String, Long>()
    touches.forEach { touch ->
        val contactId = ownerOfNumber[PhoneNumbers.matchKey(touch.number)] ?: return@forEach
        val known = lastTouch[contactId]
        if (known == null || touch.at > known) lastTouch[contactId] = touch.at
    }

    return contacts
        .mapNotNull { contact -> lastTouch[contact.id]?.let { contact to it } }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}
