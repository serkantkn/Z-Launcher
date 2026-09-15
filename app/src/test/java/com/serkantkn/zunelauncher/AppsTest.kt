package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.screens.apps.jumpAlphabet
import com.serkantkn.zunelauncher.util.foldForSearch
import com.serkantkn.zunelauncher.util.matchScore
import com.serkantkn.zunelauncher.util.rankApps
import com.serkantkn.zunelauncher.util.sectionLetterOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Finding an app by typing at it, and the alphabet the jump list offers.
 *
 * The Turkish cases are the ones worth having: an app called "Şarj" has to be findable by somebody
 * typing "sarj", and Android's own case rules turn I into ı in Turkish, which is exactly the sort
 * of thing that works on the desk and fails on the user's phone.
 */
class AppsTest {

    // ── Folding a name down to what gets typed ──────────────────────────────

    @Test
    fun `turkish letters fold to their plain neighbours`() {
        assertEquals("cgiosu", foldForSearch("çğıöşü"))
        assertEquals("cgiosu", foldForSearch("ÇĞIÖŞÜ"))
    }

    @Test
    fun `dotted capital I folds to a plain i`() {
        assertEquals("instagram", foldForSearch("İnstagram"))
    }

    @Test
    fun `a plain capital I does not become a dotless one`() {
        // Turkish locale rules would give "ı" here, which no keyboard reaches for by accident.
        assertEquals("instagram", foldForSearch("Instagram"))
    }

    @Test
    fun `other accents lose their marks too`() {
        assertEquals("cafe", foldForSearch("Café"))
        assertEquals("nino", foldForSearch("Niño"))
    }

    @Test
    fun `folding nothing gives nothing`() {
        assertEquals("", foldForSearch(""))
    }

    // ── Scoring ─────────────────────────────────────────────────────────────

    @Test
    fun `the whole name beats the beginning of it`() {
        assertTrue(
            matchScore("Maps", "com.g.maps", "maps") >
                matchScore("Maps Go", "com.g.mapsgo", "maps")
        )
    }

    @Test
    fun `the beginning of the name beats the middle`() {
        assertTrue(
            matchScore("Play Store", "com.android.vending", "play") >
                matchScore("Google Play", "com.google.play", "play")
        )
    }

    @Test
    fun `a word inside the name still counts`() {
        assertTrue(matchScore("Google Play Store", "com.x", "store") > 0)
    }

    @Test
    fun `initials find an app nobody wants to type out`() {
        assertTrue(matchScore("Google Play Store", "com.x", "gps") > 0)
    }

    @Test
    fun `the package name is the last resort`() {
        val byPackage = matchScore("Telefon", "com.android.dialer", "dialer")
        val byName = matchScore("Dialer", "com.other", "dialer")
        assertTrue(byPackage > 0)
        assertTrue(byName > byPackage)
    }

    @Test
    fun `something that does not match scores nothing`() {
        assertEquals(0, matchScore("Maps", "com.g.maps", "zzz"))
    }

    @Test
    fun `a blank query matches nothing rather than everything`() {
        assertEquals(0, matchScore("Maps", "com.g.maps", "   "))
    }

    @Test
    fun `a turkish name is found by typing it the easy way`() {
        assertTrue(matchScore("Şarj Durumu", "com.x.battery", "sarj") > 0)
        assertTrue(matchScore("Çalar Saat", "com.x.clock", "calar") > 0)
    }

    // ── Ranking a whole list ────────────────────────────────────────────────

    @Test
    fun `the best match comes first`() {
        val apps = listOf(
            app("Google Maps", "com.google.maps"),
            app("Maps", "com.maps"),
            app("Sitemap Tool", "com.tools.sitemap")
        )
        val ranked = rankApps(apps, "map")
        assertEquals(listOf("Maps", "Google Maps", "Sitemap Tool"), ranked.map { it.label })
    }

    @Test
    fun `apps that share a score keep a steady order`() {
        val apps = listOf(app("Banka", "b"), app("Bakkal", "a"))
        assertEquals(listOf("Bakkal", "Banka"), rankApps(apps, "ba").map { it.label })
    }

    @Test
    fun `searching for nothing returns nothing`() {
        assertTrue(rankApps(listOf(app("Maps", "com.maps")), "").isEmpty())
    }

    // ── Which letter an app files under ─────────────────────────────────────

    @Test
    fun `an app files under its own first letter`() {
        assertEquals('M', sectionLetterOf("Maps", Locale.ENGLISH))
    }

    @Test
    fun `turkish letters keep their own heading`() {
        assertEquals('Ç', sectionLetterOf("Çalar", Locale("tr")))
        assertEquals('Ş', sectionLetterOf("Şarj", Locale("tr")))
    }

    @Test
    fun `a name that starts with a number files under hash`() {
        assertEquals('#', sectionLetterOf("1Password", Locale.ENGLISH))
        assertEquals('#', sectionLetterOf("", Locale.ENGLISH))
    }

    @Test
    fun `leading space does not decide the heading`() {
        assertEquals('M', sectionLetterOf("  Maps", Locale.ENGLISH))
    }

    // ── The jump list's alphabet ────────────────────────────────────────────

    @Test
    fun `the turkish alphabet has its own letters and order`() {
        val letters = jumpAlphabet(emptySet(), Locale("tr"))
        assertTrue(letters.containsAll(listOf('Ç', 'Ğ', 'İ', 'Ö', 'Ş', 'Ü')))
        // Turkish has no Q, W or X.
        assertFalse(letters.contains('Q'))
        assertTrue(letters.indexOf('Ç') > letters.indexOf('C'))
        assertTrue(letters.indexOf('Ş') > letters.indexOf('S'))
    }

    @Test
    fun `an english alphabet is plain a to z`() {
        val letters = jumpAlphabet(emptySet(), Locale.ENGLISH)
        assertEquals(27, letters.size)
        assertEquals('A', letters.first())
        assertEquals('#', letters.last())
    }

    @Test
    fun `a letter an app actually uses is added even when the alphabet lacks it`() {
        val letters = jumpAlphabet(setOf('Ω'), Locale.ENGLISH)
        assertTrue(letters.contains('Ω'))
    }

    @Test
    fun `hash is last and appears once`() {
        val letters = jumpAlphabet(setOf('#', 'A'), Locale.ENGLISH)
        assertEquals('#', letters.last())
        assertEquals(1, letters.count { it == '#' })
    }

    private fun app(label: String, packageName: String) =
        AppInfo(packageName = packageName, label = label, activityName = "$packageName.Main")
}
