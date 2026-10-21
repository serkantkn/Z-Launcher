package com.serkantkn.zeta

import com.serkantkn.zeta.data.model.BrowserHistory
import com.serkantkn.zeta.data.model.ReadingItem
import com.serkantkn.zeta.data.model.frequentSites
import com.serkantkn.zeta.ui.screens.browser.ReadingCopy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserStartPageTest {

    @Test
    fun theMostVisitedSitesAddUpEveryPageOfASite() {
        val history = listOf(
            BrowserHistory("Haber - BBC News Türkçe", "https://www.bbc.com/turkce/a", timestamp = 50, visits = 2),
            BrowserHistory("BBC News Türkçe - Haberler", "https://www.bbc.com/", timestamp = 40, visits = 1),
            BrowserHistory("Hürriyet", "https://www.hurriyet.com.tr/", timestamp = 60, visits = 2),
            BrowserHistory("ara", "https://www.google.com/search?q=x", timestamp = 70, visits = 9),
            BrowserHistory("çeviri", "https://www-bbc-com.translate.goog/news", timestamp = 70, visits = 9),
            BrowserHistory("yerel", "file:///sdcard/a.html", timestamp = 70, visits = 9)
        )
        val sites = frequentSites(history)
        assertEquals(listOf("bbc.com", "hurriyet.com.tr"), sites.map { it.host })
        assertEquals(3, sites[0].visits)
        assertEquals("BBC News Türkçe", sites[0].title)
        assertEquals("https://www.bbc.com/", sites[0].url)
    }

    @Test
    fun aVisitCountSurvivesTheStoreAndOldEntriesCountOnce() {
        val entry = BrowserHistory("t", "https://a.com/", timestamp = 5, visits = 4)
        assertEquals(entry, BrowserHistory.fromJson(entry.toJson()))
        assertEquals(1, BrowserHistory.fromJson(JSONObject("""{"title":"t","url":"https://a.com/","timestamp":5}""")).visits)
    }

    @Test
    fun aReadingItemSurvivesTheStore() {
        val item = ReadingItem("https://a.com/x", "Başlık", "a.com", "https://a.com/i.jpg", "özet", 9L, read = true, offline = true)
        assertEquals(item, ReadingItem.fromJson(item.toJson()))
    }

    @Test
    fun theKeptCopyIsReadOutOfWhatThePageHandsBack() {
        // evaluateJavascript hands back the script's string as a JSON string literal.
        val inner = JSONObject().put("title", "T").put("html", "<p>metin</p>").put("image", "https://a/i.jpg").put("excerpt", "e").toString()
        val result = org.json.JSONArray().put(inner).toString().removePrefix("[").removeSuffix("]")
        val copy = ReadingCopy.parse(result)!!
        assertEquals("T", copy.title)
        assertEquals("<p>metin</p>", copy.html)
        assertEquals("https://a/i.jpg", copy.image)
        assertNull(ReadingCopy.parse("\"nothing\""))
        assertNull(ReadingCopy.parse("null"))
    }
}
