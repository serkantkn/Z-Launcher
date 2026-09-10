package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the weather hub keeps.
 *
 * Numbers are always stored in metric (°C, km/h, mm, hPa) no matter what the user reads them in,
 * so a cached forecast stays correct when the unit preference changes. The conversion happens at
 * the last moment, on screen.
 */

// ════════════════════════════════════════════════════════════
// CONDITION
// ════════════════════════════════════════════════════════════

/** How the sky is drawn. Several conditions share one look. */
enum class WeatherSky { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, STORM }

/**
 * WMO weather interpretation codes, grouped the way people talk about them.
 * https://open-meteo.com/en/docs — code table at the bottom of the page.
 */
enum class WeatherCondition(
    @StringRes val labelRes: Int,
    val sky: WeatherSky
) {
    CLEAR(R.string.weather_cond_clear, WeatherSky.CLEAR),
    MAINLY_CLEAR(R.string.weather_cond_mainly_clear, WeatherSky.CLEAR),
    PARTLY_CLOUDY(R.string.weather_cond_partly_cloudy, WeatherSky.CLOUDY),
    OVERCAST(R.string.weather_cond_overcast, WeatherSky.OVERCAST),
    FOG(R.string.weather_cond_fog, WeatherSky.FOG),
    DRIZZLE(R.string.weather_cond_drizzle, WeatherSky.RAIN),
    FREEZING_DRIZZLE(R.string.weather_cond_freezing_drizzle, WeatherSky.RAIN),
    RAIN(R.string.weather_cond_rain, WeatherSky.RAIN),
    HEAVY_RAIN(R.string.weather_cond_heavy_rain, WeatherSky.RAIN),
    FREEZING_RAIN(R.string.weather_cond_freezing_rain, WeatherSky.RAIN),
    SNOW(R.string.weather_cond_snow, WeatherSky.SNOW),
    HEAVY_SNOW(R.string.weather_cond_heavy_snow, WeatherSky.SNOW),
    SNOW_GRAINS(R.string.weather_cond_snow_grains, WeatherSky.SNOW),
    RAIN_SHOWERS(R.string.weather_cond_rain_showers, WeatherSky.RAIN),
    SNOW_SHOWERS(R.string.weather_cond_snow_showers, WeatherSky.SNOW),
    THUNDERSTORM(R.string.weather_cond_thunderstorm, WeatherSky.STORM),
    THUNDERSTORM_HAIL(R.string.weather_cond_thunderstorm_hail, WeatherSky.STORM),
    UNKNOWN(R.string.weather_cond_unknown, WeatherSky.CLOUDY);

    companion object {
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61, 63 -> RAIN
            65 -> HEAVY_RAIN
            66, 67 -> FREEZING_RAIN
            71, 73 -> SNOW
            75 -> HEAVY_SNOW
            77 -> SNOW_GRAINS
            80, 81, 82 -> RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95 -> THUNDERSTORM
            96, 99 -> THUNDERSTORM_HAIL
            else -> UNKNOWN
        }
    }
}

// ════════════════════════════════════════════════════════════
// PLACES
// ════════════════════════════════════════════════════════════

