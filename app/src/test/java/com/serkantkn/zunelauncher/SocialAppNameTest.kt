package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.ui.screens.social.appNameLower
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SocialAppNameTest {
    private lateinit var saved: Locale

    @Before
    fun turkish() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    }

    @After
    fun restore() = Locale.setDefault(saved)

    @Test
    fun `a Latin capital I stays an i in Turkish`() {
        assertEquals("instagram", appNameLower("Instagram"))
        assertEquals("linkedin", appNameLower("LinkedIn"))
    }

    @Test
    fun `a Turkish dotted capital still lowers the Turkish way`() {
        assertEquals("iş bankası", appNameLower("İş Bankası"))
    }
}
