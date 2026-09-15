package com.serkantkn.zunelauncher.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.cityNameFromZoneId
import java.util.Locale

/**
 * A clock on somebody else's wall.
 *
 * A city is nothing but its time zone: the zone id is both the identity and the whole of the data,
 * so a city added twice is the same city and the list needs no ids of its own.
 *
 * The name is worked out when it is drawn rather than stored, because it depends on the language
 * the launcher is being read in. A dozen cities people actually look for are translated by hand;
 * every other zone in the database falls back to the city written into its own id, which is
 * already the English name of the place and is what every other clock app shows too.
 */
data class WorldCity(val timeZoneId: String) {

    /** The city, in the reader's language where somebody has written it down, else out of the id. */
    fun cityName(context: Context): String {
        val curated = CURATED[timeZoneId]
        return if (curated != null) context.getString(curated.cityRes) else cityNameFromZoneId(timeZoneId)
    }

    /** The country, translated by Android from the zone's own region where it knows one. */
    fun countryName(context: Context): String {
        val curated = CURATED[timeZoneId]
        if (curated != null) return context.getString(curated.countryRes)
        val region = regionCode(timeZoneId) ?: return ""
        return Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.getDefault())
    }

    /** Both, as one searchable haystack. */
    fun searchText(context: Context): String = "${cityName(context)} ${countryName(context)} $timeZoneId"

    private data class Curated(@StringRes val cityRes: Int, @StringRes val countryRes: Int)

    companion object {
        /** The cities the launcher ships with the first time the hub is opened. */
        val DEFAULTS: List<WorldCity> = listOf(
            WorldCity("Europe/Istanbul"),
            WorldCity("Europe/London"),
            WorldCity("America/New_York"),
            WorldCity("Asia/Tokyo")
        )

        /** Offered at the top of the picker, before the full list of zones. */
        val POPULAR: List<WorldCity> = listOf(
            "Europe/Istanbul", "Europe/London", "Europe/Berlin", "Europe/Paris", "Europe/Rome",
            "Europe/Moscow", "America/New_York", "America/Los_Angeles", "Asia/Tokyo",
            "Asia/Shanghai", "Asia/Dubai", "Australia/Sydney"
        ).map { WorldCity(it) }

        private val CURATED: Map<String, Curated> = mapOf(
            "Europe/Istanbul" to Curated(R.string.city_istanbul, R.string.country_turkey),
            "Europe/London" to Curated(R.string.city_london, R.string.country_uk),
            "America/New_York" to Curated(R.string.city_new_york, R.string.country_usa),
            "America/Los_Angeles" to Curated(R.string.city_los_angeles, R.string.country_usa),
            "Asia/Tokyo" to Curated(R.string.city_tokyo, R.string.country_japan),
            "Europe/Paris" to Curated(R.string.city_paris, R.string.country_france),
            "Australia/Sydney" to Curated(R.string.city_sydney, R.string.country_australia),
            "Europe/Berlin" to Curated(R.string.city_berlin, R.string.country_germany),
            "Asia/Shanghai" to Curated(R.string.city_beijing, R.string.country_china),
            "Asia/Dubai" to Curated(R.string.city_dubai, R.string.country_uae),
            "Europe/Moscow" to Curated(R.string.city_moscow, R.string.country_russia),
            "Europe/Rome" to Curated(R.string.city_rome, R.string.country_italy)
        )

        /** The ISO country a zone belongs to, which Android's own copy of the database knows. */
        private fun regionCode(zoneId: String): String? = runCatching {
            android.icu.util.TimeZone.getRegion(zoneId).takeIf { it.length == 2 }
        }.getOrNull()
    }
}