/** A saved place. [isHere] marks the one that follows the phone's own position. */
data class WeatherPlace(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    val isHere: Boolean = false
) {
    /** "Kadıköy, İstanbul" — the second line under the temperature. */
    val subtitle: String get() = listOf(region, country).filter { it.isNotBlank() }.joinToString(", ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("region", region)
        .put("country", country)
        .put("lat", latitude)
        .put("lon", longitude)
        .put("here", isHere)

    companion object {
        const val HERE_ID = "here"

        fun fromJson(json: JSONObject): WeatherPlace? {
            val id = json.optString("id").ifBlank { return null }
            if (!json.has("lat") || !json.has("lon")) return null
            return WeatherPlace(
                id = id,
                name = json.optString("name"),
                region = json.optString("region"),
                country = json.optString("country"),
                latitude = json.optDouble("lat", 0.0),
                longitude = json.optDouble("lon", 0.0),
                isHere = json.optBoolean("here", false)
            )
        }

        /** Places are addressed by coordinate, rounded so the same spot keeps the same id. */
        fun idOf(latitude: Double, longitude: Double): String =
            "%.3f,%.3f".format(java.util.Locale.US, latitude, longitude)
    }
}

// ════════════════════════════════════════════════════════════
// READINGS
// ════════════════════════════════════════════════════════════

/** Right now, at the place. */
data class WeatherNow(
    val temperature: Double,
    val apparent: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val humidity: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val pressure: Double,
    val cloudCover: Int,
    val precipitation: Double,
    val dewPoint: Double?,
    val visibilityMeters: Double?,
    val uvIndex: Double?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", temperature).put("a", apparent).put("c", condition.name).put("day", isDay)
        .put("h", humidity).put("ws", windSpeed).put("wd", windDirection).put("p", pressure)
        .put("cc", cloudCover).put("pr", precipitation)
        .putOpt("dp", dewPoint).putOpt("vis", visibilityMeters).putOpt("uv", uvIndex)

    companion object {
        fun fromJson(json: JSONObject): WeatherNow = WeatherNow(
            temperature = json.optDouble("t", 0.0),
            apparent = json.optDouble("a", 0.0),
            condition = conditionOf(json.optString("c")),
            isDay = json.optBoolean("day", true),
            humidity = json.optInt("h", 0),
            windSpeed = json.optDouble("ws", 0.0),
            windDirection = json.optInt("wd", 0),
            pressure = json.optDouble("p", 0.0),
            cloudCover = json.optInt("cc", 0),
            precipitation = json.optDouble("pr", 0.0),
            dewPoint = json.optDoubleOrNull("dp"),
            visibilityMeters = json.optDoubleOrNull("vis"),
            uvIndex = json.optDoubleOrNull("uv")
        )
    }
}

/** One hour of the strip on the "today" page. */
data class HourlyPoint(
    val timeSeconds: Long,
    val temperature: Double,
    val condition: WeatherCondition,
    val precipitationChance: Int,
    val isDay: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("s", timeSeconds).put("t", temperature).put("c", condition.name)
        .put("p", precipitationChance).put("day", isDay)

    companion object {
        fun fromJson(json: JSONObject): HourlyPoint = HourlyPoint(
            timeSeconds = json.optLong("s"),
            temperature = json.optDouble("t", 0.0),
            condition = conditionOf(json.optString("c")),
            precipitationChance = json.optInt("p", 0),
            isDay = json.optBoolean("day", true)
        )
    }
}

/** One row of the ten-day page. */
data class DailyPoint(
    val timeSeconds: Long,
    val minTemperature: Double,
    val maxTemperature: Double,
    val condition: WeatherCondition,
    val precipitationChance: Int,
    val precipitationSum: Double,
    val windMax: Double,
    val uvIndexMax: Double?,
    val sunriseSeconds: Long?,
    val sunsetSeconds: Long?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("s", timeSeconds).put("min", minTemperature).put("max", maxTemperature)
        .put("c", condition.name).put("p", precipitationChance).put("sum", precipitationSum)
        .put("w", windMax).putOpt("uv", uvIndexMax).putOpt("up", sunriseSeconds).putOpt("down", sunsetSeconds)

    companion object {
        fun fromJson(json: JSONObject): DailyPoint = DailyPoint(
            timeSeconds = json.optLong("s"),
            minTemperature = json.optDouble("min", 0.0),
            maxTemperature = json.optDouble("max", 0.0),
            condition = conditionOf(json.optString("c")),
            precipitationChance = json.optInt("p", 0),
            precipitationSum = json.optDouble("sum", 0.0),
            windMax = json.optDouble("w", 0.0),
            uvIndexMax = json.optDoubleOrNull("uv"),
            sunriseSeconds = json.optLongOrNull("up"),
            sunsetSeconds = json.optLongOrNull("down")
        )
    }
}

/** The air quality page. Pollen is only published for Europe, so every field may be missing. */
data class AirQuality(
    val europeanAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val nitrogenDioxide: Double?,
    val sulphurDioxide: Double?,
    val carbonMonoxide: Double?,
    val birchPollen: Double?,
    val grassPollen: Double?,
    val ragweedPollen: Double?
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("aqi", europeanAqi).putOpt("pm25", pm25).putOpt("pm10", pm10)
        .putOpt("o3", ozone).putOpt("no2", nitrogenDioxide).putOpt("so2", sulphurDioxide)
        .putOpt("co", carbonMonoxide).putOpt("birch", birchPollen)
        .putOpt("grass", grassPollen).putOpt("ragweed", ragweedPollen)

    companion object {
        fun fromJson(json: JSONObject): AirQuality = AirQuality(
            europeanAqi = json.optIntOrNull("aqi"),
            pm25 = json.optDoubleOrNull("pm25"),
            pm10 = json.optDoubleOrNull("pm10"),
            ozone = json.optDoubleOrNull("o3"),
            nitrogenDioxide = json.optDoubleOrNull("no2"),
            sulphurDioxide = json.optDoubleOrNull("so2"),
            carbonMonoxide = json.optDoubleOrNull("co"),
            birchPollen = json.optDoubleOrNull("birch"),
            grassPollen = json.optDoubleOrNull("grass"),
            ragweedPollen = json.optDoubleOrNull("ragweed")
        )
    }
}

/**
 * Everything fetched for one place in one go, plus when it was fetched. [utcOffsetSeconds] is the
 * place's own offset, so times are shown as the people living there would read them.
 */
data class WeatherSnapshot(
    val place: WeatherPlace,
    val now: WeatherNow,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val airQuality: AirQuality?,
    val utcOffsetSeconds: Int,
    val fetchedAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("place", place.toJson())
        .put("now", now.toJson())
        .put("hourly", JSONArray().apply { hourly.forEach { put(it.toJson()) } })
        .put("daily", JSONArray().apply { daily.forEach { put(it.toJson()) } })
        .putOpt("air", airQuality?.toJson())
        .put("offset", utcOffsetSeconds)
        .put("at", fetchedAt)

    companion object {
        fun fromJson(json: JSONObject): WeatherSnapshot? {
            val place = json.optJSONObject("place")?.let { WeatherPlace.fromJson(it) } ?: return null
            val now = json.optJSONObject("now") ?: return null
            return WeatherSnapshot(
                place = place,
                now = WeatherNow.fromJson(now),
                hourly = json.optJSONArray("hourly").mapObjects { HourlyPoint.fromJson(it) },
                daily = json.optJSONArray("daily").mapObjects { DailyPoint.fromJson(it) },
                airQuality = json.optJSONObject("air")?.let { AirQuality.fromJson(it) },
                utcOffsetSeconds = json.optInt("offset", 0),
                fetchedAt = json.optLong("at", 0L)
            )
        }
    }
}

/** A failure worth putting on screen, with the text to put there. */
class WeatherException(
    @StringRes val messageRes: Int,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(detail, cause)

// ════════════════════════════════════════════════════════════
// UNITS
// ════════════════════════════════════════════════════════════

enum class TemperatureUnit(@StringRes val labelRes: Int) {
    CELSIUS(R.string.weather_unit_celsius),
    FAHRENHEIT(R.string.weather_unit_fahrenheit);

    /** [celsius] as this unit, rounded for display. */
    fun of(celsius: Double): Int =
        if (this == CELSIUS) Math.round(celsius).toInt() else Math.round(celsius * 9 / 5 + 32).toInt()
}

enum class WindUnit(@StringRes val labelRes: Int, @StringRes val suffixRes: Int) {
    KMH(R.string.weather_unit_kmh, R.string.weather_suffix_kmh),
    MS(R.string.weather_unit_ms, R.string.weather_suffix_ms),
    MPH(R.string.weather_unit_mph, R.string.weather_suffix_mph);

    /** [kmh] as this unit. */
    fun of(kmh: Double): Double = when (this) {
        KMH -> kmh
        MS -> kmh / 3.6
        MPH -> kmh / 1.609344
    }
}

// ════════════════════════════════════════════════════════════
// JSON HELPERS
// ════════════════════════════════════════════════════════════

private fun conditionOf(name: String): WeatherCondition =
    runCatching { WeatherCondition.valueOf(name) }.getOrDefault(WeatherCondition.UNKNOWN)

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

internal fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}
