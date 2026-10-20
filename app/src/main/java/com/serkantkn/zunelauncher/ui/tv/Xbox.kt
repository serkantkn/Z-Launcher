package com.serkantkn.zunelauncher.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * The Xbox 360's dashboard as it was from its Metro update to its last (2011-2013): a ground of
 * greys, dark at the top and light below, the sections as lower-case words along the top, each
 * section a block of green tiles - the one the remote is on lifted a little - and the next and the
 * last sections peeking in faintly at the edges; what a button does written at the bottom left
 * beside the button's coloured disc. Its menus were white lists with the chosen line in green, a
 * grey pane beside them saying what the line does.
 *
 * The X sphere and the "XBOX 360" mark are Microsoft's: the launcher draws neither.
 */
object Xbox {
    /** The tiles' green, and the lighter green of the one the remote is on. */
    val Green = Color(0xFF009A00)
    val GreenLit = Color(0xFF2FB22F)
    /** A menu's chosen line. */
    val GreenLine = Color(0xFF57A056)

    /** Words on the light parts of the ground and on the menus' white. */
    val Ink = Color(0xFF26292A)
    val InkDim = Color(0xFF5C6163)

    /** A menu: its list, and the grey pane beside it. */
    val Panel = Color(0xFFE6EAEA)
    val PanelSide = Color(0xFF9BA2A4)
    /** A question put to the user: dark, as the guide's panels were. */
    val Dialog = Color(0xF2303435)

    /** The pad's buttons, as the legend at the bottom draws them. */
    val ButtonA = Color(0xFF63B82E)
    val ButtonB = Color(0xFFD5263B)
    val ButtonX = Color(0xFF2E6FD8)
    val ButtonY = Color(0xFFF1B21D)

    /** The ground, top to bottom, read off the dashboard itself. */
    internal val groundStops = arrayOf(
        0.00f to Color(0xFF3D4344),
        0.11f to Color(0xFF4F5456),
        0.22f to Color(0xFF616466),
        0.33f to Color(0xFF777A7A),
        0.44f to Color(0xFF979797),
        0.56f to Color(0xFFB4B4B4),
        0.67f to Color(0xFFC5C5C5),
        0.72f to Color(0xFFCACACA),
        0.83f to Color(0xFFB9B9B9),
        1.00f to Color(0xFFA2A2A2)
    )
}

/**
 * The dashboard's themes: its own greys, a few grounds of one colour as the dashboard's colour
 * themes were, and a picture of the user's own under a veil. All but grey are dark, so the
 * launcher's words on them turn white ([dark]).
 */
enum class XboxTheme(val dark: Boolean, internal val stops: Array<Pair<Float, Color>>?) {
    GREY(false, Xbox.groundStops),
    NIGHT(true, arrayOf(0f to Color(0xFF0B1A2E), 0.35f to Color(0xFF173A63), 0.62f to Color(0xFF2A5A8E), 0.75f to Color(0xFF2F6299), 1f to Color(0xFF1C4170))),
    PURPLE(true, arrayOf(0f to Color(0xFF1E0F31), 0.35f to Color(0xFF412468), 0.62f to Color(0xFF5C3990), 0.75f to Color(0xFF633F99), 1f to Color(0xFF452A6C))),
    ORANGE(true, arrayOf(0f to Color(0xFF3A1606), 0.35f to Color(0xFF7A300E), 0.62f to Color(0xFFB04A16), 0.75f to Color(0xFFBC531B), 1f to Color(0xFF853810))),
    BLACK(true, arrayOf(0f to Color(0xFF050505), 0.45f to Color(0xFF1E2021), 0.72f to Color(0xFF26292A), 1f to Color(0xFF141515))),
    PICTURE(true, null);

    companion object {
        fun of(name: String): XboxTheme = entries.firstOrNull { it.name == name } ?: GREY
    }
}

/** A hub's pane: the dashboard's white, or on a dark theme a dark glass, so the words on it read. */
@Composable
internal fun xboxPane(alpha: Float): Color =
    if (com.serkantkn.zunelauncher.ui.theme.LocalZuneColors.current.isDark) Color(0xFF16191A).copy(alpha = alpha * 0.85f)
    else Xbox.Panel.copy(alpha = alpha)

/** Words written on the ground's dark top: white, with the faint shadow the dashboard gave them. */
internal val xboxShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.5f), 3f)

