package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.DailyPoint
import com.serkantkn.zunelauncher.data.model.HourlyPoint
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot

/**
 * Reading a cached forecast at a later moment than it was fetched.
 *
 * A forecast is fetched once and looked at for hours — on a free build, for most of a day. Its
 * first hour is the hour it was fetched in and its first day the day it was fetched on, and the
 * hub used to take both as "now" and "today" whatever the clock said, so at breakfast the strip
 * still started at last night and the ten-day page led with yesterday. Everything here is a pure
 * function of the snapshot and the clock, so it can be tested without a sky.
 */

private const val HOUR_SECONDS = 3_600L
private const val DAY_SECONDS = 86_400L

/** The hours still to come, starting with the hour [nowSeconds] falls in. */
fun WeatherSnapshot.hoursFrom(nowSeconds: Long): List<HourlyPoint> =
    hourly.filter { it.timeSeconds + HOUR_SECONDS > nowSeconds }

/**
 * The days from today on, today being the date at the place — midnight there, not here — so a
 * forecast for Tokyo read from Istanbul still leads with Tokyo's own today.
 */
fun WeatherSnapshot.daysFrom(nowSeconds: Long): List<DailyPoint> {
    val today = placeDateIndex(nowSeconds, utcOffsetSeconds)
    return daily.filter { placeDateIndex(it.timeSeconds, utcOffsetSeconds) >= today }
}

/** Which calendar date, counted from the epoch, a moment falls on at an offset. */
internal fun placeDateIndex(seconds: Long, offsetSeconds: Int): Long =
    Math.floorDiv(seconds + offsetSeconds, DAY_SECONDS)

/** How long ago the forecast was fetched, in the units a person would say it in. */
sealed class UpdatedAgo {
    data object JustNow : UpdatedAgo()
    data class Minutes(val minutes: Int) : UpdatedAgo()
    data class Hours(val hours: Int) : UpdatedAgo()

    /** Too long ago to say relatively: the moment itself is shown instead. */
    data class At(val millis: Long) : UpdatedAgo()
}

fun updatedAgo(fetchedAtMillis: Long, nowMillis: Long): UpdatedAgo {
    val minutes = ((nowMillis - fetchedAtMillis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> UpdatedAgo.JustNow
        minutes < 60L -> UpdatedAgo.Minutes(minutes.toInt())
        minutes < 24L * 60L -> UpdatedAgo.Hours((minutes / 60L).toInt())
        else -> UpdatedAgo.At(fetchedAtMillis)
    }
}
