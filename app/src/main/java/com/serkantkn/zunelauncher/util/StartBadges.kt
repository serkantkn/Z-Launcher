package com.serkantkn.zunelauncher.util

/**
 * The count on the messaging and phone tiles.
 *
 * The launcher reads its own unread messages and missed calls from the providers, and the
 * notification listener counts what the apps on the phone post. The two are combined by the
 * larger of them — but only the notifications of the app that actually holds the role count,
 * resolved at runtime rather than written in as the two Google apps by name.
 */
object StartBadges {

    /**
     * [own] is what the launcher counted itself; [counts] are the listener's counts by package;
     * [rolePackage] is the app that holds the SMS or dialer role, null when the phone would not
     * say. When the launcher itself holds the role its own count is the whole story: the
     * listener never sees the launcher's notifications, and another app's would be stale.
     */
    fun count(own: Int, counts: Map<String, Int>, rolePackage: String?, selfPackage: String): Int {
        if (rolePackage == null || rolePackage == selfPackage) return own
        return maxOf(own, counts[rolePackage] ?: 0)
    }
}