// ── The ground ─────────────────────────────────────────────────────────────────────────────

/**
 * The dashboard's ground in the theme chosen: its greys, or one colour, drawn once into a picture
 * (a slow TV cannot afford them every frame); or the user's own picture under a veil.
 */
@Composable
fun XboxBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = XboxTheme.of(TvDesign.theme.collectAsState().value)
    val photo by TvDesign.themePhoto.collectAsState()
    val ground by produceState(initialValue = XboxGroundCache.cached(theme, photo), theme, photo) {
        value = XboxGroundCache.cached(theme, photo)
            ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { XboxGroundCache.draw(context, theme, photo) }
    }
    Box(modifier = modifier.fillMaxSize().background(if (theme == XboxTheme.GREY) Color(0xFF9A9A9A) else Color(0xFF151617))) {
        val picture = ground
        if (picture != null) {
            Image(
                picture, null,
                contentScale = if (theme == XboxTheme.PICTURE) ContentScale.Crop else ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            // The picture is dimmed most at the top, where the words stand, and at the foot, where the buttons are told.
            if (theme == XboxTheme.PICTURE) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.62f), 0.45f to Color.Black.copy(alpha = 0.32f),
                            0.8f to Color.Black.copy(alpha = 0.38f), 1f to Color.Black.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        } else if (theme != XboxTheme.PICTURE) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawXboxGround(theme) }
        }
        content()
    }
}

private fun DrawScope.drawXboxGround(theme: XboxTheme) {
    drawRect(Brush.verticalGradient(colorStops = theme.stops ?: Xbox.groundStops))
    // A soft light across the middle, brightest a little below the centre.
    drawRect(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = if (theme.dark) 0.07f else 0.10f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.68f),
            radius = size.width * 0.6f
        )
    )
}

private object XboxGroundCache {
    private var key: String? = null
    @Volatile
    private var bitmap: ImageBitmap? = null

    private fun keyOf(theme: XboxTheme, photo: String?) = if (theme == XboxTheme.PICTURE) "picture:$photo" else theme.name

    fun cached(theme: XboxTheme, photo: String?): ImageBitmap? = bitmap?.takeIf { key == keyOf(theme, photo) }

    @Synchronized
    fun draw(context: android.content.Context, theme: XboxTheme, photo: String?): ImageBitmap? {
        cached(theme, photo)?.let { return it }
        val out = if (theme == XboxTheme.PICTURE) {
            photo?.let { tvPicture(context, android.net.Uri.parse(it), PICTURE_SIDE) } ?: return null
        } else {
            ImageBitmap(960, 540).also { picture ->
                androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                    androidx.compose.ui.unit.Density(1f),
                    androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(picture),
                    Size(960f, 540f)
                ) { drawXboxGround(theme) }
            }
        }
        key = keyOf(theme, photo)
        bitmap = out
        return out
    }

    private const val PICTURE_SIDE = 1920
}

// ── The legend ─────────────────────────────────────────────────────────────────────────────

/** One of the pad's buttons as the legend draws it: a disc of its colour with its letter. */
@Composable
fun XboxButtonGlyph(letter: String, color: Color, size: Dp = 22.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(
                Brush.verticalGradient(listOf(lerpWhite(color, 0.25f), color)),
                CircleShape
            )
    ) {
        Text(
            text = letter,
            style = TextStyle(fontFamily = SelawikFamily, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.6f).sp),
            color = Color.White
        )
    }
}

private fun lerpWhite(c: Color, t: Float) = androidx.compose.ui.graphics.lerp(c, Color.White, t)

/** "Ⓐ select  Ⓑ back", along the bottom left; [hold], [pin] and [guide] add what Ⓧ, Ⓨ and ≡ do. */
@Composable
fun XboxLegend(back: Boolean = true, modifier: Modifier = Modifier, hold: Boolean = false, pin: String? = null, guide: Boolean = false) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        XboxLegendItem("A", Xbox.ButtonA, stringResource(R.string.xbox_select))
        if (back) XboxLegendItem("B", Xbox.ButtonB, stringResource(R.string.xbox_back))
        // Ⓧ and Ⓨ: a gamepad's, or the remote's blue and yellow keys; details also come with a held Ⓐ.
        if (hold) XboxLegendItem("X", Xbox.ButtonX, stringResource(R.string.xbox_hold_details))
        if (pin != null) XboxLegendItem("Y", Xbox.ButtonY, pin)
        if (guide) XboxLegendItem("≡", Color(0xFF6E7476), stringResource(R.string.xbox_guide))
    }
}

