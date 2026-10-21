package com.serkantkn.zeta.ui.openwith

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.serkantkn.zeta.data.model.HubType

/**
 * The launcher's hubs as the phone's apps of their kind. Android asks for "the gallery", "the
 * music app", "the calendar" by category; each category is an alias of this activity in the
 * manifest, and the alias it was reached by says which hub to open.
 */
class HubShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alias = intent?.component?.className.orEmpty().substringAfterLast('.')
        hubOf(alias)?.let { OpenWith.openHub(this, it) }
        finish()
    }

    companion object {
        /** The hub an alias stands for, by the alias's own name. */
        fun hubOf(alias: String): HubType? = when (alias) {
            "AppGallery" -> HubType.PICTURES
            "AppMusic" -> HubType.MUSIC
            "AppContacts" -> HubType.PEOPLE
            "AppCalendar" -> HubType.CALENDAR
            "AppMessaging" -> HubType.MESSAGING
            "AppEmail" -> HubType.EMAIL
            "AppCalculator" -> HubType.CALCULATOR
            "AppFiles" -> HubType.FILES
            "AppWeather" -> HubType.WEATHER
            "AppBrowser" -> HubType.INTERNET
            else -> null
        }
    }
}
