package com.serkantkn.zunelauncher.ui.screens.weather

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.serkantkn.zunelauncher.data.model.WeatherSky
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The sky behind the weather hub — drawn, not photographed.
 *
 * Windows Phone's own weather app put a full-bleed photo behind the pivot. A launcher cannot ship
 * a photo for every condition without doubling its size, so the sky is painted instead: a gradient
 * for the hour of day, clouds drifting at three depths, and whatever is falling out of them. It
 * adds nothing to the download, fits any screen, and turning [animated] off leaves the same scene
 * standing still, which costs nothing at all.
 */
@Composable
fun WeatherSkyBackground(
    sky: WeatherSky,
    isDay: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
    /** Where the pivot is, so the sky slides a little behind the pages. */
    parallax: Float = 0f
) {
    val palette = remember(sky, isDay) { paletteOf(sky, isDay) }
    val scene = remember(sky, isDay) { sceneOf(sky, isDay) }

    // The sky is driven straight off the frame clock rather than off tween animations: a phone
    // with the system animation scale turned down would otherwise freeze it, and this sky is
    // meant to keep moving unless it is switched off in settings.
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var origin = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                if (origin < 0L) origin = frame
                elapsed.longValue = frame - origin
            }
        }
    }

    // The gradient cross-fades when the weather turns instead of cutting.
    val topColor by animateColorAsState(palette.top, tween(SKY_FADE_MILLIS), label = "sky_top")
    val middleColor by animateColorAsState(palette.middle, tween(SKY_FADE_MILLIS), label = "sky_middle")
    val bottomColor by animateColorAsState(palette.bottom, tween(SKY_FADE_MILLIS), label = "sky_bottom")

    Canvas(modifier = modifier.fillMaxSize()) {
        val now = elapsed.longValue

        /** Where one turn of a layer with this period stands, 0f..1f. */
        fun phase(periodMillis: Long): Float = (now % periodMillis).toFloat() / periodMillis

        val clouds = floatArrayOf(
            phase(CLOUD_NEAR_MILLIS),
            phase(CLOUD_MID_MILLIS),
            phase(CLOUD_FAR_MILLIS)
        )

        drawRect(
            brush = Brush.verticalGradient(
                0f to topColor,
                0.55f to middleColor,
                1f to bottomColor
            )
        )

        if (scene.starSeeds.isNotEmpty()) drawStars(scene, phase(TWINKLE_MILLIS))
        if (scene.hasSun) drawSunOrMoon(isDay, phase(SUN_MILLIS), parallax)
        if (scene.cloudSeeds.isNotEmpty()) drawClouds(scene, clouds, parallax, palette.cloud)
        if (scene.dropSeeds.isNotEmpty()) drawRain(scene, phase(RAIN_MILLIS))
        if (scene.flakeSeeds.isNotEmpty()) drawSnow(scene, phase(SNOW_MILLIS))
        if (scene.fogSeeds.isNotEmpty()) drawFog(scene, phase(FOG_MILLIS), palette.cloud)
        if (scene.lightning) drawLightning(phase(STORM_MILLIS))
        drawHaze(phase(HAZE_MILLIS), palette.cloud)

        // A lid over the top so the hub title and the pivot read against any sky.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.30f),
                1f to Color.Transparent,
                startY = 0f,
                endY = size.height * 0.24f
            ),
            size = Size(size.width, size.height * 0.24f)
        )

        // The gradient is measured against the canvas, not the rectangle, so its ends are named
        // explicitly — otherwise it starts part-way through and leaves a seam across the sky.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.28f),
                startY = size.height * 0.45f,
                endY = size.height
            ),
            topLeft = Offset(0f, size.height * 0.45f),
            size = Size(size.width, size.height * 0.55f)
        )
    }
}

// ════════════════════════════════════════════════════════════
// PALETTE
// ════════════════════════════════════════════════════════════

private data class SkyPalette(val top: Color, val middle: Color, val bottom: Color, val cloud: Color)

