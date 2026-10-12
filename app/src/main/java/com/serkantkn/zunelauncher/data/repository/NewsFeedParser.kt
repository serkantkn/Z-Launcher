package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.NewsRelated
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/**
 * Reads a news feed - RSS 2.0, RSS 1.0 (RDF) or Atom, which between them are every feed a news
 * site publishes - into stories.
 *
 * Feeds are written by hand as often as by a library, so the reading is forgiving: the parser
 * runs relaxed (an undeclared `&nbsp;` does not end the feed), elements are matched by their
 * written name whatever namespace they claim, and a picture is looked for in every place sites
 * put one - media tags, an enclosure, a bare `<image>`, or the first `<img>` in the text.
 */
internal object NewsFeedParser {

    data class Feed(val title: String, val items: List<NewsItem>)

    private const val RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

    /**
     * @param fallbackSource what to call the stories' source when the feed does not say
     * @param googleNews the feed is Google News's: titles end in " - Paper", and the text is a
     * list of the same story as other papers tell it rather than a summary
     */
    fun parse(input: InputStream, fallbackSource: String, googleNews: Boolean = false): Feed {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        runCatching { parser.setFeature(RELAXED, true) }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(withoutByteOrderMark(input), null)
        // HTML's entities turn up in feeds undeclared; relaxed, the parser would drop them - and
        // with them the space a "&nbsp;" stood for.
        NAMED_ENTITIES.forEach { (name, text) ->
            if (name !in XML_ENTITIES) runCatching { parser.defineEntityReplacementText(name, text) }
        }

        var feedTitle = ""
        val items = mutableListOf<NewsItem>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "item", "entry" -> readItem(parser, googleNews)?.let { items += it }
                    "title" -> if (feedTitle.isEmpty()) feedTitle = plainText(readInner(parser))
                }
            }
            event = parser.next()
        }
        val source = feedTitle.ifBlank { fallbackSource }
        return Feed(
            title = feedTitle,
            items = items.map { if (it.source.isBlank()) it.copy(source = source) else it }
        )
    }

    private class Draft {
        var title = ""
        var link = ""
        var guid = ""
        var date = ""
        var description = ""
        var content = ""
        var source = ""
        val images = mutableListOf<String>()
    }

    private fun readItem(parser: XmlPullParser, googleNews: Boolean): NewsItem? {
        val itemDepth = parser.depth
        val draft = Draft()
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == itemDepth) && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase(Locale.ROOT)
                when (name) {
                    "title" -> draft.title = readInner(parser)
                    "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        when {
                            href == null -> readInner(parser).trim().takeIf { it.isNotEmpty() }?.let { draft.link = it }
                            rel == "enclosure" && type.startsWith("image") -> draft.images += href
                            rel == null || rel == "alternate" -> if (draft.link.isEmpty()) draft.link = href
                        }
                    }
                    "guid", "id" -> draft.guid = readInner(parser).trim()
                    "pubdate", "published", "dc:date", "updated" ->
                        readInner(parser).trim().takeIf { draft.date.isEmpty() || name != "updated" }?.let { draft.date = it }
                    "description", "summary" -> draft.description = readInner(parser)
                    "content:encoded", "content" -> draft.content = readInner(parser)
                    "source" -> draft.source = plainText(readInner(parser))
                    "media:content", "media:thumbnail", "enclosure" -> {
                        val url = parser.getAttributeValue(null, "url")
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val medium = parser.getAttributeValue(null, "medium").orEmpty()
                        val isImage = name == "media:thumbnail" || medium == "image" || type.startsWith("image") ||
                            (medium.isEmpty() && type.isEmpty() && url != null && looksLikeImage(url))
                        if (url != null && isImage) draft.images += url
                    }
                    "image", "imageurl", "media:image" -> {
                        val url = parser.getAttributeValue(null, "url") ?: parser.getAttributeValue(null, "href")
                            ?: readInner(parser).trim()
                        if (url.startsWith("http")) draft.images += url
                    }
                }
            }
            event = parser.next()
        }
        return draft.toItem(googleNews)
    }

    private fun Draft.toItem(googleNews: Boolean): NewsItem? {
        val url = link.ifBlank { guid.takeIf { it.startsWith("http") }.orEmpty() }.trim()
        var headline = plainText(title)
        if (headline.isBlank() || url.isBlank()) return null
        var paper = source
        if (googleNews) {
            // "Headline - Paper": the paper is said again in <source>, so it comes off the title.
            val dash = headline.lastIndexOf(" - ")
            if (dash > 0) {
                val suffix = headline.substring(dash + 3).trim()
                if (paper.isBlank()) paper = suffix
                if (suffix.equals(paper, ignoreCase = true)) headline = headline.substring(0, dash).trim()
            }
        }
        val related = if (googleNews) {
            googleRelated(description).filterNot { it.link == url || it.title.equals(headline, ignoreCase = true) }
        } else emptyList()
        val text = content.ifBlank { description }
        val image = images.firstOrNull { it.isNotBlank() }
            ?: firstImageIn(content)
            ?: firstImageIn(description)
        return NewsItem(
            title = headline,
            link = url,
            source = paper,
            publishedAt = parseDate(date),
            // Google's text is the list of other papers, not something to read as a summary.
            summary = if (googleNews) "" else summaryOf(description.ifBlank { text }),
            imageUrl = image?.let { sharper(decodeEntities(it).trim()) }?.takeIf { it.startsWith("http") },
            related = related
        )
    }

    /** Everything inside the current element as text, with the `<img>` tags of XHTML content kept. */
    private fun readInner(parser: XmlPullParser): String {
        val depth = parser.depth
        val text = StringBuilder()
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth) && event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> text.append(parser.text)
                XmlPullParser.START_TAG -> if (parser.name.equals("img", ignoreCase = true)) {
                    parser.getAttributeValue(null, "src")?.let { text.append("<img src=\"").append(it).append("\">") }
                }
            }
            event = parser.next()
        }
        return text.toString()
    }

    private fun withoutByteOrderMark(input: InputStream): InputStream {
        val buffered = input.buffered()
        buffered.mark(3)
        val head = ByteArray(3)
        val read = buffered.read(head)
        val bom = read == 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()
        if (!bom) buffered.reset()
        return buffered
    }

    // --- Text -----------------------------------------------------------------------------------

    private val IMG_SRC = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val DROPPED_BLOCKS = Regex("""<(script|style)[^>]*>.*?</\1>|<!--.*?-->""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BREAKS = Regex("""<br\s*/?>|</p>|</div>|</li>""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("[ \\t\\u00A0]+")
    private val BLANK_LINES = Regex("\\s*\\n\\s*")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val GOOGLE_STORY = Regex(
        """<a[^>]+href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>(?:\s|&nbsp;| )*(?:<font[^>]*>(.*?)</font>)?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "middot" to "·", "bull" to "•",
        "copy" to "©", "reg" to "®", "euro" to "€", "deg" to "°"
    )
    private val NAMED_ENTITY = Regex("&([a-zA-Z]+);")
    private val XML_ENTITIES = setOf("amp", "lt", "gt", "quot", "apos")

    /** HTML as the words in it: tags gone, entities turned into their letters, space tidied. */
    fun plainText(html: String): String {
        if (html.isBlank()) return ""
        val text = html
            .replace(DROPPED_BLOCKS, " ")
            .replace(BREAKS, "\n")
            .replace(TAG, " ")
        return decodeEntities(text)
            .replace(SPACES, " ")
            .replace(BLANK_LINES, "\n")
            .trim()
    }

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(NUMERIC_ENTITY) { match ->
                val code = runCatching {
                    match.groupValues[2].toInt(if (match.groupValues[1].isNotEmpty()) 16 else 10)
                }.getOrNull()
                if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
            }
            .replace(NAMED_ENTITY) { match -> NAMED_ENTITIES[match.groupValues[1].lowercase(Locale.ROOT)] ?: match.value }
    }

    /** A feed's text cut to what a summary is: a few sentences, without the "[…]" some leave. */
    fun summaryOf(html: String): String {
        val text = plainText(html)
            .removeSuffix("[…]").removeSuffix("[...]").removeSuffix("Continue reading...")
            .trim()
        return if (text.length <= SUMMARY_MAX) text else text.take(SUMMARY_MAX).substringBeforeLast(' ') + "…"
    }

    private const val SUMMARY_MAX = 700

    fun firstImageIn(html: String): String? = IMG_SRC.find(html)?.groupValues?.get(1)

    private val BBC_SIZE = Regex("""(ichef\.bbci\.co\.uk/(?:ace/)?(?:standard|ws)/)\d+/""")

    /**
     * A larger copy of a picture where the site's address says how wide it is: the BBC's feeds
     * give 240-pixel thumbnails, too small to stand across the top of the page.
     */
    fun sharper(url: String): String = url.replace(BBC_SIZE) { it.groupValues[1] + "800/" }

    private fun looksLikeImage(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")
    }

    /** The same story as other papers tell it, from the list Google News puts in an item's text. */
    fun googleRelated(html: String): List<NewsRelated> =
        GOOGLE_STORY.findAll(html).mapNotNull { match ->
            val title = plainText(match.groupValues[2])
            val link = decodeEntities(match.groupValues[1])
            if (title.isBlank() || !link.startsWith("http")) null
            else NewsRelated(title = title, link = link, source = plainText(match.groupValues[3]))
        }.toList()

    // --- Dates ----------------------------------------------------------------------------------

    private val RFC822_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z",
        "EEE, dd MMM yyyy HH:mm:ss"
    )

    /** A feed's date as epoch milliseconds: RFC 822 for RSS, ISO 8601 for Atom; 0 when unreadable. */
    fun parseDate(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0L
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        for (pattern in RFC822_PATTERNS) {
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }
            try {
                return format.parse(text)?.time ?: continue
            } catch (_: ParseException) {
            }
        }
        // "2026-10-12 21:03:07" and the like: no zone, read as UTC.
        runCatching {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(text)?.time ?: 0L
        }
        return 0L
    }
}
