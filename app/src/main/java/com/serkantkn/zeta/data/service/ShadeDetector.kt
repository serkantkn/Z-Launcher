package com.serkantkn.zeta.data.service

/**
 * Tells the notification shade apart from SystemUI's other windows — the power menu, the
 * screenshot preview, the volume dialog — from what an accessibility event says about it.
 *
 * Pure, so the rule can be tested against what real phones report.
 */
object ShadeDetector {

    fun isShade(className: CharSequence?, text: List<CharSequence>?, description: CharSequence?): Boolean {
        val words = buildList {
            className?.let { add(it.toString()) }
            text?.forEach { add(it.toString()) }
            description?.let { add(it.toString()) }
        }.joinToString(" ").lowercase()
        if (words.isBlank()) return false
        if (NOT_SHADE.any { it in words }) return false
        return SHADE.any { it in words }
    }

    /**
     * Whether the event says anything besides the shade's own name. A shade that is really
     * coming down reports what is in it — the clock, the date, the settings, the notifications.
     * SystemUI also reports the bare name alone, as the shade folds away and as the lock screen
     * (which lives in the same window) goes away on unlocking; none of those is the shade
     * opening, and taking the last for it brought the action center down on every unlock.
     */
    fun hasContent(text: List<CharSequence>?): Boolean =
        text.orEmpty().any { item ->
            val words = item.toString().trim().lowercase()
            words.isNotEmpty() && SHADE.none { it in words }
        }

    /** What phones call the shade, in the languages this launcher speaks and in class names. */
    private val SHADE = listOf(
        "notification shade", "notificationshade", "quick settings", "quicksettings",
        "bildirim gölgesi", "bildirim paneli", "hızlı ayarlar", "notification panel",
        "statusbarwindow", "notificationpanel"
    )

    /** SystemUI windows that mention one of the words above but are not the shade. */
    private val NOT_SHADE = listOf("globalactions", "power menu", "güç menüsü", "screenshot", "volume")
}
