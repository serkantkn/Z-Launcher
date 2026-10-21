package com.serkantkn.zeta.data.model

import androidx.annotation.StringRes
import com.serkantkn.zeta.R

/**
 * Hubs of the launcher. [titleRes] is the lowercase Windows Phone style title.
 *
 * [runsInBackground] says whether the hub may be left running when the user goes Home. A hub that
 * holds the camera cannot: keeping it composed would keep the sensor streaming and the screen
 * awake behind the Start screen, so the camera is closed on the way out instead.
 *
 * [onTelevision] says whether the hub is offered on a TV, which has no phone line, no camera and
 * no keyboard of ours, and is not where mail or notes are written.
 */
enum class HubType(
    @StringRes val titleRes: Int,
    val runsInBackground: Boolean = true,
    val onTelevision: Boolean = false
) {
    HOME(R.string.home_hub, runsInBackground = false),
    MUSIC(R.string.music_hub, onTelevision = true),
    PEOPLE(R.string.people_hub),
    PICTURES(R.string.pictures_hub, onTelevision = true),
    SETTINGS(R.string.settings_hub, onTelevision = true),
    PHONE(R.string.hub_phone),
    CLOCK(R.string.hub_clock, onTelevision = true),
    INTERNET(R.string.hub_internet),
    CALENDAR(R.string.hub_calendar),
    MESSAGING(R.string.hub_messaging),
    FILES(R.string.hub_files, onTelevision = true),
    NOTES(R.string.hub_notes),
    EMAIL(R.string.hub_email),
    CALCULATOR(R.string.hub_calculator),
    WEATHER(R.string.hub_weather, onTelevision = true),
    NEWS(R.string.hub_news, onTelevision = true),
    CAMERA(R.string.hub_camera, runsInBackground = false)
}
