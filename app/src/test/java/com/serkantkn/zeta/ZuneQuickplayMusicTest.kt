package com.serkantkn.zeta

import com.serkantkn.zeta.data.model.QUICKPLAY_PINS_LIMIT
import com.serkantkn.zeta.data.model.QuickplayPin
import com.serkantkn.zeta.data.model.newestGroups
import com.serkantkn.zeta.data.model.togglePin
import com.serkantkn.zeta.ui.screens.music.groupByGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Quickplay's pins and "new", and the Zune music menu's genres. */
class ZuneQuickplayMusicTest {

    private fun pin(kind: QuickplayPin.Kind, key: String) = QuickplayPin(kind, key, "title $key")

    @Test
    fun `pinning twice takes the pin off again`() {
        val album = pin(QuickplayPin.Kind.ALBUM, "7")
        val once = togglePin(emptyList(), album)
        assertEquals(listOf(album), once)
        assertTrue(togglePin(once, album.copy(title = "renamed")).isEmpty())
    }

    @Test
    fun `the same key under another kind is another pin`() {
        val list = togglePin(togglePin(emptyList(), pin(QuickplayPin.Kind.NOTE, "1")), pin(QuickplayPin.Kind.ALBUM, "1"))
        assertEquals(2, list.size)
        assertEquals(QuickplayPin.Kind.ALBUM, list.first().kind)
    }

    @Test
    fun `the oldest pin falls off past the limit`() {
        var list = emptyList<QuickplayPin>()
        repeat(QUICKPLAY_PINS_LIMIT + 3) { list = togglePin(list, pin(QuickplayPin.Kind.APP, "app$it")) }
        assertEquals(QUICKPLAY_PINS_LIMIT, list.size)
        assertEquals("app${QUICKPLAY_PINS_LIMIT + 2}", list.first().key)
        assertTrue(list.none { it.key == "app0" })
    }

    @Test
    fun `pins survive the preference and rubbish does not`() {
        val pins = listOf(
            QuickplayPin(QuickplayPin.Kind.PHOTO, "content://media/1", "IMG_1.jpg", art = "content://media/1"),
            QuickplayPin(QuickplayPin.Kind.NOTE, "n-2", "alışveriş", subtitle = "#FF8800")
        )
        assertEquals(pins, QuickplayPin.listFromJson(QuickplayPin.listToJson(pins)))
        assertTrue(QuickplayPin.listFromJson("{").isEmpty())
        assertNull(QuickplayPin.fromJson(org.json.JSONObject("""{"k":"SONG","id":"1"}""")))
        assertNull(QuickplayPin.fromJson(org.json.JSONObject("""{"k":"APP"}""")))
    }

    @Test
    fun `new records are the newest first, within the month, without the undated`() {
        val day = 24L * 60 * 60
        val now = 100 * day
        // (album, added)
        val tracks = listOf(
            1L to now - 2 * day, 1L to now - 40 * day,
            2L to now - 1 * day,
            3L to now - 45 * day,
            0L to now,
            4L to 0L
        )
        val groups = newestGroups(tracks, now, 30 * day, 4, { it.first }, { it.second })
        assertEquals(listOf(2L, 1L), groups.map { it.first })
        assertEquals(1, groups.last().second.size)
        assertEquals(listOf(2L), newestGroups(tracks, now, 30 * day, 1, { it.first }, { it.second }).map { it.first })
    }

    @Test
    fun `genres gather whatever their case, alphabetical, the untagged left out`() {
        val tr = Locale.forLanguageTag("tr")
        // (title, genre)
        val songs = listOf("Zeybek" to "Halk", "Ağıt" to "halk", "Cambaz" to "Rock", "Sessiz" to "", "Çay" to "Rock")
        val genres = groupByGenre(songs, tr, { it.second }, { it.first })
        assertEquals(listOf("Halk", "Rock"), genres.map { it.first })
        assertEquals(listOf("Ağıt", "Zeybek"), genres.first().second.map { it.first })
        assertEquals(listOf("Cambaz", "Çay"), genres.last().second.map { it.first })
    }
}

/** The strip beside the Zune list: pins first, each thing once, never more than it holds. */
class QuickplayStripTest {

    @Test
    fun `pins lead, a pinned record is not shown twice, and the strip stops at its limit`() {
        val record = com.serkantkn.zeta.data.model.RecentAlbum(7L, "Homework", "Daft Punk", null)
        val pins = listOf(
            QuickplayPin(QuickplayPin.Kind.APP, "com.android.chrome", "Chrome"),
            QuickplayPin(QuickplayPin.Kind.ALBUM, "7", "Homework", "Daft Punk")
        )
        val state = com.serkantkn.zeta.data.model.Quickplay(
            pins = pins,
            recentAlbums = listOf(record),
            newAlbums = listOf(com.serkantkn.zeta.data.model.RecentAlbum(9L, "Karma", "Tarkan", null))
        )
        val strip = com.serkantkn.zeta.ui.screens.home.quickplayStripItems(state, 8)
        assertEquals(listOf("com.android.chrome", "7", "9"), strip.map { it.key })
        assertEquals(2, com.serkantkn.zeta.ui.screens.home.quickplayStripItems(state, 2).size)
    }
}
