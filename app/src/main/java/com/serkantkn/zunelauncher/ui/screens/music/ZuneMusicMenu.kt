package com.serkantkn.zunelauncher.ui.screens.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.animation.ZUNE_ENTER_MILLIS
import com.serkantkn.zunelauncher.ui.animation.ZUNE_EXIT_MILLIS
import com.serkantkn.zunelauncher.ui.animation.ZUNE_OPEN_AT_MILLIS
import com.serkantkn.zunelauncher.ui.animation.zuneListMotion
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One word of the Zune music menu: the page it opens, and what it says. */
data class ZuneMusicEntry(val page: Int, val label: String)

/**
 * The music hub as the Zune HD had it: no pivot, but the kinds of music one under another in large
 * type - artists, albums, songs, genres, playlists, videos - the words sliding in from the left. A
 * word touched zooms through as the list's words do, and its list comes in behind it.
 */
@Composable
fun ZuneMusicMenu(
    entries: List<ZuneMusicEntry>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = if (LocalZuneColors.current.isDark) Color.White else Color.Black
    val animationsOn = LocalAnimationsEnabled.current
    val progress = remember { Animatable(if (animationsOn) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (animationsOn) progress.animateTo(1f, tween(ZUNE_ENTER_MILLIS, easing = LinearEasing))
    }

    fun pick(page: Int) {
        if (picked != null) return
        picked = page
        if (!animationsOn) {
            onPick(page)
            return
        }
        scope.launch {
            launch { progress.animateTo(2f, tween(ZUNE_EXIT_MILLIS, easing = LinearEasing)) }
            delay(ZUNE_OPEN_AT_MILLIS)
            onPick(page)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal, bottom = 120.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            Text(
                text = entry.label,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = MENU_SIZE,
                    lineHeight = MENU_SIZE * 1.12f,
                    letterSpacing = (-1).sp
                ),
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .zuneListMotion({ progress.value }, index, isClicked = picked == entry.page)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { pick(entry.page) }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

/**
 * The music hub's name - or, in a list, the list's - large and heavy across the top, as the other
 * main hubs of the Zune layout have it: where "music" or "videos" lands from the list. A new name
 * slides in from the right as its list does; [arrived] runs 0 -> 1.
 */
@Composable
fun ColumnScope.ZuneMusicTitle(title: String, arrived: () -> Float) {
    val slidePx = with(LocalDensity.current) { TITLE_SLIDE.toPx() }
    com.serkantkn.zunelauncher.ui.animation.ZuneHubFlightTitle(
        title = title,
        key = MUSIC_TITLE_KEY,
        modifier = Modifier
            .padding(top = com.serkantkn.zunelauncher.ui.animation.ZUNE_HUB_TITLE_TOP, bottom = 14.dp, start = ZuneDimens.ScreenPaddingHorizontal)
            .graphicsLayer {
                val eased = ArriveEasing.transform(arrived().coerceIn(0f, 1f))
                translationX = slidePx * (1f - eased)
                alpha = eased
            }
    )
}

/** The key the music hub's title answers to, for "music" and "videos" alike. */
const val MUSIC_TITLE_KEY = "MUSIC"

/** A list's page coming in behind its word: from the right, a little behind the title. */
fun Modifier.zuneMusicSection(arrived: () -> Float, slidePx: Float): Modifier = graphicsLayer {
    val value = arrived()
    if (value >= 1f) return@graphicsLayer
    val t = ((value - SECTION_LAG) / (1f - SECTION_LAG)).coerceIn(0f, 1f)
    val eased = ArriveEasing.transform(t)
    translationX = slidePx * (1f - eased)
    alpha = eased
}

private val MENU_SIZE = 54.sp
private val TITLE_SLIDE = 140.dp
private const val SECTION_LAG = 0.12f
private val ArriveEasing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