private fun paletteOf(sky: WeatherSky, isDay: Boolean): SkyPalette = when (sky) {
    WeatherSky.CLEAR -> if (isDay) {
        SkyPalette(Color(0xFF0B4DA2), Color(0xFF2E86DE), Color(0xFF8FC7F0), Color(0xFFFFFFFF))
    } else {
        SkyPalette(Color(0xFF04060F), Color(0xFF0C1733), Color(0xFF1D2B54), Color(0xFF9FB0D0))
    }

    WeatherSky.CLOUDY -> if (isDay) {
        SkyPalette(Color(0xFF2C557C), Color(0xFF5479A0), Color(0xFF9BB2C6), Color(0xFFF2F6FA))
    } else {
        SkyPalette(Color(0xFF070B14), Color(0xFF141E33), Color(0xFF27364F), Color(0xFFA9B6C9))
    }

    WeatherSky.OVERCAST -> if (isDay) {
        SkyPalette(Color(0xFF44505C), Color(0xFF6C7885), Color(0xFF9BA5B0), Color(0xFFE6EAEE))
    } else {
        SkyPalette(Color(0xFF070A0F), Color(0xFF141920), Color(0xFF242B34), Color(0xFF98A1AC))
    }

    WeatherSky.FOG -> if (isDay) {
        SkyPalette(Color(0xFF5B6670), Color(0xFF8A939C), Color(0xFFB9C0C6), Color(0xFFFFFFFF))
    } else {
        SkyPalette(Color(0xFF0A0E12), Color(0xFF181D23), Color(0xFF2B333B), Color(0xFFB3BAC1))
    }

    WeatherSky.RAIN -> if (isDay) {
        SkyPalette(Color(0xFF223449), Color(0xFF3F5872), Color(0xFF6E8399), Color(0xFFD8E1EA))
    } else {
        SkyPalette(Color(0xFF05080D), Color(0xFF101823), Color(0xFF1E2A38), Color(0xFF8E9AA8))
    }

    WeatherSky.SNOW -> if (isDay) {
        SkyPalette(Color(0xFF556879), Color(0xFF8194A6), Color(0xFFB9C7D6), Color(0xFFFFFFFF))
    } else {
        SkyPalette(Color(0xFF080C13), Color(0xFF161E2A), Color(0xFF283749), Color(0xFFDDE5EE))
    }

    WeatherSky.STORM -> SkyPalette(
        Color(0xFF0A0B12),
        Color(0xFF191C2B),
        Color(0xFF2C3046),
        Color(0xFF8B93A8)
    )
}

// ════════════════════════════════════════════════════════════
// SCENE
// ════════════════════════════════════════════════════════════

/**
 * The pieces on screen for one condition. Positions are random but fixed per condition, so the sky
 * does not reshuffle itself every time the hub recomposes.
 */
private class SkyScene(
    clouds: Int,
    rainDrops: Int,
    snowFlakes: Int,
    fogBands: Int,
    stars: Int,
    val hasSun: Boolean,
    val lightning: Boolean,
    seed: Int
) {
    private val random = Random(seed)
    val cloudSeeds = List(clouds) { seedTriple() }
    val dropSeeds = List(rainDrops) { seedTriple() }
    val flakeSeeds = List(snowFlakes) { seedTriple() }
    val starSeeds = List(stars) { seedTriple() }
    val fogSeeds = List(fogBands) { seedTriple() }

    private fun seedTriple() = Triple(random.nextFloat(), random.nextFloat(), random.nextFloat())
}

private fun sceneOf(sky: WeatherSky, isDay: Boolean): SkyScene = when (sky) {
    WeatherSky.CLEAR -> SkyScene(
        clouds = 3, rainDrops = 0, snowFlakes = 0, fogBands = 0,
        stars = if (isDay) 0 else 70, hasSun = true, lightning = false, seed = 11
    )

    WeatherSky.CLOUDY -> SkyScene(
        clouds = 6, rainDrops = 0, snowFlakes = 0, fogBands = 0,
        stars = if (isDay) 0 else 40, hasSun = true, lightning = false, seed = 22
    )

    WeatherSky.OVERCAST -> SkyScene(
        clouds = 8, rainDrops = 0, snowFlakes = 0, fogBands = 1,
        stars = 0, hasSun = false, lightning = false, seed = 33
    )

    WeatherSky.FOG -> SkyScene(
        clouds = 3, rainDrops = 0, snowFlakes = 0, fogBands = 5,
        stars = 0, hasSun = false, lightning = false, seed = 44
    )

    WeatherSky.RAIN -> SkyScene(
        clouds = 7, rainDrops = 110, snowFlakes = 0, fogBands = 0,
        stars = 0, hasSun = false, lightning = false, seed = 55
    )

    WeatherSky.SNOW -> SkyScene(
        clouds = 6, rainDrops = 0, snowFlakes = 90, fogBands = 1,
        stars = 0, hasSun = false, lightning = false, seed = 66
    )

    WeatherSky.STORM -> SkyScene(
        clouds = 9, rainDrops = 150, snowFlakes = 0, fogBands = 0,
        stars = 0, hasSun = false, lightning = true, seed = 77
    )
}