@Composable
internal fun XboxLegendItem(letter: String, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        XboxButtonGlyph(letter, color)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = xboxLegendStyle(), color = Color.White)
    }
}

@Composable
private fun xboxLegendStyle() = MaterialTheme.typography.bodyLarge.copy(fontFamily = SelawikFamily, fontSize = 17.sp, shadow = xboxShadow)

/**
 * A hub's commands at the bottom, where the dashboard wrote what its buttons did: select and
 * back, then each command as a button of its own - a disc with the command's sign - that the
 * remote can go down to.
 */
@Composable
fun XboxCommandLegend(actions: List<WpBarAction>, menuItems: List<WpBarMenuItem>, height: Dp, modifier: Modifier = Modifier) {
    val colors = listOf(Xbox.ButtonX, Xbox.ButtonY, Color(0xFF6E7476))
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = XBOX_SIDE)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            XboxLegend()
            actions.forEachIndexed { i, action ->
                XboxCommand(action.label, colors[minOf(i, colors.lastIndex)], action.icon, action.onClick)
            }
            menuItems.forEach { item -> XboxCommand(item.text, colors.last(), null, item.onClick) }
        }
    }
}

@Composable
private fun XboxCommand(label: String, color: Color, icon: ImageVector?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp).background(color, CircleShape)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label.lowercase(), style = xboxLegendStyle(), color = Color.White, maxLines = 1)
    }
}

// ── Hubs ───────────────────────────────────────────────────────────────────────────────────

/**
 * A hub as one of the dashboard's apps (Xbox Music, Video, a game's page): its name in the top
 * right, its ways in as a row of lower-case words just under it at the left - the chosen one
 * white, the others faint - and the page below. Moving along the words turns the page.
 */
@Composable
fun XboxHub(
    title: String,
    tabs: List<String>,
    pager: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    hiddenTabs: Set<Int> = emptySet(),
    onSelected: (Int) -> Unit = {},
    navExtra: (@Composable ColumnScope.() -> Unit)? = null,
    sidePaneWidth: Dp = 340.dp,
    sidePane: (@Composable () -> Unit)? = null,
    pane: (@Composable () -> Unit)? = null,
    paneKey: Any? = null,
    bottomRoom: Dp = 0.dp,
    page: @Composable (index: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val shown = tabs.indices.filter { it !in hiddenTabs }
    val focus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val rowState = rememberLazyListState()
    var wordFocused by remember { mutableStateOf(false) }
    LaunchedEffect(tabs.isEmpty()) {
        if (tabs.isEmpty()) return@LaunchedEffect
        val since = android.os.SystemClock.uptimeMillis()
        delay(FIRST_FOCUS_MILLIS)
        repeat(FOCUS_TRIES) {
            if (wordFocused || TvFocus.lastArrowAt > since) return@LaunchedEffect
            runCatching { focus[pager.currentPage.coerceIn(0, tabs.size - 1)].requestFocus() }
            delay(FOCUS_RETRY_MILLIS)
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        XboxHubTitle(title, modifier = Modifier.align(Alignment.TopEnd))
        Column(modifier = Modifier.fillMaxSize().padding(start = maxWidth * XBOX_START, end = XBOX_SIDE, top = XBOX_TOP)) {
            if (shown.isNotEmpty()) {
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    contentPadding = PaddingValues(start = 2.dp, end = 24.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    itemsIndexed(shown) { position, index ->
                        XboxPivotWord(
                            text = tabs[index],
                            chosen = pager.currentPage == index,
                            modifier = Modifier
                                .focusRequester(focus[index])
                                .onFocusChanged { if (it.isFocused) wordFocused = true },
                            onFocused = {
                                val byArrow = android.os.SystemClock.uptimeMillis() - TvFocus.lastArrowAt < ARROW_RECENT_MILLIS
                                if (byArrow && pager.currentPage != index) {
                                    onSelected(index)
                                    scope.launch { pager.scrollToPage(index) }
                                } else if (!byArrow && pager.currentPage != index) {
                                    // The focus fell here on its own - the row it was on went with a
                                    // dialog - and not onto the page showing: it goes to that page's word.
                                    runCatching { focus[pager.currentPage.coerceIn(0, focus.lastIndex)].requestFocus() }
                                    return@XboxPivotWord
                                }
                                scope.launch { rowState.animateScrollToItem((position - 1).coerceAtLeast(0)) }
                            },
                            onClick = {
                                onSelected(index)
                                scope.launch { pager.scrollToPage(index) }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // The page on a light panel, as the dashboard set its lists on white: the ground is
            // darkest just where a page begins, and the launcher's words there are dark.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = bottomRoom)
                    .background(xboxPane(0.62f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (navExtra != null) {
                    Column(
                        modifier = Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 24.dp),
                        content = navExtra
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        userScrollEnabled = false
                    ) { index -> page(index) }
                }
                if (sidePane != null) {
                    Box(modifier = Modifier.width(sidePaneWidth).fillMaxHeight().padding(start = 24.dp)) { sidePane() }
                }
                if (pane != null) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp)) {
                        AnimatedContent(
                            targetState = paneKey,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(90)) },
                            label = "xbox_pane",
                            modifier = Modifier.fillMaxSize()
                        ) { pane() }
                    }
                }
            }
        }
    }
}

/** A hub's name in the top right corner, white and light, as Xbox Music wrote "xbox music". */
@Composable
fun XboxHubTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.lowercase(),
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = 30.sp, shadow = xboxShadow),
        color = Color.White,
        maxLines = 1,
        modifier = modifier.padding(top = 18.dp, end = XBOX_SIDE)
    )
}

