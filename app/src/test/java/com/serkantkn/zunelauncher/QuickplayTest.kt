package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.RecentAlbum
import com.serkantkn.zunelauncher.data.model.mostRecentFirst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Zune list's "lately": newest first, no repeats, never longer than asked. */
class QuickplayTest {

    @Test
    fun `an app opened again moves to the front rather than appearing twice`() {
        val list = mostRecentFirst(listOf("b", "a", "c"), "a", 8) { x, y -> x == y }
        assertEquals(listOf("a", "b", "c"), list)
    }

    @Test
    fun `the list is cut at its limit from the old end`() {
        var list = emptyList<String>()
        ('a'..'j').forEach { list = mostRecentFirst(list, it.toString(), 4) { x, y -> x == y } }
        assertEquals(listOf("j", "i", "h", "g"), list)
    }

    @Test
    fun `a record survives the trip through the preference`() {
        val albums = listOf(
            RecentAlbum(7L, "Uçurtma", "Ayşe Yıldız", "content://media/external/audio/albumart/7"),
            RecentAlbum(3L, "Sisli Sabah", "Cam Sokağı", null)
        )
        assertEquals(albums, RecentAlbum.listFromJson(RecentAlbum.listToJson(albums)))
    }

    @Test
    fun `rubbish in the preference is an empty list and a record without an id is skipped`() {
        assertTrue(RecentAlbum.listFromJson("not json").isEmpty())
        assertTrue(RecentAlbum.listFromJson(null).isEmpty())
        assertNull(RecentAlbum.fromJson(org.json.JSONObject("""{"t":"x"}""")))
    }
}
