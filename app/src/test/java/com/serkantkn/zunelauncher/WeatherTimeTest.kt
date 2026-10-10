package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.DailyPoint
import com.serkantkn.zunelauncher.data.model.HourlyPoint
import com.serkantkn.zunelauncher.data.model.MeasureSystem
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherCondition
import com.serkantkn.zunelauncher.data.model.WeatherNow
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.data.model.WindUnit
import com.serkantkn.zunelauncher.util.UpdatedAgo
import com.serkantkn.zunelauncher.util.daysFrom
import com.serkantkn.zunelauncher.util.hoursFrom
import com.serkantkn.zunelauncher.util.updatedAgo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** A cached forecast, read later than it was fetched. */
class WeatherTimeTest {

    private val place = WeatherPlace(id = "x", name = "x", latitude = 0.0, longitude = 0.0)
    private val now = WeatherNow(20.0, 20.0, WeatherCondition.CLEAR, true, 50, 10.0, 90, 1013.0, 0, 0.0, null, null, null)

    private fun hour(seconds: Long) = HourlyPoint(seconds, 20.0, WeatherCondition.CLEAR, 0, true)
    private fun day(seconds: Long) = DailyPoint(seconds, 10.0, 20.0, WeatherCondition.CLEAR, 0, 0.0, 0.0, null, null, null)

    // Istanbul: UTC+3. A day at the place starts at 21:00 UTC the evening before.
    private val offset = 3 * 3600
    private val dayStart = 1_700_000_000L - (1_700_000_000L + offset) % 86_400L // local midnight

    private fun snapshot(fetchedAtSeconds: Long) = WeatherSnapshot(
        place = place,
        now = now,
        hourly = (0 until 24).map { hour(fetchedAtSeconds - fetchedAtSeconds % 3600 + it * 3600L) },
        daily = (0 until 5).map { day(dayStart + it * 86_400L) },
        airQuality = null,
        utcOffsetSeconds = offset,
        fetchedAt = fetchedAtSeconds * 1000
    )

    @Test
    fun `the hours start with the hour it is now, not the hour of the fetch`() {
        val fetched = dayStart + 8 * 3600 + 600 // 08:10
        val later = dayStart + 13 * 3600 + 1200 // 13:20 the same day
        val hours = snapshot(fetched).hoursFrom(later)
        assertEquals(dayStart + 13 * 3600, hours.first().timeSeconds)
        assertEquals(24 - 5, hours.size)
    }

    @Test
    fun `read at the moment of the fetch, the hours are unchanged`() {
        val fetched = dayStart + 8 * 3600 + 600
        assertEquals(24, snapshot(fetched).hoursFrom(fetched).size)
    }

    @Test
    fun `yesterday's forecast day is dropped the next morning, at the place`() {
        val fetched = dayStart + 20 * 3600
        val nextMorning = dayStart + 86_400 + 7 * 3600
        val days = snapshot(fetched).daysFrom(nextMorning)
        assertEquals(4, days.size)
        assertEquals(dayStart + 86_400, days.first().timeSeconds)
    }

    @Test
    fun `just after midnight at the place is already the next day, whatever UTC says`() {
        val fetched = dayStart + 20 * 3600
        // 00:30 local = 21:30 UTC the day before.
        val justPastMidnight = dayStart + 86_400 + 1800
        assertEquals(dayStart + 86_400, snapshot(fetched).daysFrom(justPastMidnight).first().timeSeconds)
    }

    @Test
    fun `how old the forecast is, in the units a person would say`() {
        val fetched = 1_000_000_000L
        assertEquals(UpdatedAgo.JustNow, updatedAgo(fetched, fetched + 30_000L))
        assertEquals(UpdatedAgo.Minutes(5), updatedAgo(fetched, fetched + 5 * 60_000L + 2_000L))
        assertEquals(UpdatedAgo.Hours(3), updatedAgo(fetched, fetched + 3 * 3_600_000L + 90_000L))
        assertEquals(UpdatedAgo.At(fetched), updatedAgo(fetched, fetched + 25 * 3_600_000L))
        // A clock set backwards is not a forecast from the future.
        assertEquals(UpdatedAgo.JustNow, updatedAgo(fetched, fetched - 60_000L))
    }

    @Test
    fun `the units default by country`() {
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.defaultFor(Locale.US))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.defaultFor(Locale("tr", "TR")))
        assertEquals(WindUnit.MPH, WindUnit.defaultFor(Locale.US))
        assertEquals(WindUnit.KMH, WindUnit.defaultFor(Locale.GERMANY))
        assertEquals(MeasureSystem.IMPERIAL, MeasureSystem.defaultFor(Locale.US))
        assertEquals(MeasureSystem.METRIC, MeasureSystem.defaultFor(Locale.UK))
    }

    @Test
    fun `imperial readings are converted from the metric cache`() {
        assertEquals(10.0, MeasureSystem.METRIC.distanceOf(10_000.0), 0.001)
        assertEquals(6.2137, MeasureSystem.IMPERIAL.distanceOf(10_000.0), 0.001)
        assertEquals(1.0, MeasureSystem.IMPERIAL.precipitationOf(25.4), 0.001)
        assertEquals(29.92, MeasureSystem.IMPERIAL.pressureOf(1013.25), 0.01)
        assertEquals(1013.25, MeasureSystem.METRIC.pressureOf(1013.25), 0.001)
    }
}
