package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R

/** Hubs of the launcher. [titleRes] is the lowercase Windows Phone style title. */
enum class HubType(@StringRes val titleRes: Int) {
    HOME(R.string.home_hub),
    MUSIC(R.string.music_hub),
    PEOPLE(R.string.people_hub),
    PICTURES(R.string.pictures_hub),
    SETTINGS(R.string.settings_hub),
    PHONE(R.string.hub_phone),
    CLOCK(R.string.hub_clock),
    INTERNET(R.string.hub_internet),
    CALENDAR(R.string.hub_calendar),
    MESSAGING(R.string.hub_messaging),
    FILES(R.string.hub_files),
    NOTES(R.string.hub_notes),
    EMAIL(R.string.hub_email),
    CALCULATOR(R.string.hub_calculator),
    WEATHER(R.string.hub_weather)
}
