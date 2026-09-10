package com.serkantkn.zunelauncher.ui.screens.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AirQuality
import com.serkantkn.zunelauncher.data.model.DailyPoint
import com.serkantkn.zunelauncher.data.model.HourlyPoint
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherCondition
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSky
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.data.model.WindUnit
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The pages of the weather hub. Everything here is drawn over the painted sky, so the ink is
 * white rather than the theme's, and panels are the faint translucent blocks Windows Phone used
 * over photographic backgrounds.
 */

private val Ink = Color.White
private val InkMuted = Color.White.copy(alpha = 0.72f)
private val InkDim = Color.White.copy(alpha = 0.5f)
private val PanelFill = Color.White.copy(alpha = 0.10f)
private val PanelStroke = Color.White.copy(alpha = 0.18f)

// ════════════════════════════════════════════════════════════
// TODAY
// ════════════════════════════════════════════════════════════

@Composable
fun WeatherTodayPage(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    updatesLeft: Int,
    dailyAllowance: Int,
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    val today = snapshot.daily.firstOrNull()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        Text(
            text = snapshot.place.name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Light),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (snapshot.place.subtitle.isNotBlank()) {
            Text(
                text = snapshot.place.subtitle.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = InkDim,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${temperatureUnit.of(snapshot.now.temperature)}°",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 96.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-4).sp
                ),
                color = Ink,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = conditionIcon(snapshot.now.condition, snapshot.now.isDay),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(46.dp)
            )
        }

        Text(
            text = stringResource(snapshot.now.condition.labelRes),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Light),
            color = Ink
        )
        Text(
            text = stringResource(
                R.string.weather_feels_like,
                "${temperatureUnit.of(snapshot.now.apparent)}°"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
        )
        if (today != null) {
            Text(
                text = stringResource(
                    R.string.weather_high_low,
                    "${temperatureUnit.of(today.maxTemperature)}°",
                    "${temperatureUnit.of(today.minTemperature)}°"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.weather_updated_at, formatAt(snapshot.fetchedAt / 1000, snapshot.utcOffsetSeconds, "HH:mm")),
            style = MaterialTheme.typography.labelSmall,
            color = InkDim
        )
        if (!isPremium) {
            Text(
                text = stringResource(R.string.weather_quota_left, updatesLeft, dailyAllowance),
                style = MaterialTheme.typography.labelSmall,
                color = InkDim
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (snapshot.hourly.isNotEmpty()) {
            HourlyStrip(
                hours = snapshot.hourly,
                offsetSeconds = snapshot.utcOffsetSeconds,
                unit = temperatureUnit
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (today?.sunriseSeconds != null && today.sunsetSeconds != null) {
            SunArc(
                sunriseSeconds = today.sunriseSeconds,
                sunsetSeconds = today.sunsetSeconds,
                nowSeconds = System.currentTimeMillis() / 1000,
                offsetSeconds = snapshot.utcOffsetSeconds
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HourlyStrip(hours: List<HourlyPoint>, offsetSeconds: Int, unit: TemperatureUnit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        itemsIndexed(hours, key = { _, hour -> hour.timeSeconds }) { index, hour ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(62.dp)
                    .background(if (index == 0) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = if (index == 0) stringResource(R.string.weather_now)
                    else formatAt(hour.timeSeconds, offsetSeconds, "HH:mm"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = InkMuted,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Icon(
                    imageVector = conditionIcon(hour.condition, hour.isDay),
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${unit.of(hour.temperature)}°",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = Ink
                )
                if (hour.precipitationChance >= 10) {
                    Text(
                        text = stringResource(R.string.weather_percent, hour.precipitationChance),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color(0xFF9CD2FF)
                    )
                }
            }
        }
    }
}

/** The daylight arc: where the sun is between rising and setting, as Windows Phone drew it. */
@Composable
private fun SunArc(sunriseSeconds: Long, sunsetSeconds: Long, nowSeconds: Long, offsetSeconds: Int) {
    val span = (sunsetSeconds - sunriseSeconds).coerceAtLeast(1)
    val progress = ((nowSeconds - sunriseSeconds).toFloat() / span).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.weather_daylight),
            style = MaterialTheme.typography.labelSmall,
            color = InkDim
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            // Half an ellipse that exactly fills the box: a circle as wide as the screen would
            // tower far above it.
            val stroke = 2.dp.toPx()
            val inset = 6.dp.toPx()
            val boxWidth = size.width - inset * 2
            val boxHeight = size.height - inset

            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(boxWidth, boxHeight * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.85f),
                startAngle = 180f,
                sweepAngle = 180f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(boxWidth, boxHeight * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            val angle = Math.PI * progress
            val sun = Offset(
                x = inset + boxWidth / 2f - (boxWidth / 2f) * cos(angle).toFloat(),
                y = inset + boxHeight - boxHeight * sin(angle).toFloat()
            )
            drawCircle(color = Color(0xFFFFE9A8).copy(alpha = 0.35f), radius = 11.dp.toPx(), center = sun)
            drawCircle(color = Color(0xFFFFE9A8), radius = 5.dp.toPx(), center = sun)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SunLabel(stringResource(R.string.weather_sunrise), formatAt(sunriseSeconds, offsetSeconds, "HH:mm"))
            SunLabel(stringResource(R.string.weather_sunset), formatAt(sunsetSeconds, offsetSeconds, "HH:mm"))
        }
    }
}

@Composable
private fun SunLabel(title: String, value: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = InkDim)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
            color = Ink
        )
    }
}

// ════════════════════════════════════════════════════════════
// TEN DAYS
// ════════════════════════════════════════════════════════════

@Composable
fun WeatherDaysPage(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val coldest = snapshot.daily.minOfOrNull { it.minTemperature } ?: 0.0
    val warmest = snapshot.daily.maxOfOrNull { it.maxTemperature } ?: 1.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 90.dp
        )
    ) {
        itemsIndexed(snapshot.daily, key = { _, day -> day.timeSeconds }) { index, day ->
            DayRow(
                day = day,
                isToday = index == 0,
                coldest = coldest,
                warmest = warmest,
                offsetSeconds = snapshot.utcOffsetSeconds,
                unit = temperatureUnit
            )
        }
    }
}

@Composable
private fun DayRow(
    day: DailyPoint,
    isToday: Boolean,
    coldest: Double,
    warmest: Double,
    offsetSeconds: Int,
    unit: TemperatureUnit
) {
    val span = (warmest - coldest).coerceAtLeast(1.0)
    val start = ((day.minTemperature - coldest) / span).toFloat().coerceIn(0f, 1f)
    val end = ((day.maxTemperature - coldest) / span).toFloat().coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)
    ) {
        Text(
            text = if (isToday) stringResource(R.string.common_today)
            else formatAt(day.timeSeconds, offsetSeconds, "EEEE").lowercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isToday) FontWeight.Medium else FontWeight.Light
            ),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(78.dp)
        )
        Icon(
            imageVector = conditionIcon(day.condition, true),
            contentDescription = stringResource(day.condition.labelRes),
            tint = Ink,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = if (day.precipitationChance >= 10)
                stringResource(R.string.weather_percent, day.precipitationChance) else "",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color(0xFF9CD2FF),
            maxLines = 1,
            modifier = Modifier.width(38.dp).padding(start = 6.dp)
        )
        Text(
            text = "${unit.of(day.minTemperature)}°",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
            color = InkMuted,
            maxLines = 1,
            modifier = Modifier.width(34.dp)
        )
        // The bar puts the day's range against the range of the whole ten days.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .padding(horizontal = 6.dp)
        ) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(size.width * start, 0f),
                size = Size((size.width * (end - start)).coerceAtLeast(size.height), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            )
        }
        Text(
            text = "${unit.of(day.maxTemperature)}°",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            modifier = Modifier.width(34.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════
// DETAILS
// ════════════════════════════════════════════════════════════

@Composable
fun WeatherDetailsPage(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    modifier: Modifier = Modifier
) {
    val now = snapshot.now
    val windSuffix = stringResource(windUnit.suffixRes)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile(
                title = stringResource(R.string.weather_wind),
                value = "${windUnit.of(now.windSpeed).roundToInt()} $windSuffix",
                modifier = Modifier.weight(1f)
            ) {
                // The needle points the way the wind is going.
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(22.dp).rotate(now.windDirection.toFloat())
                )
            }
            DetailTile(
                title = stringResource(R.string.weather_humidity),
                value = stringResource(R.string.weather_percent, now.humidity),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile(
                title = stringResource(R.string.weather_pressure),
                value = "${now.pressure.roundToInt()} hPa",
                modifier = Modifier.weight(1f)
            )
            DetailTile(
                title = stringResource(R.string.weather_cloud),
                value = stringResource(R.string.weather_percent, now.cloudCover),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile(
                title = stringResource(R.string.weather_uv),
                value = now.uvIndex?.let { it.roundToInt().toString() } ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailTile(
                title = stringResource(R.string.weather_dew),
                value = now.dewPoint?.let { "${temperatureUnit.of(it)}°" } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile(
                title = stringResource(R.string.weather_visibility),
                value = now.visibilityMeters?.let { "${(it / 1000).roundToInt()} km" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailTile(
                title = stringResource(R.string.weather_precip_sum),
                value = "${"%.1f".format(Locale.getDefault(), snapshot.daily.firstOrNull()?.precipitationSum ?: 0.0)} mm",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.weather_source),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = InkDim
        )
        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun DetailTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    glyph: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .aspectRatio(1.35f)
            .background(PanelFill)
            .border(0.5.dp, PanelStroke)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            glyph?.invoke()
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Light),
                color = Ink,
                maxLines = 1
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// AIR QUALITY
// ════════════════════════════════════════════════════════════

@Composable
fun WeatherAirPage(
    air: AirQuality?,
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isPremium) {
        LockedPage(
            title = stringResource(R.string.weather_premium_locked),
            body = stringResource(R.string.weather_premium_locked_air),
            modifier = modifier
        )
        return
    }
    if (air == null) {
        EmptyNote(stringResource(R.string.weather_air_unavailable), modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        air.europeanAqi?.let { aqi ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AqiRing(aqi)
                Spacer(modifier = Modifier.width(18.dp))
                Column {
                    Text(
                        text = stringResource(R.string.weather_aqi),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                    Text(
                        text = stringResource(aqiLabel(aqi)),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Light),
                        color = Ink
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        PollutantBar(stringResource(R.string.weather_pm25), air.pm25, 50.0)
        PollutantBar(stringResource(R.string.weather_pm10), air.pm10, 100.0)
        PollutantBar(stringResource(R.string.weather_ozone), air.ozone, 180.0)
        PollutantBar(stringResource(R.string.weather_no2), air.nitrogenDioxide, 200.0)
        PollutantBar(stringResource(R.string.weather_so2), air.sulphurDioxide, 350.0)
        PollutantBar(stringResource(R.string.weather_co), air.carbonMonoxide, 10000.0)

        val pollen = listOfNotNull(
            air.birchPollen?.let { stringResource(R.string.weather_pollen_birch) to it },
            air.grassPollen?.let { stringResource(R.string.weather_pollen_grass) to it },
            air.ragweedPollen?.let { stringResource(R.string.weather_pollen_ragweed) to it }
        )
        if (pollen.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.weather_pollen),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            pollen.forEach { (name, value) -> PollutantBar(name, value, 50.0) }
        }
        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun AqiRing(aqi: Int) {
    val fraction = (aqi / 120f).coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = aqiColor(aqi),
                startAngle = 135f,
                sweepAngle = 270f * fraction,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = aqi.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, fontWeight = FontWeight.Light),
            color = Ink
        )
    }
}

@Composable
private fun PollutantBar(name: String, value: Double?, ceiling: Double) {
    if (value == null) return
    val fraction = (value / ceiling).toFloat().coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = name, style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 1)
            Text(
                text = "%.1f".format(Locale.getDefault(), value),
                style = MaterialTheme.typography.bodySmall,
                color = Ink,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            drawRect(color = Color.White.copy(alpha = 0.16f))
            drawRect(color = Color.White.copy(alpha = 0.85f), size = Size(size.width * fraction, size.height))
        }
    }
}

private fun aqiLabel(aqi: Int): Int = when {
    aqi <= 20 -> R.string.weather_aqi_good
    aqi <= 40 -> R.string.weather_aqi_fair
    aqi <= 60 -> R.string.weather_aqi_moderate
    aqi <= 80 -> R.string.weather_aqi_poor
    aqi <= 100 -> R.string.weather_aqi_very_poor
    else -> R.string.weather_aqi_extremely_poor
}

private fun aqiColor(aqi: Int): Color = when {
    aqi <= 20 -> Color(0xFF6BD97F)
    aqi <= 40 -> Color(0xFFB6E36A)
    aqi <= 60 -> Color(0xFFF2D047)
    aqi <= 80 -> Color(0xFFF29D47)
    aqi <= 100 -> Color(0xFFE8604C)
    else -> Color(0xFFB44BC9)
}

// ════════════════════════════════════════════════════════════
// PLACES
// ════════════════════════════════════════════════════════════

@Composable
fun WeatherPlacesPage(
    summaries: List<Pair<WeatherPlace, WeatherSnapshot?>>,
    results: List<WeatherPlace>,
    isSearching: Boolean,
    canAddPlace: Boolean,
    temperatureUnit: TemperatureUnit,
    onSearch: (String) -> Unit,
    onAdd: (WeatherPlace) -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUseMyLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 90.dp
        )
    ) {
        item(key = "search") {
            ZuneSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = stringResource(R.string.weather_search_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (query.isNotBlank()) {
            if (results.isEmpty() && !isSearching) {
                item(key = "search_empty") {
                    Text(
                        text = stringResource(R.string.weather_search_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(results, key = { "result_${it.id}" }) { place ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            query = ""
                            onAdd(place)
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = InkDim, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = place.name, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 1)
                        if (place.subtitle.isNotBlank()) {
                            Text(
                                text = place.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkDim,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "use_location") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUseMyLocation() }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.weather_use_my_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }

        items(summaries, key = { "place_${it.first.id}" }) { (place, snapshot) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(place.id) }
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (place.isHere) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = InkMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (place.subtitle.isNotBlank()) {
                        Text(
                            text = place.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkDim,
                            maxLines = 1
                        )
                    }
                }
                snapshot?.let {
                    Icon(
                        imageVector = conditionIcon(it.now.condition, it.now.isDay),
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${temperatureUnit.of(it.now.temperature)}°",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = Ink
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.weather_remove_place),
                    tint = InkDim,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onRemove(place.id) }
                )
            }
        }

        if (!canAddPlace) {
            item(key = "limit") {
                Text(
                    text = stringResource(R.string.weather_place_limit),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkDim,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// SHARED
// ════════════════════════════════════════════════════════════

@Composable
fun LockedPage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(PanelFill, CircleShape)
                .border(0.5.dp, PanelStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Light),
            color = Ink
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}

/** The glyph for a condition; night has its own face for the clear sky. */
fun conditionIcon(condition: WeatherCondition, isDay: Boolean): ImageVector = when (condition.sky) {
    WeatherSky.CLEAR -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightlightRound
    WeatherSky.CLOUDY -> Icons.Default.WbCloudy
    WeatherSky.OVERCAST -> Icons.Default.Cloud
    WeatherSky.FOG -> Icons.Default.BlurOn
    WeatherSky.RAIN -> Icons.Default.WaterDrop
    WeatherSky.SNOW -> Icons.Default.AcUnit
    WeatherSky.STORM -> Icons.Default.Bolt
}

/** Formats an instant as the people living at that place would read it. */
fun formatAt(seconds: Long, offsetSeconds: Int, pattern: String): String {
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    format.timeZone = SimpleTimeZone(offsetSeconds * 1000, "place")
    return format.format(Date(seconds * 1000))
}

/** Kept for the wind needle: 0° is north, and the arrow is drawn pointing east. */
internal fun windRotation(direction: Int): Float = ((direction + 90) % 360).toFloat()

internal fun temperatureSpread(min: Double, max: Double): Double = abs(max - min)
