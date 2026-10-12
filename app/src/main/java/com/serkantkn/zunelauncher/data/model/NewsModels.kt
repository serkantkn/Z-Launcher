package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * The news hub's headings that come from Google News: its front page and its topic sections.
 * [googleSection] is the section's name in Google's feed addresses; the front page has none.
 */
enum class NewsTopic(val googleSection: String?, @StringRes val titleRes: Int) {
    TOP(null, R.string.news_topic_top),
    NATION("NATION", R.string.news_topic_nation),
    WORLD("WORLD", R.string.news_topic_world),
    BUSINESS("BUSINESS", R.string.news_topic_business),
    TECHNOLOGY("TECHNOLOGY", R.string.news_topic_technology),
    SCIENCE("SCIENCE", R.string.news_topic_science),
    HEALTH("HEALTH", R.string.news_topic_health),
    SPORTS("SPORTS", R.string.news_topic_sports),
    ENTERTAINMENT("ENTERTAINMENT", R.string.news_topic_entertainment);

    /** Where this topic's stories are kept in the cache. */
    val sectionKey: String get() = "topic:$name"

    companion object {
        /** The headings a new hub starts with; the rest are a tick away in its settings. */
        val DEFAULT = listOf(TOP, NATION, WORLD, BUSINESS, TECHNOLOGY, SPORTS)
    }
}

/**
 * A site's RSS or Atom feed. [preset] feeds are the ones the hub offers ready-made; the user's own
 * are added by address. [language] says which presets to offer first.
 */
data class NewsSource(
    val id: String,
    val name: String,
    val url: String,
    val language: String = "",
    val preset: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("language", language)

    companion object {
        fun fromJson(json: JSONObject) = NewsSource(
            id = json.getString("id"),
            name = json.getString("name"),
            url = json.getString("url"),
            language = json.optString("language", "")
        )
    }
}

/** Another paper's telling of the same story, as Google News groups them. */
data class NewsRelated(val title: String, val link: String, val source: String) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("link", link).put("source", source)

    companion object {
        fun fromJson(json: JSONObject) = NewsRelated(
            title = json.getString("title"),
            link = json.getString("link"),
            source = json.optString("source", "")
        )
    }
}

/**
 * One story: its headline, where it came from and when, what the feed says of it, and a picture
 * when the feed has one. [link] is the story on its own site, where reading it goes on.
 */
data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: Long,
    val summary: String = "",
    val imageUrl: String? = null,
    val related: List<NewsRelated> = emptyList()
) {
    val id: String get() = link

    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("link", link)
        .put("source", source)
        .put("publishedAt", publishedAt)
        .put("summary", summary)
        .put("imageUrl", imageUrl ?: JSONObject.NULL)
        .put("related", JSONArray().apply { related.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject) = NewsItem(
            title = json.getString("title"),
            link = json.getString("link"),
            source = json.optString("source", ""),
            publishedAt = json.optLong("publishedAt", 0L),
            summary = json.optString("summary", ""),
            imageUrl = json.optStringOrNull("imageUrl"),
            related = json.optJSONArray("related")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    runCatching { NewsRelated.fromJson(array.getJSONObject(i)) }.getOrNull()
                }
            }.orEmpty()
        )
    }
}

/** A heading's stories as last fetched, and when. */
data class NewsSection(val key: String, val items: List<NewsItem>, val fetchedAt: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("fetchedAt", fetchedAt)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })

    companion object {
        /** The stories from every feed the user follows, newest first, under one heading. */
        const val SOURCES_KEY = "sources"

        fun fromJson(json: JSONObject) = NewsSection(
            key = json.getString("key"),
            fetchedAt = json.optLong("fetchedAt", 0L),
            items = json.optJSONArray("items")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    runCatching { NewsItem.fromJson(array.getJSONObject(i)) }.getOrNull()
                }
            }.orEmpty()
        )
    }
}