// ════════════════════════════════════════════════════════════
// LAYERS
// ════════════════════════════════════════════════════════════

private fun DrawScope.drawStars(scene: SkyScene, phase: Float) {
    scene.starSeeds.forEach { (x, y, seed) ->
        // Each star breathes at its own pace, none of them in step.
        val twinkle = 0.35f + 0.65f * abs(sin((phase + seed) * 2 * PI).toFloat())
        drawCircle(
            color = Color.White.copy(alpha = 0.7f * twinkle),
            radius = dp(0.7f + phase * 1.4f),
            center = Offset(x * size.width, y * size.height * 0.7f)
        )
    }
}

private fun DrawScope.drawSunOrMoon(isDay: Boolean, phase: Float, parallax: Float) {
    val wave = sin((phase * 2 * PI).toFloat())
    val drift = wave * dp(6f)
    val center = Offset(
        x = size.width * 0.84f - parallax * dp(30f),
        y = size.height * 0.13f + drift
    )
    val radius = dp(34f)
    val core = if (isDay) Color(0xFFFFE9A8) else Color(0xFFE8EEF8)

    // The halo breathes with the same wave that moves the disc.
    val halo = radius * (2.6f + 0.35f * wave)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(core.copy(alpha = 0.40f + 0.10f * wave), Color.Transparent),
            center = center,
            radius = halo
        ),
        radius = halo,
        center = center
    )
    drawCircle(color = core.copy(alpha = if (isDay) 0.80f else 0.75f), radius = radius, center = center)
    if (!isDay) {
        // A bite out of the disc turns the sun into a moon.
        drawCircle(
            color = Color(0xFF0C1733),
            radius = radius * 0.86f,
            center = Offset(center.x + radius * 0.42f, center.y - radius * 0.28f)
        )
    }
}

private fun DrawScope.drawClouds(scene: SkyScene, phases: FloatArray, parallax: Float, tint: Color) {
    scene.cloudSeeds.forEachIndexed { index, (seedX, seedY, seedScale) ->
        // Three depths: the ones in front are bigger, faster and more solid.
        val depth = index % 3
        val scale = (0.55f + seedScale * 0.55f) * (0.7f + depth * 0.3f)
        val alpha = 0.14f + depth * 0.08f
        val width = size.width * 0.38f * scale
        val height = width * 0.30f

        val travel = ((seedX + phases[depth]) % 1f) * (size.width + width * 2f) - width
        val x = travel - parallax * dp(12f + depth * 16f)
        val y = size.height * (0.05f + seedY * 0.42f)

        drawCloud(Offset(x, y), width, height, tint.copy(alpha = alpha))
    }
}

/**
 * A cloud is six overlapping lumps, each a radial gradient rather than a hard circle — that is
 * what keeps the edges soft enough to read as vapour instead of bubbles.
 */
