package com.serkantkn.zeta

import com.serkantkn.zeta.data.repository.AdBlocker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    @After
    fun reset() {
        AdBlocker.enabled = true
        AdBlocker.allowedSites = emptySet()
    }

    @Test
    fun aHostsFileGivesItsHostsAndNothingElse() {
        val hosts = AdBlocker.parseHosts(
            sequenceOf(
                "# Title: StevenBlack/hosts",
                "127.0.0.1 localhost",
                "0.0.0.0 0.0.0.0",
                "0.0.0.0 ads.example.com # an advert",
                "0.0.0.0\tTracker.Example.net",
                "1.2.3.4 not-a-block.example.org",
                "fe80::1%lo0 localhost",
                "0.0.0.0 nodot"
            )
        )
        assertEquals(setOf("ads.example.com", "tracker.example.net"), hosts)
    }

    @Test
    fun aHostIsCaughtByItselfOrAnyDomainAboveIt() {
        val list = setOf("doubleclick.net", "ads.example.com")
        assertTrue(AdBlocker.matches("securepubads.g.doubleclick.net", list))
        assertTrue(AdBlocker.matches("doubleclick.net", list))
        assertTrue(AdBlocker.matches("x.ads.example.com", list))
        assertFalse(AdBlocker.matches("example.com", list))
        assertFalse(AdBlocker.matches("notdoubleclick.net", list))
    }

    @Test
    fun theHostIsReadOutOfAnyAddress() {
        assertEquals("a.b.com", AdBlocker.hostOf("https://A.B.com:443/path?q=1"))
        assertEquals("b.com", AdBlocker.hostOf("http://user@b.com/x"))
        assertEquals(null, AdBlocker.hostOf("data:image/png;base64,xx"))
    }

    @Test
    fun aPagesOwnSiteAndSitesSwitchedOffAreNeverBlocked() {
        // The seed list is what is loaded in a test; doubleclick.net is on it.
        assertTrue(AdBlocker.shouldBlock("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "www.hurriyet.com.tr"))
        assertFalse(AdBlocker.shouldBlock("https://doubleclick.net/", "doubleclick.net"))
        assertFalse(AdBlocker.shouldBlock("https://static.hurriyet.com.tr/a.js", "www.hurriyet.com.tr"))
        AdBlocker.allowedSites = setOf("hurriyet.com.tr")
        assertFalse(AdBlocker.shouldBlock("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "www.hurriyet.com.tr"))
        AdBlocker.allowedSites = emptySet()
        AdBlocker.enabled = false
        assertFalse(AdBlocker.shouldBlock("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "www.hurriyet.com.tr"))
    }
}
