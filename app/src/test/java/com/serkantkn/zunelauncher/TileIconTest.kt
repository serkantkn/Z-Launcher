package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.WpGlyphs
import com.serkantkn.zunelauncher.data.repository.IconPackRepository
import com.serkantkn.zunelauncher.util.alphaBounds
import com.serkantkn.zunelauncher.util.alphaCoverage
import com.serkantkn.zunelauncher.util.curveAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which picture a tile shows for an app, and how an icon is reduced to a shape. */
class TileIconTest {

    // ── What the user picked ──────────────────────────────────────────────────────────────────

    @Test
    fun everyKindOfPickedIconSurvivesBeingWrittenAndReadBack() {
        val icons = listOf(
            TileIcon.Default,
            TileIcon.Glyph("phone"),
            TileIcon.Pack("com.example.pack", "ic_whatsapp"),
            TileIcon.Picture("/data/user/0/app/files/tile_icon_com_example.png")
        )

        icons.forEach { icon ->
            assertEquals(icon, TileIcon.parse(icon.store()))
        }
    }

    @Test
    fun aPackedIconKeepsItsPackageEvenWhenTheNameLooksLikeAPath() {
        val icon = TileIcon.parse("pack:com.example.pack/folder_open")

        assertEquals(TileIcon.Pack("com.example.pack", "folder_open"), icon)
    }

    @Test
    fun nonsenseFallsBackToTheLauncherDeciding() {
        listOf(null, "", "   ", "glyph:", "pack:", "pack:justapackage", "file:", "wat:phone")
            .forEach { assertEquals("parsing \"$it\"", TileIcon.Default, TileIcon.parse(it)) }
    }

    @Test
    fun theWholeSetOfPickedIconsSurvivesBeingWrittenAndReadBack() {
        val overrides = mapOf(
            "com.example.one" to TileIcon.Glyph("music"),
            "com.example.two" to TileIcon.Pack("com.pack", "ic_two"),
            "com.example.three" to TileIcon.Picture("/files/three.png")
        )

        assertEquals(overrides, TileIcon.mapFromJson(TileIcon.mapToJson(overrides)))
    }

    @Test
    fun anAppBackOnItsOwnIconIsNotStoredAtAll() {
        val stored = TileIcon.mapToJson(
            mapOf("com.example.one" to TileIcon.Default, "com.example.two" to TileIcon.Glyph("mail"))
        )

        assertEquals(mapOf("com.example.two" to TileIcon.Glyph("mail")), TileIcon.mapFromJson(stored))
    }

    @Test
    fun aRuinedPreferenceIsReadAsNoPickedIconsRatherThanCrashing() {
        assertEquals(emptyMap<String, TileIcon>(), TileIcon.mapFromJson("{not json"))
    }

    // ── The bundled glyphs ────────────────────────────────────────────────────────────────────

    @Test
    fun noGlyphIsListedTwice() {
        assertEquals(WpGlyphs.ALL.size, WpGlyphs.ALL.toSet().size)
    }

    @Test
    fun everyGlyphGroupHasSomethingInIt() {
        assertTrue(WpGlyphs.GROUPS.isNotEmpty())
        WpGlyphs.GROUPS.forEach { assertTrue(it.id, it.names.isNotEmpty()) }
    }

    @Test
    fun aGlyphNamesItsDrawable() {
        assertEquals("ic_wp_phone", WpGlyphs.resourceName("phone"))
    }

    // ── Icon packs ────────────────────────────────────────────────────────────────────────────

    @Test
    fun anAppIsLookedUpTheWayEveryIconPackWritesItDown() {
        assertEquals(
            "ComponentInfo{com.example/com.example.MainActivity}",
            IconPackRepository.componentKey("com.example", "com.example.MainActivity")
        )
    }

    @Test
    fun aPackWrittenForAnOlderVersionStillMatchesByPackage() {
        val component = IconPackRepository.componentKey("com.example", "com.example.OldActivity")

        assertEquals("com.example", IconPackRepository.packageOf(component))
    }

    @Test
    fun aMalformedComponentMatchesNothing() {
        listOf("", "ComponentInfo{}", "ComponentInfo{/only.an.activity}", "com.example/Main")
            .forEach { assertNull("parsing \"$it\"", IconPackRepository.packageOf(it)) }
    }

    // ── Reducing an icon to a shape ───────────────────────────────────────────────────────────

    /** An 8x8 icon whose opaque pixels are the rectangle [left, top, right, bottom]. */
    private fun icon(left: Int, top: Int, right: Int, bottom: Int, side: Int = 8): IntArray =
        IntArray(side * side) { index ->
            val x = index % side
            val y = index / side
            if (x in left..right && y in top..bottom) 255 else 0
        }

    @Test
    fun aSeeThroughIconIsMostlyNotThere() {
        assertEquals(0f, alphaCoverage(IntArray(64), 40), 0.001f)
        assertEquals(0.25f, alphaCoverage(icon(0, 0, 3, 3), 40), 0.001f)
        assertEquals(1f, alphaCoverage(icon(0, 0, 7, 7), 40), 0.001f)
    }

    @Test
    fun aFaintPixelDoesNotCountAsPartOfTheShape() {
        val faint = IntArray(64) { 20 }

        assertEquals(0f, alphaCoverage(faint, 40), 0.001f)
    }

    @Test
    fun theShapeIsBoxedInASquareCentredOnIt() {
        // A wide glyph: 6 across, 2 down, sitting in the middle.
        val bounds = alphaBounds(icon(1, 3, 6, 4), 8, 8, 40)

        assertNotNull(bounds)
        val (left, top, width, height) = bounds!!.let { arrayOf(it[0], it[1], it[2], it[3]) }
        assertEquals("a square, so nothing is stretched", width, height)
        assertEquals(6, width)
        // Centred on the glyph: x 1..6 has its middle at 3.5, y 3..4 at 4.
        assertEquals(1, left)
        assertEquals(1, top)
    }

    @Test
    fun aPalePlateBehindAGlyphIsNotPartOfIt() {
        val curved = curveAlpha(intArrayOf(0, 40, 96, 120, 190, 255), 96, 190)

        assertEquals(0, curved[0])
        assertEquals("a faint plate disappears", 0, curved[1])
        assertEquals(0, curved[2])
        assertTrue("the ramp keeps the glyph's edge", curved[3] in 1..254)
        assertEquals("a solid pixel stays solid", 255, curved[4])
        assertEquals(255, curved[5])
    }

    @Test
    fun anIconWithNothingInItHasNoShape() {
        assertNull(alphaBounds(IntArray(64), 8, 8, 40))
    }

    @Test
    fun anIconSmallerThanItClaimsHasNoShape() {
        assertNull(alphaBounds(IntArray(9), 8, 8, 40))
    }
}
