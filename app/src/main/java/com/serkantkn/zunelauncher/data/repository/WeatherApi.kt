package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AirQuality
import com.serkantkn.zunelauncher.data.model.DailyPoint
import com.serkantkn.zunelauncher.data.model.HourlyPoint
import com.serkantkn.zunelauncher.data.model.WeatherCondition
import com.serkantkn.zunelauncher.data.model.WeatherException
import com.serkantkn.zunelauncher.data.model.WeatherNow
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Open-Meteo, the free weather service the hub runs on.
 *
 * There is no API key and no sign-up: the endpoints are open, which is why this service was
 * chosen over the ones that need a registered application. Data is CC-BY Open-Meteo; the
 * attribution sits in settings → about.
 *
 * Every call blocks and belongs on Dispatchers.IO. Times are asked for as unix seconds together
 * with the place's own UTC offset, so nothing has to guess at a timezone name.
 */
internal object WeatherApi {

    private const val TAG = "WeatherApi"
    private const val TIMEOUT_MS = 20_000

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val AIR_QUALITY = "https://air-quality-api.open-meteo.com/v1/air-quality"
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"

    private const val CURRENT_FIELDS =
        "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
            "weather_code,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m"
    private const val HOURLY_FIELDS =
        "temperature_2m,weather_code,precipitation_probability,is_day,uv_index,visibility,dew_point_2m"
    private const val DAILY_FIELDS =
        "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
            "precipitation_probability_max,precipitation_sum,uv_index_max,wind_speed_10m_max"
    private const val AIR_FIELDS =
        "european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone," +
            "birch_pollen,grass_pollen,ragweed_pollen"

    /** Days of forecast asked for; the ten-day page shows them all. */
    const val FORECAST_DAYS = 10

    /** Hours kept for the strip on the today page. */
    const val HOURLY_HOURS = 24

    // --- Calls ------------------------------------------------------------------------------------

    fun forecast(place: WeatherPlace, nowSeconds: Long): WeatherSnapshot {
        val url = "$FORECAST?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=$CURRENT_FIELDS&hourly=$HOURLY_FIELDS&daily=$DAILY_FIELDS" +
            "&timezone=auto&timeformat=unixtime&forecast_days=$FORECAST_DAYS"
        return parseForecast(getJson(url), place, nowSeconds)
    }

    fun airQuality(place: WeatherPlace): AirQuality? {
        val url = "$AIR_QUALITY?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=$AIR_FIELDS&timezone=auto&timeformat=unixtime"
        return try {
            parseAirQuality(getJson(url))
        } catch (e: WeatherException) {
            // Air quality is an extra: a place it does not cover must not sink the whole forecast.
            ZuneLog.w(TAG, "air quality unavailable for ${place.name}", e)
            null
        }
    }

    fun search(query: String, language: String): List<WeatherPlace> {
        val url = "$GEOCODING?name=${encode(query)}&count=10&language=$language&format=json"
        return parseSearch(getJson(url))
    }

    // --- Parsing (kept separate so it can be unit tested without a network) ------------------------

