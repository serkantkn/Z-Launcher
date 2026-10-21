package com.serkantkn.zeta.util

/**
 * What the reader needs to know about a mail's HTML before it draws it.
 *
 * Kept apart from the WebView so it can be tested: whether the mail would fetch pictures from the
 * internet at all - which decides whether "show pictures" is offered - and the small rewrites
 * that let those pictures load once it is pressed.
 */
object EmailHtml {

    /**
     * Whether the mail would fetch anything from the internet to be drawn: a picture by its
     * address, in double quotes, single quotes or none, in `src` or `srcset`, as an old
     * `background` attribute, or as a CSS `url()`. Addresses that leave out the scheme
     * (`//host/…`) count too.
     *
     * The first version only looked for `<img src="http…`, so a mail written with single quotes
     * or with its pictures as backgrounds was never offered them at all.
     */
    fun hasRemoteImages(html: String): Boolean =
        REMOTE_ATTRIBUTE.containsMatchIn(html) || REMOTE_CSS_URL.containsMatchIn(html) || REMOTE_SRCSET_ITEM.containsMatchIn(html)

    /**
     * Gives addresses that leave out their scheme an https one.
     *
     * A mail is drawn from a string, not from an address of its own, so `//cdn.example/a.png` has
     * nothing to take its scheme from and is never fetched. Https is what such an address would
     * be on any page worth trusting.
     */
    fun withSchemes(html: String): String = html
        .replace(SCHEMELESS_ATTRIBUTE) { it.groupValues[1] + "https://" }
        .replace(SCHEMELESS_CSS_URL) { it.groupValues[1] + "https://" }
        .replace(SCHEMELESS_SRCSET_ITEM) { it.groupValues[1] + "https://" }

    /**
     * The https address to try for a picture asked for over plain http, or null when [url] is
     * not plain http.
     *
     * Android lets an app open plain http only if it says so for the whole app, and nothing else
     * here needs it; opening it for every connection the launcher makes to show a newsletter's
     * pictures would be a poor trade. Nearly every server that sends mail pictures answers on
     * https as well, which is what Chrome does with such pictures on a secure page too.
     */
    fun httpsVersion(url: String): String? =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring("http://".length) else null

    private val REMOTE_ATTRIBUTE =
        Regex("""(?i)\b(?:src|srcset|background|poster)\s*=\s*["']?\s*(?:https?:)?//""")
    private val REMOTE_CSS_URL =
        Regex("""(?i)url\(\s*["']?\s*(?:https?:)?//""")
    private val REMOTE_SRCSET_ITEM =
        Regex("""(?i)srcset\s*=\s*["'][^"']*,\s*(?:https?:)?//""")

    private val SCHEMELESS_ATTRIBUTE =
        Regex("""(?i)(\b(?:src|srcset|background|poster|href)\s*=\s*["']?\s*)//(?=[a-z0-9])""")
    private val SCHEMELESS_CSS_URL =
        Regex("""(?i)(url\(\s*["']?\s*)//(?=[a-z0-9])""")
    private val SCHEMELESS_SRCSET_ITEM =
        Regex("""(,\s*)//(?=[a-z0-9][a-z0-9.-]*\.[a-z]{2,}[:/])""", RegexOption.IGNORE_CASE)
}
