package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherCondition
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSky
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.data.model.WindUnit
import com.serkantkn.zunelauncher.data.repository.WeatherApi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Open-Meteo answers, and what the hub makes of it. */
class WeatherApiTest {

    private val place = WeatherPlace(
        id = "41.015,28.979",
        name = "İstanbul",
        region = "İstanbul",
        country = "Türkiye",
        latitude = 41.015,
        longitude = 28.979
    )

    /** 10:00, 11:00, 12:00 and 13:00 local time on an imaginary day. */
    private val hour10 = 1_757_498_400L
    private val hour11 = hour10 + 3600
    private val hour12 = hour10 + 7200
    private val hour13 = hour10 + 10800

    private fun forecastJson(): JSONObject = JSONObject()
        .put("utc_offset_seconds", 10800)
        .put(
            "current",
            JSONObject()
                .put("time", hour11)
                .put("temperature_2m", 21.4)
                .put("apparent_temperature", 22.9)
                .put("relative_humidity_2m", 63)
                .put("is_day", 1)
                .put("precipitation", 0.0)
                .put("weather_code", 61)
                .put("cloud_cover", 75)
                .put("surface_pressure", 1012.3)
                .put("wind_speed_10m", 18.5)
                .put("wind_direction_10m", 220)
        )
        .put(
            "hourly",
            JSONObject()
                .put("time", JSONArray(listOf(hour10, hour11, hour12, hour13)))
                .put("temperature_2m", JSONArray(listOf(20.1, 21.4, 22.0, 22.6)))
                .put("weather_code", JSONArray(listOf(3, 61, 61, 80)))
                .put("precipitation_probability", JSONArray(listOf(10, 55, 60, 70)))
                .put("is_day", JSONArray(listOf(1, 1, 1, 1)))
                .put("uv_index", JSONArray(listOf(2.1, 3.4, 4.0, 3.8)))
                .put("visibility", JSONArray(listOf(24000.0, 18000.0, 12000.0, 9000.0)))
                .put("dew_point_2m", JSONArray(listOf(12.0, 13.5, 14.0, 14.2)))
        )
        .put(
            "daily",
            JSONObject()
                .put("time", JSONArray(listOf(hour10, hour10 + 86400)))
                .put("weather_code", JSONArray(listOf(61, 3)))
                .put("temperature_2m_max", JSONArray(listOf(24.0, 26.5)))
                .put("temperature_2m_min", JSONArray(listOf(16.0, 17.5)))
                .put("sunrise", JSONArray(listOf(hour10 - 14400, hour10 + 71000)))
                .put("sunset", JSONArray(listOf(hour10 + 32400, hour10 + 118000)))
                .put("precipitation_probability_max", JSONArray(listOf(70, 15)))
                .put("precipitation_sum", JSONArray(listOf(4.2, 0.0)))
                .put("uv_index_max", JSONArray(listOf(4.0, 6.2)))
                .put("wind_speed_10m_max", JSONArray(listOf(28.0, 15.0)))
        )

    @Test
    fun forecastReadsCurrentConditions() {
        val snapshot = WeatherApi.parseForecast(forecastJson(), place, hour11)

        assertEquals(21.4, snapshot.now.temperature, 0.001)
        assertEquals(22.9, snapshot.now.apparent, 0.001)
        assertEquals(WeatherCondition.RAIN, snapshot.now.condition)
        assertEquals(63, snapshot.now.humidity)
        assertEquals(220, snapshot.now.windDirection)
        assertEquals(10800, snapshot.utcOffsetSeconds)
        assertTrue(snapshot.now.isDay)
    }

    /** UV, visibility and dew point are only published per hour, so they come from the hour we are in. */
    @Test
    fun forecastTakesHourlyOnlyReadingsFromTheCurrentHour() {
        val snapshot = WeatherApi.parseForecast(forecastJson(), place, hour12 + 900)

        assertEquals(4.0, snapshot.now.uvIndex!!, 0.001)
        assertEquals(12000.0, snapshot.now.visibilityMeters!!, 0.001)
        assertEquals(14.0, snapshot.now.dewPoint!!, 0.001)
    }

    @Test
    fun hourlyStripStartsAtTheCurrentHour() {
        val snapshot = WeatherApi.parseForecast(forecastJson(), place, hour11 + 100)

        assertEquals(3, snapshot.hourly.size)
        assertEquals(hour11, snapshot.hourly.first().timeSeconds)
        assertEquals(55, snapshot.hourly.first().precipitationChance)
        assertEquals(WeatherCondition.RAIN_SHOWERS, snapshot.hourly.last().condition)
    }

    @Test
    fun dailyRowsCarryTheirOwnSunTimes() {
        val snapshot = WeatherApi.parseForecast(forecastJson(), place, hour11)
        val today = snapshot.daily.first()

        assertEquals(2, snapshot.daily.size)
        assertEquals(16.0, today.minTemperature, 0.001)
        assertEquals(24.0, today.maxTemperature, 0.001)
        assertEquals(70, today.precipitationChance)
        assertEquals(hour10 - 14400, today.sunriseSeconds)
        assertEquals(hour10 + 32400, today.sunsetSeconds)
    }

