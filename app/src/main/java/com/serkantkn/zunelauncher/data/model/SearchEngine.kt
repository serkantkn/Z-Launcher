package com.serkantkn.zunelauncher.data.model

import java.net.URLEncoder

/**
 * Where a typed search goes.
 *
 * Google used to be written into three places in the browser with no way to change it, which also
 * meant every keystroke went to Google's suggestion service whether or not the user wanted it to.
 * Each engine here answers suggestions in the same OpenSearch shape — ["query", ["a", "b", …]] —
 * so one parser serves all of them.
 */
enum class SearchEngine(
    val label: String,
    private val searchTemplate: String,
    private val suggestTemplate: String?
) {
    GOOGLE(
        "Google",
        "https://www.google.com/search?q=%s",
        "https://suggestqueries.google.com/complete/search?client=firefox&oe=utf8&q=%s"
    ),
    BING(
        "Bing",
        "https://www.bing.com/search?q=%s",
        "https://api.bing.com/osjson.aspx?query=%s"
    ),
    DUCKDUCKGO(
        "DuckDuckGo",
        "https://duckduckgo.com/?q=%s",
        "https://duckduckgo.com/ac/?type=list&q=%s"
    ),
    YANDEX(
        "Yandex",
        "https://yandex.com.tr/search/?text=%s",
        "https://suggest.yandex.com/suggest-ff.cgi?part=%s"
    );

    fun searchUrl(query: String): String = searchTemplate.format(encode(query))

    fun suggestUrl(query: String): String? = suggestTemplate?.format(encode(query))

    /** "I'm feeling lucky" is a Google trick; no other engine has anything like it. */
    val hasLuckySearch: Boolean get() = this == GOOGLE

    fun luckyUrl(query: String): String =
        "https://www.google.com/search?q=${encode(query)}&btnI=I"

    private fun encode(query: String): String = URLEncoder.encode(query, "UTF-8")

    companion object {
        val DEFAULT = GOOGLE

        fun fromName(name: String?): SearchEngine =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