/** A word in a row of sections: white when chosen, faint when not, a little larger with the remote on it. */
@Composable
fun XboxPivotWord(
    text: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 24.sp,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "xboxWord")
    Text(
        text = text.lowercase(),
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Normal, fontSize = size, shadow = if (focused || chosen) xboxShadow else null),
        color = if (focused || chosen) Color.White else Color.White.copy(alpha = 0.45f),
        maxLines = 1,
        modifier = modifier
            .tvOwnFocus()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

// ── Pages, menus and questions ─────────────────────────────────────────────────────────────

/**
 * A page inside a hub or settings, as the dashboard's settings were: its name in white at the top
 * left, the page itself on a panel of white, the legend under it.
 */
@Composable
fun XboxPage(
    breadcrumb: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val side = maxWidth * 0.12f
        Column(modifier = Modifier.fillMaxSize().padding(start = side, end = side, top = 26.dp, bottom = 16.dp)) {
            Text(
                breadcrumb.lowercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontSize = 16.sp, shadow = xboxShadow),
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SelawikFamily, fontSize = 26.sp, shadow = xboxShadow),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(xboxPane(0.94f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                content = content
            )
            XboxLegend(modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * A question or a choice, as the dashboard put one up: a panel in the middle of the dimmed
 * screen, white with its name at the top, its answers in a row along its foot. It takes the remote
 * as it comes and keeps it until it goes.
 */
@Composable
fun XboxDialogPanel(
    title: String?,
    progress: Float,
    focus: FocusRequester,
    onDismiss: () -> Unit,
    buttons: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().tvIdleKeys()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f * progress))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            // Dark, as the Xbox's guide and its messages were: the launcher's dialogs write white.
            XboxPanelBody(title, progress, focus, maxWidth, maxHeight, buttons, dark = true, content = content)
            TvFocusFrame(modifier = Modifier.matchParentSize())
        }
    }
}

/** A choice put up over a page without a window of its own (a sheet's list of lines). */
@Composable
fun XboxPanel(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tvTake() }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(PANEL_MILLIS)) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
        // On a dark theme the launcher's words are white: the panel goes dark under them.
        XboxPanelBody(title, shown.value, focus, maxWidth, maxHeight, null, dark = com.serkantkn.zunelauncher.ui.theme.LocalZuneColors.current.isDark) { content() }
    }
}

@Composable
private fun BoxScope.XboxPanelBody(
    title: String?,
    progress: Float,
    focus: FocusRequester,
    maxWidth: Dp,
    maxHeight: Dp,
    buttons: (@Composable RowScope.() -> Unit)?,
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .width(minOf(maxWidth * 0.62f, 640.dp))
            .heightIn(max = maxHeight * 0.8f)
            .focusRequester(focus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .graphicsLayer {
                alpha = progress
                val grow = 0.94f + 0.06f * progress
                scaleX = grow
                scaleY = grow
            }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0f, 8f), size = size)
            }
            .background(if (dark) Xbox.Dialog else Xbox.Panel)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 14.dp)
    ) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
                color = if (dark) Color.White else Xbox.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), content = content)
        if (buttons != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons
            )
        }
    }
}

