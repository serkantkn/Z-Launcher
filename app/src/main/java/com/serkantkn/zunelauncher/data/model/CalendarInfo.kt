package com.serkantkn.zunelauncher.data.model

/**
 * One of the phone's calendars.
 *
 * A phone usually has several: the account's own, a birthdays feed, a holidays feed, sometimes a
 * shared work one. They are not equal — a holidays feed cannot be written to, and nobody wants
 * their agenda to be mostly public holidays — so the hub lists them and lets each be switched off.
 */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val colorHex: String,
    /** Whether the launcher may add events to it. */
    val isWritable: Boolean,
    /** The account's main calendar, which is where a new event goes unless told otherwise. */
    val isPrimary: Boolean
) {
    /** The launcher's own store, offered alongside the phone's calendars. */
    companion object {
        const val LOCAL_ID = -1L
    }
}