    internal fun parseForecast(json: JSONObject, place: WeatherPlace, nowSeconds: Long): WeatherSnapshot {
        val offset = json.optInt("utc_offset_seconds", 0)
        val current = json.optJSONObject("current")
            ?: throw WeatherException(R.string.weather_error_service, "no current block")
        val hourly = json.optJSONObject("hourly")
        val hourTimes = hourly?.optJSONArray("time")

        // Readings the service only publishes hourly are taken from the hour we are in.
        val hourNow = indexOfHour(hourTimes, nowSeconds)

        val now = WeatherNow(
            temperature = current.optDouble("temperature_2m", 0.0),
            apparent = current.optDouble("apparent_temperature", current.optDouble("temperature_2m", 0.0)),
            condition = WeatherCondition.fromWmo(current.optInt("weather_code", -1)),
            isDay = current.optInt("is_day", 1) == 1,
            humidity = current.optInt("relative_humidity_2m", 0),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            windDirection = current.optInt("wind_direction_10m", 0),
            pressure = current.optDouble("surface_pressure", 0.0),
            cloudCover = current.optInt("cloud_cover", 0),
            precipitation = current.optDouble("precipitation", 0.0),
            dewPoint = hourly.valueAt("dew_point_2m", hourNow),
            visibilityMeters = hourly.valueAt("visibility", hourNow),
            uvIndex = hourly.valueAt("uv_index", hourNow)
        )

        val hours = buildList {
            if (hourTimes == null || hourNow < 0) return@buildList
            var index = hourNow
            while (index < hourTimes.length() && size < HOURLY_HOURS) {
                add(
                    HourlyPoint(
                        timeSeconds = hourTimes.optLong(index),
                        temperature = hourly.valueAt("temperature_2m", index) ?: 0.0,
                        condition = WeatherCondition.fromWmo(
                            hourly.valueAt("weather_code", index)?.toInt() ?: -1
                        ),
                        precipitationChance = hourly.valueAt("precipitation_probability", index)?.toInt() ?: 0,
                        isDay = (hourly.valueAt("is_day", index)?.toInt() ?: 1) == 1
                    )
                )
                index++
            }
        }

        val daily = json.optJSONObject("daily")
        val dayTimes = daily?.optJSONArray("time")
        val days = buildList {
            if (dayTimes == null) return@buildList
            for (index in 0 until dayTimes.length()) {
                add(
                    DailyPoint(
                        timeSeconds = dayTimes.optLong(index),
                        minTemperature = daily.valueAt("temperature_2m_min", index) ?: 0.0,
                        maxTemperature = daily.valueAt("temperature_2m_max", index) ?: 0.0,
                        condition = WeatherCondition.fromWmo(daily.valueAt("weather_code", index)?.toInt() ?: -1),
                        precipitationChance = daily.valueAt("precipitation_probability_max", index)?.toInt() ?: 0,
                        precipitationSum = daily.valueAt("precipitation_sum", index) ?: 0.0,
                        windMax = daily.valueAt("wind_speed_10m_max", index) ?: 0.0,
                        uvIndexMax = daily.valueAt("uv_index_max", index),
                        sunriseSeconds = daily.valueAt("sunrise", index)?.toLong(),
                        sunsetSeconds = daily.valueAt("sunset", index)?.toLong()
                    )
                )
            }
        }

        return WeatherSnapshot(
            place = place,
            now = now,
            hourly = hours,
            daily = days,
            airQuality = null,
            utcOffsetSeconds = offset,
            fetchedAt = System.currentTimeMillis()
        )
    }

    internal fun parseAirQuality(json: JSONObject): AirQuality? {
        val current = json.optJSONObject("current") ?: return null
        return AirQuality(
            europeanAqi = current.numberOrNull("european_aqi")?.toInt(),
            pm25 = current.numberOrNull("pm2_5"),
            pm10 = current.numberOrNull("pm10"),
            ozone = current.numberOrNull("ozone"),
            nitrogenDioxide = current.numberOrNull("nitrogen_dioxide"),
            sulphurDioxide = current.numberOrNull("sulphur_dioxide"),
            carbonMonoxide = current.numberOrNull("carbon_monoxide"),
            birchPollen = current.numberOrNull("birch_pollen"),
            grassPollen = current.numberOrNull("grass_pollen"),
            ragweedPollen = current.numberOrNull("ragweed_pollen")
        )
    }

    internal fun parseSearch(json: JSONObject): List<WeatherPlace> {
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").ifBlank { return@mapNotNull null }
            val latitude = item.optDouble("latitude", Double.NaN)
            val longitude = item.optDouble("longitude", Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) return@mapNotNull null
            WeatherPlace(
                id = WeatherPlace.idOf(latitude, longitude),
                name = name,
                region = item.optString("admin1"),
                country = item.optString("country"),
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    /** Index of the hour [nowSeconds] falls in, or the first hour when the list starts later. */
    internal fun indexOfHour(times: JSONArray?, nowSeconds: Long): Int {
        if (times == null || times.length() == 0) return -1
        var best = 0
        for (index in 0 until times.length()) {
            if (times.optLong(index) <= nowSeconds) best = index else break
        }
        return best
    }

    // --- HTTP -------------------------------------------------------------------------------------

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                ZuneLog.w(TAG, "http $status for $url: $detail")
                throw WeatherException(
                    if (status == 429) R.string.weather_error_busy else R.string.weather_error_service,
                    detail
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } catch (e: JSONException) {
            throw WeatherException(R.string.weather_error_service, e.message, e)
        } catch (e: IOException) {
            throw WeatherException(R.string.weather_error_network, e.message, e)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/** Element [index] of the named array, or null when the service left it out. */
private fun JSONObject?.valueAt(key: String, index: Int): Double? {
    if (this == null || index < 0) return null
    val array = optJSONArray(key) ?: return null
    if (index >= array.length() || array.isNull(index)) return null
    return array.optDouble(index).takeIf { !it.isNaN() }
}

private fun JSONObject.numberOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