private fun DrawScope.drawCloud(at: Offset, width: Float, height: Float, color: Color) {
    val lumps = listOf(
        Triple(0.14f, 0.66f, 0.62f),
        Triple(0.33f, 0.46f, 0.86f),
        Triple(0.52f, 0.38f, 1.00f),
        Triple(0.70f, 0.52f, 0.80f),
        Triple(0.88f, 0.70f, 0.56f),
        Triple(0.50f, 0.74f, 0.78f)
    )
    lumps.forEach { (dx, dy, r) ->
        val radius = height * r
        val center = Offset(at.x + width * dx, at.y + height * dy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = color.alpha * 0.45f), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

private fun DrawScope.drawRain(scene: SkyScene, phase: Float) {
    val slant = dp(5f)
    scene.dropSeeds.forEach { (seedX, seedLength, seedSpeed) ->
        // Not every drop falls at the same rate, so the curtain never looks like one sheet.
        val speed = 0.75f + seedSpeed * 0.6f
        val progress = (seedLength + phase * speed) % 1f
        val length = dp(10f + seedLength * 16f)
        val x = seedX * (size.width + slant * 4) - slant * 2
        val y = progress * (size.height + length) - length
        drawLine(
            color = Color.White.copy(alpha = 0.16f + seedSpeed * 0.18f),
            start = Offset(x + slant, y),
            end = Offset(x, y + length),
            strokeWidth = dp(1.4f),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(scene: SkyScene, phase: Float) {
    scene.flakeSeeds.forEach { (seedX, seedPhase, seedSize) ->
        val speed = 0.6f + seedSize * 0.7f
        val progress = (seedPhase + phase * speed) % 1f
        // Flakes sway as they fall instead of dropping straight down.
        val sway = sin((progress * 4f + seedPhase) * 2 * PI).toFloat() * dp(14f)
        drawCircle(
            color = Color.White.copy(alpha = 0.45f + seedSize * 0.4f),
            radius = dp(1.4f + seedSize * 2.4f),
            center = Offset(seedX * size.width + sway, progress * (size.height + 20f) - 10f)
        )
    }
}

private fun DrawScope.drawFog(scene: SkyScene, phase: Float, tint: Color) {
    scene.fogSeeds.forEachIndexed { index, (seedX, seedY, seedHeight) ->
        val speed = 0.7f + index * 0.16f
        val bandHeight = size.height * (0.06f + seedHeight * 0.10f)
        val y = size.height * (0.25f + seedY * 0.6f)
        val offset = ((seedX + phase * speed) % 1f) * size.width * 2f - size.width * 0.5f
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to tint.copy(alpha = 0.16f),
                1f to Color.Transparent,
                startX = offset - size.width * 0.5f,
                endX = offset + size.width
            ),
            topLeft = Offset(0f, y),
            size = Size(size.width, bandHeight)
        )
    }
}

/**
 * A wide band of light that drifts across the whole sky. It is what stops a clear day from looking
 * like a still gradient when there is almost nothing else moving.
 */
private fun DrawScope.drawHaze(phase: Float, tint: Color) {
    val travel = phase * size.width * 2.4f - size.width * 0.7f
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to tint.copy(alpha = 0.05f),
            1f to Color.Transparent,
            startX = travel - size.width * 0.6f,
            endX = travel + size.width * 0.6f
        )
    )
}

private fun DrawScope.drawLightning(phase: Float) {
    // Two flashes per beat, each a quick white blink that fades out.
    val beat = phase
    val strike = when {
        beat < 0.035f -> 1f - beat / 0.035f
        beat in 0.06f..0.085f -> 1f - (beat - 0.06f) / 0.025f
        else -> 0f
    }
    if (strike <= 0f) return
    drawRect(color = Color.White.copy(alpha = 0.30f * strike))
}

// ════════════════════════════════════════════════════════════

/*
 * How long one turn of each layer takes. These are the numbers that decide whether the sky reads
 * as weather or as a still picture: a cloud should cross in a minute or two, a raindrop in about a
 * second.
 */
private const val CLOUD_NEAR_MILLIS = 34_000L
private const val CLOUD_MID_MILLIS = 52_000L
private const val CLOUD_FAR_MILLIS = 78_000L
private const val RAIN_MILLIS = 1_100L
private const val SNOW_MILLIS = 9_000L
private const val FOG_MILLIS = 45_000L
private const val TWINKLE_MILLIS = 3_400L
private const val STORM_MILLIS = 7_000L
private const val SUN_MILLIS = 24_000L
private const val HAZE_MILLIS = 26_000L
private const val SKY_FADE_MILLIS = 900

/** dp inside a draw scope, where only pixels exist. */
private fun DrawScope.dp(value: Float): Float = value * density
