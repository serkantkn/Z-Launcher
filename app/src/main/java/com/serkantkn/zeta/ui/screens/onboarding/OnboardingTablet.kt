package com.serkantkn.zeta.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.util.OnboardingStep

/**
 * The tour on a tablet: Windows 8's first run rather than Windows Phone's.
 *
 * Windows 8 set itself up on a field of the colour being chosen, white type straight on it, the
 * page's name large at the top left beside a ringed arrow back, what the page is for in a narrow
 * column on the left and the choices to its right, and a solid button in the corner to go on.
 * Picking a colour repainted the whole field at once, which is still the quickest way to show
 * that a choice took. The pages are the phone's - the same steps, asking the same things - laid
 * out for a screen twice as wide.
 *
 * The lessons leave the field and play on the tablet's own Start, full size, with what they ask
 * for on a band of the colour along the bottom.
 */

/** What the tablet's pages need from the tour around them: where they are, and the ways on. */
internal data class TourChrome(
    val count: Int,
    val index: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onSkip: () -> Unit
)

/** Set only by the tablet's tour; a page that finds it lays itself out the Windows 8 way. */
internal val LocalTourChrome = staticCompositionLocalOf<TourChrome?> { null }

/** The colour the page stands on, which its buttons are cut out of. */
internal val LocalTourGround = staticCompositionLocalOf { Color.Black }

/** Type on the accent: white, unless the accent is so light that only black reads on it. */
internal fun inkOn(ground: Color): Color = if (ground.luminance() > INK_FLIP_LUMINANCE) Color.Black else Color.White

/** Where the page's title and body start, clear of the ring back beside the title. */
private val TABLET_CONTENT_START = 120.dp
private val TABLET_SIDE = 56.dp

@Composable
internal fun TabletTour(
    tour: FirstRunScreen.Tour,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    page: @Composable (OnboardingStep) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val animate = LocalAnimationsEnabled.current
    val accent = zuneColors.accentColor
    val themeGround = if (zuneColors.isDark) Color(0xFF0A0A0A) else Color.White

    // -- The page turn --
    //
    // Windows 8 did not swing its pages on a hinge: the old one let go, and the new one slid a
    // short way in from the side it was coming from while it faded up. Forward comes from the
    // right, back from the left.
    var shownStep by remember { mutableStateOf(tour.step) }
    var shownIndex by remember { mutableIntStateOf(tour.index) }
    val enter = remember { Animatable(1f) }
    var fromRight by remember { mutableStateOf(true) }

    LaunchedEffect(tour.step) {
        if (tour.step == shownStep) return@LaunchedEffect
        fromRight = tour.index >= shownIndex
        if (animate) enter.animateTo(0f, tween(PAGE_OUT_MS, easing = LinearOutSlowInEasing)) else enter.snapTo(0f)
        shownStep = tour.step
        shownIndex = tour.index
        if (animate) enter.animateTo(1f, tween(PAGE_IN_MS, easing = FastOutSlowInEasing)) else enter.snapTo(1f)
    }

    // The field: the accent for the pages that ask something, the Start's own ground for a lesson.
    val ground by animateColorAsState(
        targetValue = if (shownStep.isLesson) themeGround else accent,
        animationSpec = tween(if (animate) GROUND_MS else 0),
        label = "tour_ground"
    )

    val chrome = TourChrome(
        count = tour.steps.size,
        index = tour.index,
        isFirst = tour.isFirst,
        isLast = tour.isLast,
        onBack = onBack,
        onNext = onNext,
        onSkip = onSkip
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ground)
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = enter.value
                    alpha = p
                    val travel = PAGE_TRAVEL_DP * density * (1f - p)
                    translationX = if (fromRight) travel else -travel
                }
        ) {
            CompositionLocalProvider(LocalTourChrome provides chrome) {
                if (shownStep.isLesson) {
                    // A lesson is the launcher, in the launcher's colours.
                    page(shownStep)
                } else {
                    OnAccent(accent) { page(shownStep) }
                }
            }
        }
    }
}

/**
 * Everything below drawn for the accent field: the words in the ink, and every place the pages
 * would use the accent - a picked square, a link, a button's ring - in the ink too, since the
 * accent there would be invisible.
 */
@Composable
private fun OnAccent(accent: Color, content: @Composable () -> Unit) {
    val ink = inkOn(accent)
    val zuneColors = LocalZuneColors.current
    CompositionLocalProvider(
        LocalTourGround provides accent,
        LocalZuneColors provides zuneColors.copy(
            accentColor = ink,
            accentLink = ink,
            textMuted = ink.copy(alpha = 0.82f),
            textDim = ink.copy(alpha = 0.5f)
        )
    ) {
        // A tablet is read from further away than a phone: the pages' words go up a size.
        val type = MaterialTheme.typography
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(onBackground = ink, onSurface = ink),
            typography = type.copy(
                titleMedium = type.titleMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
                bodyMedium = type.bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                bodySmall = type.bodySmall.copy(fontSize = 15.sp, lineHeight = 20.sp)
            ),
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

/**
 * One page that asks something, the Windows 8 way: the ring back and the name across the top,
 * what it is for in a column at the left, the choices on the right, and the way on at the
 * bottom right.
 */
@Composable
internal fun TabletOnboardingPage(
    chrome: TourChrome,
    title: String,
    line: String,
    content: @Composable () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TabletHeader(chrome = chrome, title = title)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = TABLET_CONTENT_START, end = TABLET_SIDE, top = 36.dp)
        ) {
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 20.sp,
                        lineHeight = 28.sp
                    ),
                    color = zuneColors.textMuted,
                    modifier = Modifier.width(340.dp)
                )
                Spacer(modifier = Modifier.width(72.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                content()
            }
        }

        TabletBottomBar(chrome)
    }
}

