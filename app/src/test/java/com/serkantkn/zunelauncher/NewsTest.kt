package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.NewsRelated
import com.serkantkn.zunelauncher.data.model.NewsSection
import com.serkantkn.zunelauncher.data.model.NewsTopic
import com.serkantkn.zunelauncher.data.repository.GoogleSignature
import com.serkantkn.zunelauncher.data.repository.NewsFeedParser
import com.serkantkn.zunelauncher.data.repository.NewsPresets
import com.serkantkn.zunelauncher.data.repository.NewsRepository
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class NewsTest {

    private fun parse(xml: String, google: Boolean = false, bom: Boolean = false): NewsFeedParser.Feed {
        val bytes = (if (bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) else ByteArray(0)) + xml.toByteArray()
        return NewsFeedParser.parse(bytes.inputStream(), "fallback", google)
    }

    @Test
    fun rss2WithMediaTagsAndAnUndeclaredEntity() {
        val feed = parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Örnek Haber</title>
                <image><title>logo</title><url>https://example.com/logo.png</url></image>
                <item>
                  <title>Başlık &amp; ikinci&nbsp;kısım</title>
                  <link>https://example.com/a</link>
                  <description><![CDATA[<p>İlk <b>paragraf</b>.</p><p>İkinci.</p>]]></description>
                  <pubDate>Mon, 12 Oct 2026 23:44:00 +0300</pubDate>
                  <media:content url="https://example.com/a.jpg" medium="image" width="800"/>
                </item>
                <item><title></title><link>https://example.com/empty</link></item>
              </channel>
            </rss>""",
            bom = true
        )
        assertEquals("Örnek Haber", feed.title)
        assertEquals(1, feed.items.size)
        val item = feed.items.single()
        assertEquals("Başlık & ikinci kısım", item.title)
        assertEquals("https://example.com/a", item.link)
        assertEquals("Örnek Haber", item.source)
        assertEquals("İlk paragraf .\nİkinci.", item.summary)
        assertEquals("https://example.com/a.jpg", item.imageUrl)
        assertEquals(1791837840000L, item.publishedAt)
    }

    @Test
    fun picturesAreFoundWhereverTheSiteKeepsThem() {
        val feed = parse(
            """<rss><channel><title>t</title>
              <item><title>a</title><link>https://x/a</link><enclosure url="https://x/a.jpg" type="image/jpeg"/></item>
              <item><title>b</title><link>https://x/b</link><image>https://x/b.jpg</image></item>
              <item><title>c</title><link>https://x/c</link><imageUrl>https://x/c.jpg</imageUrl></item>
              <item><title>d</title><link>https://x/d</link><description>&lt;img src="https://x/d.jpg?w=1&amp;h=2"&gt; text</description></item>
              <item><title>e</title><link>https://x/e</link><media:thumbnail url="https://ichef.bbci.co.uk/ace/ws/240/p/e.jpg"/></item>
              <item><title>f</title><link>https://x/f</link><enclosure url="https://x/f.mp3" type="audio/mpeg"/></item>
            </channel></rss>"""
        )
        assertEquals(
            listOf("https://x/a.jpg", "https://x/b.jpg", "https://x/c.jpg", "https://x/d.jpg?w=1&h=2", "https://ichef.bbci.co.uk/ace/ws/800/p/e.jpg", null),
            feed.items.map { it.imageUrl }
        )
    }

    @Test
    fun atomEntries() {
        val feed = parse(
            """<?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title type="text">The Site</title>
              <entry>
                <title type="html"><![CDATA[An Atom story]]></title>
                <link rel="alternate" type="text/html" href="https://site/story"/>
                <link rel="enclosure" type="image/png" href="https://site/pic.png"/>
                <published>2026-10-12T13:00:00-04:00</published>
                <updated>2026-10-12T07:13:10-04:00</updated>
                <summary type="html"><![CDATA[What it is about [&#8230;]]]></summary>
              </entry>
            </feed>"""
        )
        val entry = feed.items.single()
        assertEquals("An Atom story", entry.title)
        assertEquals("https://site/story", entry.link)
        assertEquals("https://site/pic.png", entry.imageUrl)
        assertEquals("What it is about", entry.summary)
        // published, not the later-written updated
        assertEquals(1791824400000L, entry.publishedAt)
    }

    @Test
    fun rss1Rdf() {
        val feed = parse(
            """<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel><title>DW</title><items><rdf:Seq><rdf:li rdf:resource="https://dw/a"/></rdf:Seq></items></channel>
              <item rdf:about="https://dw/a"><title>RDF haberi</title><link>https://dw/a</link><dc:date>2026-10-12T19:58:00Z</dc:date></item>
            </rdf:RDF>"""
        )
        assertEquals("DW", feed.title)
        assertEquals("RDF haberi", feed.items.single().title)
        assertEquals(1791835080000L, feed.items.single().publishedAt)
    }

    @Test
    fun googleNewsTitlesLoseThePaperAndKeepTheOtherTellings() {
        val description = """&lt;ol&gt;&lt;li&gt;&lt;a href="https://news.google.com/rss/articles/A" target="_blank"&gt;Aynı haber&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;Milliyet&lt;/font&gt;&lt;/li&gt;&lt;li&gt;&lt;a href="https://news.google.com/rss/articles/B" target="_blank"&gt;Başka anlatım&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;Hürriyet&lt;/font&gt;&lt;/li&gt;&lt;/ol&gt;"""
        val feed = parse(
            """<rss><channel><title>Google Haberler</title>
              <item><title>Aynı haber - Milliyet</title><link>https://news.google.com/rss/articles/A</link>
              <pubDate>Mon, 12 Oct 2026 16:27:28 GMT</pubDate><description>$description</description>
              <source url="https://www.milliyet.com.tr">Milliyet</source></item>
            </channel></rss>""",
            google = true
        )
        val item = feed.items.single()
        assertEquals("Aynı haber", item.title)
        assertEquals("Milliyet", item.source)
        assertEquals("", item.summary)
        assertEquals(listOf(NewsRelated("Başka anlatım", "https://news.google.com/rss/articles/B", "Hürriyet")), item.related)
        assertEquals(1791822448000L, item.publishedAt)
    }

    @Test
    fun aDashThatIsNotThePaperStays() {
        val feed = parse(
            """<rss><channel><item><title>2-1 - final score - BBC</title><link>https://g/1</link><source>Reuters</source></item></channel></rss>""",
            google = true
        )
        assertEquals("2-1 - final score - BBC", feed.items.single().title)
    }

    @Test
    fun datesInTheFormsFeedsWriteThem() {
        assertEquals(1791837840000L, NewsFeedParser.parseDate("Mon, 12 Oct 2026 23:44:00 +0300"))
        assertEquals(1791822448000L, NewsFeedParser.parseDate("Mon, 12 Oct 2026 16:27:28 GMT"))
        assertEquals(1791822448000L, NewsFeedParser.parseDate("12 Oct 2026 16:27:28 +0000"))
        assertEquals(1791824400000L, NewsFeedParser.parseDate("2026-10-12T13:00:00-04:00"))
        assertEquals(1791822448000L, NewsFeedParser.parseDate("2026-10-12 16:27:28"))
        assertEquals(0L, NewsFeedParser.parseDate("someday"))
        assertEquals(0L, NewsFeedParser.parseDate(""))
    }

    @Test
    fun htmlBecomesText() {
        assertEquals("a & b — “c” é", NewsFeedParser.plainText("<p>a &amp; b &mdash; &ldquo;c&rdquo; &#233;</p><script>x()</script><!-- hidden -->"))
        assertEquals("", NewsFeedParser.plainText("   "))
        val long = "word ".repeat(400)
        val summary = NewsFeedParser.summaryOf(long)
        assertTrue(summary.length <= 701)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun aPagesShareCardIsReadWhicheverWayRoundItsAttributesAre() {
        val html = """<head><meta content="https://p/og.jpg" property="og:image"><meta name='description' content='Sayfa &amp; özet'></head>"""
        assertEquals("https://p/og.jpg", NewsRepository.metaContent(html, "og:image"))
        assertEquals("Sayfa & özet", NewsRepository.metaContent(html, "description"))
        assertNull(NewsRepository.metaContent(html, "og:description"))
    }

    @Test
    fun googleEditionFollowsThePhonesLanguageAndCountry() {
        assertEquals(
            "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr",
            NewsRepository.googleNewsUrl(NewsTopic.TOP, Locale("tr", "TR"))
        )
        assertEquals(
            "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en&gl=US&ceid=US:en",
            NewsRepository.googleNewsUrl(NewsTopic.TECHNOLOGY, Locale("en"))
        )
        assertTrue(NewsRepository.isGoogleLink("https://news.google.com/rss/articles/X"))
        assertFalse(NewsRepository.isGoogleLink("https://www.bbc.com/turkce/a"))
    }

    @Test
    fun presetsAreUniqueAndEveryDefaultExists() {
        assertEquals(NewsPresets.all.size, NewsPresets.all.map { it.id }.toSet().size)
        val ids = NewsPresets.all.map { it.id }.toSet()
        listOf("tr", "en", "de").forEach { language ->
            assertTrue(NewsPresets.defaultIdsFor(language).isNotEmpty())
            assertTrue(ids.containsAll(NewsPresets.defaultIdsFor(language)))
        }
        assertEquals("tr", NewsPresets.forLanguage("tr").first().language)
        assertTrue(NewsPresets.all.all { it.url.startsWith("https://") })
    }

    @Test
    fun sectionsSurviveTheCache() {
        val section = NewsSection(
            key = NewsTopic.WORLD.sectionKey,
            fetchedAt = 42L,
            items = listOf(
                NewsItem("t", "https://l", "s", 7L, "sum", null, listOf(NewsRelated("r", "https://r", "p"))),
                NewsItem("u", "https://m", "s", 8L, imageUrl = "https://i", pageUrl = "https://publisher/u")
            )
        )
        assertEquals(section, NewsSection.fromJson(section.toJson()))
        assertEquals("topic:WORLD", section.key)
        assertEquals(NewsTopic.TOP, NewsTopic.DEFAULT.first())
    }

    @Test
    fun aStoryIsReadFromThePublisherOnceItsAddressIsKnown() {
        val story = NewsItem("t", "https://news.google.com/rss/articles/CBMi", "s", 1L)
        assertEquals(story.link, story.readUrl)
        assertEquals("https://publisher/t", story.copy(pageUrl = "https://publisher/t").readUrl)
        assertEquals(story.link, story.copy(pageUrl = "https://publisher/t").id)
    }

    @Test
    fun googlesPageGivesItsSignature() {
        val page = """<c-wiz><div jscontroller="aLI87" data-n-a-id="CBMi" data-n-a-ts="1791965791" data-n-a-sg="AbIaSL85NsnbxUWYirQNVyxSDxPJ"></div>"""
        assertEquals(GoogleSignature(1791965791L, "AbIaSL85NsnbxUWYirQNVyxSDxPJ"), NewsRepository.googleSignature(page))
        assertNull(NewsRepository.googleSignature("<html>consent</html>"))
    }

    @Test
    fun theRequestForThePublisherIsTheOneGooglesPageMakes() {
        val request = JSONArray(NewsRepository.googleDecodeRequest("CBMi", GoogleSignature(17L, "sig")))
        val call = request.getJSONArray(0).getJSONArray(0)
        assertEquals("Fbv4je", call.getString(0))
        assertEquals("generic", call.getString(3))
        val inner = JSONArray(call.getString(1))
        assertEquals("garturlreq", inner.getString(0))
        assertEquals("CBMi", inner.getString(2))
        assertEquals(17L, inner.getLong(3))
        assertEquals("sig", inner.getString(4))
    }

    @Test
    fun theAnswerGivesThePublishersAddress() {
        val answer = ")]}'\n\n" +
            """[["wrb.fr","Fbv4je","[\"garturlres\",\"https://www.ntv.com.tr/turkiye/ankarada-taksici-1746209\",1]",null,null,null,"generic"],["di",13],["af.httprm",12,"-58",84]]"""
        assertEquals("https://www.ntv.com.tr/turkiye/ankarada-taksici-1746209", NewsRepository.googleDecodedLink(answer))
        assertNull(NewsRepository.googleDecodedLink(")]}'\n\n[[\"er\",null,null,null,null,400]]"))
        assertNull(NewsRepository.googleDecodedLink("<html>"))
    }
}