    /** A place the service has no readings for must not take the whole forecast down with it. */
    @Test
    fun missingHourlyBlockStillProducesASnapshot() {
        val json = forecastJson().apply { remove("hourly") }
        val snapshot = WeatherApi.parseForecast(json, place, hour11)

        assertTrue(snapshot.hourly.isEmpty())
        assertNull(snapshot.now.uvIndex)
        assertEquals(21.4, snapshot.now.temperature, 0.001)
    }

    @Test
    fun indexOfHourPicksTheHourInProgress() {
        val times = JSONArray(listOf(hour10, hour11, hour12, hour13))

        assertEquals(0, WeatherApi.indexOfHour(times, hour10))
        assertEquals(1, WeatherApi.indexOfHour(times, hour11 + 59 * 60))
        assertEquals(3, WeatherApi.indexOfHour(times, hour13 + 86400))
        // Before the list even starts, the first hour is the best guess available.
        assertEquals(0, WeatherApi.indexOfHour(times, hour10 - 7200))
        assertEquals(-1, WeatherApi.indexOfHour(JSONArray(), hour10))
    }

    @Test
    fun airQualityReadsWhatIsThereAndLeavesTheRestNull() {
        val json = JSONObject().put(
            "current",
            JSONObject()
                .put("european_aqi", 42)
                .put("pm2_5", 11.3)
                .put("pm10", 20.7)
                .put("ozone", 68.0)
                .put("nitrogen_dioxide", JSONObject.NULL)
                .put("grass_pollen", 1.4)
        )

        val air = WeatherApi.parseAirQuality(json)!!

        assertEquals(42, air.europeanAqi)
        assertEquals(11.3, air.pm25!!, 0.001)
        assertEquals(1.4, air.grassPollen!!, 0.001)
        assertNull(air.nitrogenDioxide)
        assertNull(air.birchPollen)
    }

    @Test
    fun airQualityWithoutACurrentBlockIsNull() {
        assertNull(WeatherApi.parseAirQuality(JSONObject()))
    }

    @Test
    fun searchTurnsResultsIntoPlaces() {
        val json = JSONObject().put(
            "results",
            JSONArray()
                .put(
                    JSONObject()
                        .put("name", "İzmir")
                        .put("latitude", 38.41885)
                        .put("longitude", 27.12872)
                        .put("country", "Türkiye")
                        .put("admin1", "İzmir")
                )
                // No coordinates: unusable, and must not crash the list.
                .put(JSONObject().put("name", "Nowhere"))
        )

        val places = WeatherApi.parseSearch(json)

        assertEquals(1, places.size)
        assertEquals("İzmir", places.first().name)
        assertEquals("38.419,27.129", places.first().id)
        assertEquals("İzmir, Türkiye", places.first().subtitle)
    }

    @Test
    fun searchWithoutResultsIsEmpty() {
        assertTrue(WeatherApi.parseSearch(JSONObject()).isEmpty())
    }

    @Test
    fun wmoCodesMapToConditionsAndSkies() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmo(0))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmo(48))
        assertEquals(WeatherCondition.HEAVY_SNOW, WeatherCondition.fromWmo(75))
        assertEquals(WeatherCondition.THUNDERSTORM_HAIL, WeatherCondition.fromWmo(99))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(1234))

        assertEquals(WeatherSky.RAIN, WeatherCondition.fromWmo(65).sky)
        assertEquals(WeatherSky.SNOW, WeatherCondition.fromWmo(85).sky)
        assertEquals(WeatherSky.STORM, WeatherCondition.fromWmo(95).sky)
    }

    /** The cache is JSON on disk, so a snapshot has to survive the round trip unchanged. */
    @Test
    fun snapshotSurvivesTheCache() {
        val original = WeatherApi.parseForecast(forecastJson(), place, hour11)

        val restored = WeatherSnapshot.fromJson(JSONObject(original.toJson().toString()))

        assertNotNull(restored)
        assertEquals(original.place.name, restored!!.place.name)
        assertEquals(original.now.temperature, restored.now.temperature, 0.001)
        assertEquals(original.now.condition, restored.now.condition)
        assertEquals(original.hourly.size, restored.hourly.size)
        assertEquals(original.daily.first().sunsetSeconds, restored.daily.first().sunsetSeconds)
        assertEquals(original.utcOffsetSeconds, restored.utcOffsetSeconds)
    }

    @Test
    fun unitsConvertAtTheLastMoment() {
        assertEquals(21, TemperatureUnit.CELSIUS.of(21.4))
        assertEquals(71, TemperatureUnit.FAHRENHEIT.of(21.4))
        assertEquals(-4, TemperatureUnit.CELSIUS.of(-3.6))

        assertEquals(18.0, WindUnit.KMH.of(18.0), 0.001)
        assertEquals(5.0, WindUnit.MS.of(18.0), 0.01)
        assertEquals(11.185, WindUnit.MPH.of(18.0), 0.01)
    }
}
