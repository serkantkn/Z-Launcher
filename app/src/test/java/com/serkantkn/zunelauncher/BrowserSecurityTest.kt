package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.ui.screens.browser.PageSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrowserSecurityTest {

    @Test
    fun theAddressSaysHowSafeThePageIs() {
        assertEquals(PageSecurity.NONE, PageSecurity.of("", certificateIgnored = false))
        assertEquals(PageSecurity.SECURE, PageSecurity.of("https://www.bbc.com/turkce", certificateIgnored = false))
        assertEquals(PageSecurity.INSECURE, PageSecurity.of("http://info.cern.ch", certificateIgnored = false))
        assertEquals(PageSecurity.CERTIFICATE_IGNORED, PageSecurity.of("https://expired.badssl.com/", certificateIgnored = true))
        assertEquals(PageSecurity.NONE, PageSecurity.of("about:blank", certificateIgnored = false))
    }

    @Test
    fun plainHttpPagesCanBeOpenedButCertificatesStayTheSystems() {
        // Without this the browser could open no http page at all (ERR_CLEARTEXT_NOT_PERMITTED).
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        val config = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(config.contains("<certificates src=\"system\" />"))
        assertTrue("user-added certificates are not trusted", !config.contains("src=\"user\""))
    }
}

class BrowserTextSizeTest {
    private val sizes = mapOf("bbc.com" to 150, com.serkantkn.zunelauncher.ui.screens.browser.TextSizes.EVERY_SITE to 115)

    @Test
    fun aSiteKeepsItsOwnSizeAndTheRestTakeEverySites() {
        val t = com.serkantkn.zunelauncher.ui.screens.browser.TextSizes
        assertEquals(150, t.forUrl(sizes, "https://www.bbc.com/turkce/articles/x"))
        assertEquals(115, t.forUrl(sizes, "https://example.com/"))
        assertEquals(100, t.forUrl(emptyMap(), "https://example.com/"))
        assertEquals("bbc.com", t.hostOf("https://www.bbc.com/a"))
        assertEquals(sizes, t.parse(t.toJson(sizes)))
        assertEquals(emptyMap<String, Int>(), t.parse("not json"))
    }

    @Test
    fun translationGoesThroughGoogleTranslateIntoThePhonesLanguage() {
        val url = com.serkantkn.zunelauncher.ui.screens.browser.translationOf("https://www.bbc.co.uk/news?a=1&b=2", "tr")
        assertEquals("https://translate.google.com/translate?sl=auto&tl=tr&u=https%3A%2F%2Fwww.bbc.co.uk%2Fnews%3Fa%3D1%26b%3D2", url)
    }
}
