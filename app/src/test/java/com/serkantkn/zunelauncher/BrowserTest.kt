package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.BrowserFavorite
import com.serkantkn.zunelauncher.data.model.BrowserHistory
import com.serkantkn.zunelauncher.data.model.SearchEngine
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.ui.screens.browser.groupHistoryByDay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The internet hub's rules that hold without a screen: searching, history and pinned sites. */
class BrowserTest {

    // ── Where a search goes ───────────────────────────────────────────────────────────────────

    @Test
    fun everyEngineCanBeSearchedAndSuggestsSomething() {
        SearchEngine.entries.forEach { engine ->
            assertTrue(
                "${engine.label} has no search address",
                engine.searchUrl("kedi").startsWith("https://")
            )
            assertTrue(
                "${engine.label} has no suggestion address",
                engine.suggestUrl("kedi").orEmpty().startsWith("https://")
            )
        }
    }

    @Test
    fun aSearchWithSpacesAndTurkishLettersSurvivesTheTrip() {
        val url = SearchEngine.GOOGLE.searchUrl("çilekli pasta tarifi")

        assertTrue(url.contains("%C3%A7ilekli+pasta+tarifi"))
        assertFalse("a raw space would break the address", url.contains(" "))
    }

    @Test
    fun onlyGoogleHasFeelingLucky() {
        assertTrue(SearchEngine.GOOGLE.hasLuckySearch)
        assertFalse(SearchEngine.BING.hasLuckySearch)
        assertFalse(SearchEngine.DUCKDUCKGO.hasLuckySearch)
        assertFalse(SearchEngine.YANDEX.hasLuckySearch)
    }

    @Test
    fun anEngineThatIsNoLongerKnownFallsBackRatherThanBreaking() {
        assertEquals(SearchEngine.DEFAULT, SearchEngine.fromName(null))
        assertEquals(SearchEngine.DEFAULT, SearchEngine.fromName("ASKJEEVES"))
        assertEquals(SearchEngine.BING, SearchEngine.fromName("BING"))
    }

    // ── The history, under day headings ───────────────────────────────────────────────────────

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 3, 14, 0, 0)
    }.timeInMillis

    private fun daysAgo(days: Int, hour: Int = 10): Long = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, hour)
    }.timeInMillis

    private fun visit(url: String, at: Long) = BrowserHistory("title of $url", url, at)

    @Test
    fun pagesSeenTodayShareOneHeading() {
        val groups = groupHistoryByDay(
            listOf(visit("a", daysAgo(0, 13)), visit("b", daysAgo(0, 9))),
            "today",
            "yesterday",
            now
        )

        assertEquals(1, groups.size)
        assertEquals("today", groups.first().first)
        assertEquals(2, groups.first().second.size)
    }

    @Test
    fun eachDayGetsItsOwnHeadingInTheOrderTheyWereVisited() {
        val groups = groupHistoryByDay(
            listOf(visit("a", daysAgo(0)), visit("b", daysAgo(1)), visit("c", daysAgo(5))),
            "today",
            "yesterday",
            now
        )

        assertEquals(listOf("today", "yesterday"), groups.take(2).map { it.first })
        assertEquals(3, groups.size)
        assertEquals("the oldest day is last", listOf("c"), groups.last().second.map { it.url })
    }

    @Test
    fun aDayThatComesBackLaterGetsAHeadingOfItsOwn() {
        // The history is kept newest first, so this should not happen; if it ever does, the run
        // is what is grouped, not the day, and nothing is silently merged out of order.
        val groups = groupHistoryByDay(
            listOf(visit("a", daysAgo(0)), visit("b", daysAgo(1)), visit("c", daysAgo(0))),
            "today",
            "yesterday",
            now
        )

        assertEquals(3, groups.size)
    }

    @Test
    fun anEmptyHistoryHasNoHeadings() {
        assertEquals(emptyList<Any>(), groupHistoryByDay(emptyList(), "today", "yesterday", now))
    }

    // ── Favourites and their folders ──────────────────────────────────────────────────────────

    @Test
    fun aFavoriteInAFolderSurvivesBeingWrittenAndReadBack() {
        val favorite = BrowserFavorite("Ekşi", "https://eksisozluk.com", "okuma")

        val restored = BrowserFavorite.fromJson(JSONObject(favorite.toJson().toString()))

        assertEquals(favorite, restored)
    }

    @Test
    fun aFavoriteWithNoFolderStoresNoneAndReadsBackLoose() {
        val stored = BrowserFavorite("Google", "https://www.google.com").toJson()

        assertFalse(stored.has("folder"))
        assertEquals("", BrowserFavorite.fromJson(JSONObject(stored.toString())).folder)
    }

    // ── A site pinned to Start ────────────────────────────────────────────────────────────────

    @Test
    fun aPinnedSiteKeepsItsAddressAndItsName() {
        val tile = StartTileItem.fromWeb("https://github.com/", "GitHub")

        assertTrue(tile.isWeb)
        assertEquals("https://github.com/", tile.webUrl)
        assertEquals("GitHub", tile.name)
    }

    @Test
    fun aPinnedSiteSurvivesBeingWrittenAndReadBack() {
        val tile = StartTileItem.fromWeb("https://eksisozluk.com", "Ekşi Sözlük", span = 4)

        val restored = StartTileItem.fromJson(JSONObject(tile.toJson().toString()))

        assertEquals(tile, restored)
        assertEquals(4, restored.span)
    }

    @Test
    fun anAddressWithColonsInItIsStillReadBackWhole() {
        val tile = StartTileItem.fromWeb("https://localhost:8080/a:b", "Local")

        assertEquals("https://localhost:8080/a:b", tile.webUrl)
    }

    @Test
    fun otherKindsOfTileAreNotWebsites() {
        assertFalse(StartTileItem.fromApp("com.whatsapp").isWeb)
        assertNull(StartTileItem.fromApp("com.whatsapp").webUrl)
    }
}
