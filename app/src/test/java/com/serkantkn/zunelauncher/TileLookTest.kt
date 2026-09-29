package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.TileInk
import com.serkantkn.zunelauncher.data.model.TileLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tile's own look survives the trip through the preference, and a plain one is not stored. */
class TileLookTest {

    @Test
    fun `every field comes back as it went in`() {
        val look = TileLook(
            color = 0xFFE51400.toInt(),
            name = "ara",
            icon = TileIcon.Glyph("rocket"),
            showLabel = false,
            picture = "/data/user/0/x/files/tile_picture_hub_PHONE_1.jpg",
            iconScale = 1.3f,
            animation = TileAnimation.FLIP,
            notifications = false,
            opacity = 80,
            ink = TileInk.LIGHT
        )
        val back = TileLook.mapFromJson(TileLook.mapToJson(mapOf("hub:PHONE" to look)))
        assertEquals(look, back["hub:PHONE"])
    }

    @Test
    fun `a plain look is not written and a written one reads as plain when emptied`() {
        assertTrue(TileLook().isDefault)
        assertEquals("{}", TileLook.mapToJson(mapOf("hub:PHONE" to TileLook.DEFAULT)))
        val back = TileLook.mapFromJson("""{"hub:PHONE":{}}""")
        assertFalse(back.containsKey("hub:PHONE"))
    }

    @Test
    fun `an unset field is null rather than a default that would override the setting`() {
        val back = TileLook.mapFromJson("""{"app:x":{"n":"one"}}""").getValue("app:x")
        assertNull(back.color)
        assertNull(back.animation)
        assertNull(back.opacity)
        assertNull(back.ink)
        assertTrue(back.showLabel)
        assertTrue(back.notifications)
        assertEquals(1f, back.iconScale)
        assertEquals(TileIcon.Default, back.icon)
    }

    @Test
    fun `rubbish in the preference is an empty set, not a crash`() {
        assertTrue(TileLook.mapFromJson("not json").isEmpty())
        assertTrue(TileLook.mapFromJson(null).isEmpty())
        val back = TileLook.mapFromJson("""{"app:x":{"a":"WOBBLE","s":9,"o":140}}""").getValue("app:x")
        assertNull(back.animation)
        assertEquals(TileLook.MAX_ICON_SCALE, back.iconScale)
        assertEquals(100, back.opacity)
    }
}
