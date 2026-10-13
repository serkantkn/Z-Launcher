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