/** The ring back, and the page's name beside it. */
@Composable
internal fun TabletHeader(chrome: TourChrome, title: String) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = TABLET_SIDE, top = 44.dp)
    ) {
        Box(modifier = Modifier.width(TABLET_CONTENT_START - 44.dp)) {
            if (!chrome.isFirst) BackRing(ink, chrome.onBack)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = 60.sp,
                letterSpacing = (-1.5).sp
            ),
            color = ink,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (!chrome.isLast) SkipWord(LocalZuneColors.current.textMuted, chrome.onSkip)
    }
}

/** Where in the tour this is at the bottom left, and the button on at the bottom right. */
@Composable
private fun TabletBottomBar(chrome: TourChrome) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TABLET_CONTENT_START, end = TABLET_SIDE, top = 12.dp, bottom = 36.dp)
    ) {
        TourSquares(
            count = chrome.count,
            current = chrome.index,
            on = MaterialTheme.colorScheme.onBackground,
            off = LocalZuneColors.current.textDim
        )
        Spacer(modifier = Modifier.weight(1f))
        NextButton(chrome, ink = MaterialTheme.colorScheme.onBackground, cutOut = LocalTourGround.current)
    }
}

/** A solid block of the ink with the word cut out of it in the field's colour: Windows 8's button. */
@Composable
private fun NextButton(chrome: TourChrome, ink: Color, cutOut: Color) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .wpTilt(source)
            .widthIn(min = 132.dp)
            .background(ink)
            .clickable(interactionSource = source, indication = null, onClick = chrome.onNext)
            .padding(horizontal = 28.dp, vertical = 11.dp)
    ) {
        Text(
            text = stringResource(if (chrome.isLast) R.string.onboarding_finish else R.string.onboarding_next),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 19.sp),
            color = cutOut
        )
    }
}

/** Windows 8's back: an arrow in a ring. */
@Composable
private fun BackRing(ink: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .wpTilt(source)
            .border(2.dp, ink, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.onboarding_back),
            tint = ink,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SkipWord(colour: Color, onSkip: () -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_skip),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
        color = colour,
        modifier = Modifier
            .clickable(onClick = onSkip)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun TourSquares(count: Int, current: Int, on: Color, off: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 11.dp else 7.dp)
                    .background(if (index == current) on else off)
            )
        }
    }
}

/**
 * The greeting, at the size a tablet allows: the word huge at the left of the field, and the
 * preface under it at a length somebody will actually read.
 */
@Composable
internal fun TabletWelcomePage(chrome: TourChrome) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(durationMillis = 900)) }
    val ink = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TabletHeader(chrome = chrome, title = "")
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = TABLET_CONTENT_START, end = TABLET_SIDE)
                    .widthIn(max = 780.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 120.sp,
                        lineHeight = 120.sp,
                        letterSpacing = (-5).sp
                    ),
                    color = ink,
                    modifier = Modifier.w10mStaggeredAnimation(progress = { entrance.value }, index = 0)
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_line_tablet),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 24.sp,
                        lineHeight = 34.sp
                    ),
                    color = ink,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .w10mStaggeredAnimation(progress = { entrance.value }, index = 1)
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_more),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 19.sp,
                        lineHeight = 27.sp
                    ),
                    color = LocalZuneColors.current.textMuted,
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .w10mStaggeredAnimation(progress = { entrance.value }, index = 2)
                )
            }
        }
        TabletBottomBar(chrome)
    }
}

/**
 * What a lesson asks for, on a band of the accent along the bottom of the tablet's Start: the
 * ring back, the lesson's name and a line, the ticks to earn, and the button on.
 */
@Composable
internal fun TabletLessonBand(
    chrome: TourChrome,
    title: String,
    line: String,
    ticks: List<Pair<String, Boolean>>,
    modifier: Modifier = Modifier
) {
    val accent = LocalZuneColors.current.accentColor
    val ink = inkOn(accent)
    val quiet = ink.copy(alpha = 0.82f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(accent)
            .navigationBarsPadding()
            .padding(start = 44.dp, end = TABLET_SIDE, top = 22.dp, bottom = 24.dp)
    ) {
        Box(modifier = Modifier.width(TABLET_CONTENT_START - 44.dp)) {
            if (!chrome.isFirst) BackRing(ink, chrome.onBack)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 34.sp),
                color = ink
            )
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
                color = quiet,
                modifier = Modifier.padding(top = 4.dp).widthIn(max = 620.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                ticks.forEach { (label, done) -> BandTick(label, done, ink, accent) }
            }
        }
        Spacer(modifier = Modifier.width(32.dp))
        Column(horizontalAlignment = Alignment.End) {
            TourSquares(chrome.count, chrome.index, on = ink, off = ink.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!chrome.isLast) {
                    SkipWord(quiet, chrome.onSkip)
                    Spacer(modifier = Modifier.width(20.dp))
                }
                NextButton(chrome, ink = ink, cutOut = accent)
            }
        }
    }
}

@Composable
private fun BandTick(label: String, done: Boolean, ink: Color, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .then(if (done) Modifier.background(ink) else Modifier.border(2.dp, ink.copy(alpha = 0.7f)))
        ) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (done) FontWeight.Normal else FontWeight.Light,
                fontSize = 17.sp
            ),
            color = if (done) ink else ink.copy(alpha = 0.82f),
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private const val INK_FLIP_LUMINANCE = 0.55f
private const val PAGE_OUT_MS = 120
private const val PAGE_IN_MS = 420
private const val PAGE_TRAVEL_DP = 90f
private const val GROUND_MS = 300
