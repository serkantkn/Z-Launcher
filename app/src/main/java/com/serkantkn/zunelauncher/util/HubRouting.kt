package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.SmsConversationModel

/** One of the launcher's own hubs that can show a notification in place of the app that sent it. */
enum class HubDestination { MESSAGING, PHONE }

/**
 * Whether the launcher can open a notification itself.
 *
 * The launcher has its own messages and its own call history, reading the same stores the phone's
 * own apps read. When a notification is about something one of those hubs already holds, opening
 * the hub is better than leaving the launcher for an app that shows the same thing — that is the
 * whole point of having the hubs.
 *
 * It is deliberately a short list. A notification is only routed when the hub can show *the same
 * item*, not merely something similar: a mail hub with no account set up, or a calendar that
 * cannot jump to the event, would be worse than the app.
 */
fun hubFor(
    packageName: String,
    category: String?,
    defaultSmsPackage: String?,
    defaultDialerPackage: String?
): HubDestination? {
    if (packageName.isBlank()) return null

    // Text messages: the Messaging hub reads the same store the SMS app writes to.
    if (packageName == defaultSmsPackage || packageName in KNOWN_SMS_APPS) {
        return HubDestination.MESSAGING
    }

    // A dialler announces several things; only a missed call is one the Phone hub already has.
    if (packageName == defaultDialerPackage || packageName in KNOWN_DIALERS) {
        return if (category == CATEGORY_MISSED_CALL) HubDestination.PHONE else null
    }

    return null
}

/**
 * The conversation a message notification is about.
 *
 * The notification only gives a heading — a contact's name, or a bare number — so the match is
 * made the way a person would: the same name, or the same line. Nothing is guessed: when neither
 * matches, the hub simply opens on its list rather than on the wrong conversation.
 */
fun matchConversation(
    heading: String,
    conversations: List<SmsConversationModel>
): SmsConversationModel? {
    val needle = heading.trim()
    if (needle.isEmpty() || conversations.isEmpty()) return null

    val folded = foldForSearch(needle)
    conversations.firstOrNull { foldForSearch(it.contactName) == folded && it.contactName.isNotBlank() }
        ?.let { return it }

    // A heading that is a number: compare it as a line, not as text.
    if (PhoneNumbers.digitsOf(needle).length >= MINIMUM_DIGITS) {
        conversations.firstOrNull { PhoneNumbers.sameNumber(it.address, needle) }?.let { return it }
    }
    return null
}

/** Android's own category for a call that was not answered. */
private const val CATEGORY_MISSED_CALL = "missed_call"

/** Shorter than this and it is not a number anybody could be reached on. */
private const val MINIMUM_DIGITS = 5

private val KNOWN_SMS_APPS = setOf(
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.android.mms",
    "com.textra",
    "com.moez.QKSMS"
)

private val KNOWN_DIALERS = setOf(
    "com.google.android.dialer",
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.android.server.telecom"
)