/** An answer in an Xbox panel: a grey block; the one the remote is on turns green. */
@Composable
fun XboxDialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 140.dp)
            .height(40.dp)
            .tvOwnFocus()
            .background(if (focused) Xbox.Green else Color(0xFF4C5254))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontSize = 17.sp), color = Color.White, maxLines = 1)
    }
}

// ── The logo ───────────────────────────────────────────────────────────────────────────────

/**
 * The console starting, as the dashboard opened: out of a light grey a green ribbon sweeps
 * round a white sphere, and the name stands beside it. The sphere carries no X and the name is
 * the launcher's: the Xbox's own mark is Microsoft's.
 */
@Composable
fun XboxSplash(onDone: () -> Unit) {
    val context = LocalContext.current
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        VistaSounds.prepare(context)
        delay(120)
        VistaSounds.play(context, VistaSounds.Kind.START)
        time.animateTo(1f, tween(SPLASH_MILLIS, easing = LinearEasing))
        onDone()
    }
    val t = time.value
    val out = ((t - 0.85f) / 0.15f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out }
            .background(Brush.radialGradient(listOf(Color(0xFFF7F7F7), Color(0xFFDADADA)), radius = 1600f))
            .focusable(false)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.5f, size.height * 0.42f)
            val r = size.height * 0.085f
            val sweep = (t / 0.35f).coerceIn(0f, 1f)
            val ribbon = 1f - ((t - 0.35f) / 0.2f).coerceIn(0f, 1f)
            // The ribbons, wide and close at first, wrapping round to nothing as the sphere settles.
            for (i in 0 until 3) {
                val radius = r * (3.2f - 2.0f * sweep) + i * r * 0.18f
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF0D8A0D), Color(0xFF7AD82A), Color(0xFFE4F55A), Color(0xFF0D8A0D)), center = c),
                    startAngle = -90f + 400f * sweep + i * 25f,
                    sweepAngle = 140f - i * 30f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = r * (0.55f - i * 0.12f)),
                    alpha = ribbon * (0.95f - i * 0.2f)
                )
            }
            val settle = ((t - 0.2f) / 0.25f).coerceIn(0f, 1f)
            // The sphere, white and lit from the top left, its shadow on the floor under it.
            drawOval(
                Color.Black.copy(alpha = 0.12f * settle),
                topLeft = Offset(c.x - r * 1.1f, c.y + r * 1.9f),
                size = Size(r * 2.2f, r * 0.35f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White, Color(0xFFE9E9E9), Color(0xFFB9B9B9)),
                    center = c + Offset(-r * 0.35f, -r * 0.4f),
                    radius = r * 1.5f
                ),
                radius = r * (0.6f + 0.4f * settle),
                center = c
            )
            drawCircle(Color(0xFF7FCB2E).copy(alpha = 0.55f * settle), radius = r * (0.6f + 0.4f * settle), center = c, style = Stroke(width = r * 0.06f))
        }
        val word = ((t - 0.4f) / 0.2f).coerceIn(0f, 1f)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = 44.sp),
            color = Xbox.Green,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 170.dp)
                .graphicsLayer { alpha = word }
        )
    }
}

/** The time in the top right corner, where the dashboard kept the gamertag. */
@Composable
fun XboxClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time by produceState(initialValue = clockText(context)) {
        while (true) {
            value = clockText(context)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(
        text = time,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontSize = 19.sp, shadow = xboxShadow),
        color = Color.White,
        modifier = modifier
    )
}

private fun clockText(context: android.content.Context) = android.text.format.DateFormat.getTimeFormat(context).format(Date())

/** How a page's content is set across the screen, as the dashboard set it. */
internal const val XBOX_START = 0.12f
internal val XBOX_SIDE = 56.dp
internal val XBOX_TOP = 56.dp
private const val FIRST_FOCUS_MILLIS = 250L
private const val FOCUS_TRIES = 25
private const val FOCUS_RETRY_MILLIS = 100L
private const val ARROW_RECENT_MILLIS = 400L
private const val PANEL_MILLIS = 200
private const val SPLASH_MILLIS = 3_000
