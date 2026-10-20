package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.theme.findActivity
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** One of Vista Start's strips: its name and what it holds. */
internal class VistaCategory(val key: String, val title: String, val items: List<VistaItem>)

/** A thing on a strip: its name, and a sign, a picture or an app's banner. */
internal class VistaItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val image: ImageBitmap? = null,
    val banner: Boolean = false,
    val progress: Float? = null,
    /** The shutdown tile's three small buttons. */
    val shutdown: Boolean = false,
    val open: () -> Unit
)

/** The strips, in Vista's order, round and round. */
@Composable
internal fun vistaCategories(
    places: List<TvPlace>,
    apps: List<TvApp>,
    onOpenHub: (HubType) -> Unit,
    onProgramLibrary: () -> Unit,
    onShutdown: () -> Unit
): List<VistaCategory> {
    val context = LocalContext.current
    val byKey = places.associateBy { it.key }
    fun place(key: String, label: String, icon: ImageVector): VistaItem? = byKey[key]?.let {
        VistaItem(it.key, label, icon, it.image, progress = it.progress, open = it.open)
    }
    val pictures = stringResource(R.string.vista_picture_library)
    val playAll = stringResource(R.string.vista_play_all)
    val videos = stringResource(R.string.vista_video_library)
    val music = stringResource(R.string.vista_music_library)
    val continueLabel = stringResource(R.string.tv_continue)
    return listOf(
        VistaCategory("pictures", stringResource(R.string.vista_cat_pictures), listOfNotNull(
            place("pictures", pictures, Icons.Default.Photo),
            VistaItem("pictures_play", playAll, Icons.Default.PlayCircleOutline) {
                com.serkantkn.zunelauncher.data.repository.PicturesBridge.playAll()
                onOpenHub(HubType.PICTURES)
            },
            place("videos", videos, Icons.Default.Movie)
        )),
        VistaCategory("music", stringResource(R.string.vista_cat_music), listOfNotNull(
            place("music", music, Icons.Default.MusicNote),
            VistaItem("music_play", playAll, Icons.Default.PlayCircleOutline) {
                com.serkantkn.zunelauncher.data.repository.MusicBridge.shuffleAll()
                onOpenHub(HubType.MUSIC)
            }
        )),
        VistaCategory("tv", stringResource(R.string.vista_cat_tv), listOfNotNull(
            byKey["continue"]?.let { VistaItem("continue", continueLabel, Icons.Default.PlayArrow, it.image, progress = it.progress, open = it.open) },
            place("videos", videos, Icons.Default.Movie)?.let { VistaItem("tv_videos", it.label, it.icon, null, open = it.open) }
        )),
        VistaCategory("apps", stringResource(R.string.vista_cat_apps), buildList {
            add(VistaItem("program_library", stringResource(R.string.vista_program_library), Icons.Default.Apps, open = onProgramLibrary))
            apps.forEach { app ->
                add(VistaItem(app.packageName, app.label.lowercase(), null, app.banner ?: app.icon, banner = app.banner != null) {
                    runCatching { context.startActivity(app.launch) }
                        .onFailure { ZuneLog.w("VistaStart", "could not open ${app.packageName}", it) }
                })
            }
        }),
        VistaCategory("extras", stringResource(R.string.vista_cat_extras), listOfNotNull(
            place("weather", stringResource(HubType.WEATHER.titleRes), Icons.Default.WbSunny),
            place("news", stringResource(HubType.NEWS.titleRes), Icons.Default.Article),
            place("clock", stringResource(HubType.CLOCK.titleRes), Icons.Default.Alarm)
        )),
        VistaCategory("tasks", stringResource(R.string.vista_cat_tasks), listOfNotNull(
            place("settings", stringResource(R.string.settings_hub), Icons.Default.CheckBox),
            VistaItem("shutdown", stringResource(R.string.vista_shutdown), Icons.Default.PowerSettingsNew, shutdown = true, open = onShutdown),
            place("files", stringResource(HubType.FILES.titleRes), Icons.Default.Folder),
            place("android_settings", stringResource(R.string.tv_android_settings), Icons.Default.Tv)
        ))
    )
}

/**
 * Vista Media Center's Start.
 *
 * The strips stand one above another round a fixed place: the word "start" faint at the left, the
 * chosen strip's name in white with a pane of glass under it, the strips before and after it paler
 * and paler above and below, round and round. The glass never moves: up and down turn the strips
 * through it, left and right slide the chosen strip's things through it, the one in the glass
 * with its name in its corner. The remote is caught here whole, as Vista's own Start was one
 * thing to its remote, so the glass is drawn by the strip itself and not the TV's focus frame.
 */
@Composable
internal fun VistaStart(
    categories: List<VistaCategory>,
    apps: List<TvApp>,
    hubOpen: Boolean,
    homeResetSignal: Int,
    libraryOpen: Boolean,
    onLibraryOpen: (Boolean) -> Unit,
    shutdownOpen: Boolean,
    onShutdownOpen: (Boolean) -> Unit,
    focusModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val count = categories.size
    // Where the strips have turned to, counted on past the ends so the turn always goes the short
    // way round; the strip is this modulo their number.
    var turn by remember { mutableIntStateOf(START_CATEGORY) }
    val turned = remember { Animatable(START_CATEGORY.toFloat()) }
    var item by remember { mutableIntStateOf(0) }
    val slid = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val intro = remember { Animatable(0f) }
    var popupAt by remember { mutableIntStateOf(1) }
    var libraryAt by remember { mutableIntStateOf(0) }
    var backHeld by remember { mutableStateOf(false) }
    val current = categories[floorMod(turn, count)]
    val itemsNow = current.items

    LaunchedEffect(Unit) {
        // Start builds itself once the logo has gone.
        while (!TvDesign.splashShown) delay(SPLASH_POLL_MILLIS)
        intro.animateTo(1f, tween(INTRO_MILLIS, easing = FastOutSlowInEasing))
        reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
    }

    fun turnTo(next: Int) {
        turn = next
        item = 0
        scope.launch {
            reveal.snapTo(0f)
            slid.snapTo(0f)
            turned.animateTo(next.toFloat(), tween(TURN_MILLIS, easing = FastOutSlowInEasing))
            reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    fun slideTo(next: Int) {
        item = next
        scope.launch { slid.animateTo(next.toFloat(), tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) }
    }
    fun toTop() {
        onShutdownOpen(false)
        onLibraryOpen(false)
        if (floorMod(turn, count) != START_CATEGORY || item != 0) {
            val back = turn - floorMod(turn, count) + START_CATEGORY
            turnTo(back)
        }
    }

    var homeSeen by remember { mutableIntStateOf(homeResetSignal) }
    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal != homeSeen) {
            homeSeen = homeResetSignal
            toTop()
        }
    }

    val keys = Modifier.onPreviewKeyEvent { event ->
        if (hubOpen) return@onPreviewKeyEvent false
        val down = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.Back -> {
                // Both halves are kept once Start acts on the press, so the activity does not act too.
                if (down) {
                    backHeld = when {
                        shutdownOpen -> { onShutdownOpen(false); true }
                        libraryOpen -> { onLibraryOpen(false); true }
                        floorMod(turn, count) != START_CATEGORY || item != 0 -> { toTop(); true }
                        else -> false
                    }
                    backHeld
                } else {
                    val held = backHeld
                    backHeld = false
                    held
                }
            }
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (!down) return@onPreviewKeyEvent true
                when {
                    shutdownOpen -> when (event.key) {
                        Key.DirectionLeft -> popupAt = (popupAt - 1).coerceAtLeast(0)
                        Key.DirectionRight -> popupAt = (popupAt + 1).coerceAtMost(SHUTDOWN_ACTIONS - 1)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onShutdownOpen(false)
                            shutdownAction(context, popupAt)
                        }
                        else -> {}
                    }
                    libraryOpen -> {
                        val total = apps.size
                        if (total > 0) when (event.key) {
                            Key.DirectionUp -> if (libraryAt % LIBRARY_ROWS > 0) libraryAt--
                            Key.DirectionDown -> if (libraryAt % LIBRARY_ROWS < LIBRARY_ROWS - 1 && libraryAt + 1 < total) libraryAt++
                            Key.DirectionLeft -> if (libraryAt - LIBRARY_ROWS >= 0) libraryAt -= LIBRARY_ROWS
                            Key.DirectionRight -> libraryAt = (libraryAt + LIBRARY_ROWS).coerceAtMost(total - 1)
                            else -> apps.getOrNull(libraryAt)?.let { app ->
                                runCatching { context.startActivity(app.launch) }
                            }
                        }
                    }
                    else -> when (event.key) {
                        Key.DirectionUp -> turnTo(turn - 1)
                        Key.DirectionDown -> turnTo(turn + 1)
                        Key.DirectionLeft -> if (item > 0) slideTo(item - 1)
                        Key.DirectionRight -> if (item < itemsNow.lastIndex) slideTo(item + 1)
                        else -> itemsNow.getOrNull(item)?.let { chosen ->
                            if (chosen.shutdown) popupAt = 1
                            chosen.open()
                        }
                    }
                }
                true
            }
            else -> false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(focusModifier)
            .tvOwnFocus()
            .then(keys)
            .focusable()
    ) {
        val u = maxWidth / REFERENCE_WIDTH
        val libraryShown by animateFloatAsState(if (libraryOpen) 1f else 0f, tween(PAGE_MILLIS, easing = FastOutSlowInEasing), label = "vistaLibrary")
        // Start zooms past as the library comes up, as Vista's pages did.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = libraryShown
                    scaleX = 1f + 0.25f * p
                    scaleY = 1f + 0.25f * p
                    alpha = 1f - p
                }
        ) {
            VistaStartWord(u, intro.value)
            VistaStrips(
                categories = categories,
                turned = turned.value,
                u = u,
                intro = intro.value
            )
            VistaRow(
                items = categories[floorMod(turn, count)].items,
                slid = slid.value,
                reveal = reveal.value * (1f - abs(turned.value - turn).coerceAtMost(1f)),
                u = u,
                intro = intro.value
            )
            if (shutdownOpen) VistaShutdownPanel(popupAt, u)
        }
        if (libraryShown > 0f) {
            VistaProgramLibrary(
                apps = apps,
                at = libraryAt,
                modifier = Modifier.graphicsLayer {
                    val p = libraryShown
                    scaleX = 0.7f + 0.3f * p
                    scaleY = 0.7f + 0.3f * p
                    alpha = p
                }
            )
        }
    }
}

/** "start", huge and faint at the left. */
@Composable
private fun VistaStartWord(u: Dp, intro: Float) {
    Text(
        text = stringResource(R.string.vista_start),
        style = MaterialTheme.typography.displayLarge.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = 84.sp),
        color = Vista.StartWord,
        maxLines = 1,
        modifier = Modifier
            .offset(x = u * 34f, y = u * 150f)
            .graphicsLayer { alpha = intro }
    )
}

/** The strips' names, the chosen one white over the glass, the others paler away from it. */
@Composable
private fun VistaStrips(categories: List<VistaCategory>, turned: Float, u: Dp, intro: Float) {
    val count = categories.size
    val base = floor(turned).toInt()
    val density = LocalDensity.current
    for (p in base - 3..base + 3) {
        val rel = p - turned
        if (abs(rel) > 2.6f) continue
        val category = categories[floorMod(p, count)]
        val centerY = stripY(rel)
        val near = (1f - abs(rel)).coerceIn(0f, 1f)
        val fade = when {
            abs(rel) <= 1f -> 1f - 0.15f * abs(rel)
            else -> (0.85f - 0.5f * (abs(rel) - 1f)).coerceAtLeast(0f) * ((2.6f - abs(rel)) / 0.6f).coerceIn(0f, 1f)
        }
        val x = 285f - 20f * near
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = SelawikFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (17f + 1.5f * near).sp
            ),
            color = lerp(Vista.Category, Color.White, near),
            maxLines = 1,
            modifier = Modifier
                .offset { IntOffset((u * x).roundToPx(), with(density) { (u * centerY).roundToPx() - 14.dp.roundToPx() }) }
                .graphicsLayer {
                    alpha = fade * intro
                    val grow = 1f + 0.5f * (1f - intro)
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        )
    }
}

/** Where a strip's name stands, by how far it is from the chosen one. */
private fun stripY(rel: Float): Float {
    val stops = floatArrayOf(-3f, -2f, -1f, 0f, 1f, 2f, 3f)
    val ys = floatArrayOf(110f, 145f, 180f, 217f, 358f, 394f, 430f)
    val r = rel.coerceIn(-3f, 3f)
    for (i in 0 until stops.lastIndex) {
        if (r <= stops[i + 1]) {
            val t = (r - stops[i]) / (stops[i + 1] - stops[i])
            return ys[i] + (ys[i + 1] - ys[i]) * t
        }
    }
    return ys.last()
}

/** The glass, and the chosen strip's things sliding through it. */
@Composable
private fun VistaRow(items: List<VistaItem>, slid: Float, reveal: Float, u: Dp, intro: Float) {
    val boxX = u * BOX_X
    val boxY = u * BOX_Y
    val boxW = u * BOX_W
    val boxH = u * BOX_H
    val centerX = BOX_X + BOX_W / 2f
    // The glass first, as Vista drew it before anything was in it.
    Box(
        modifier = Modifier
            .offset(x = boxX, y = boxY)
            .size(boxW, boxH)
            .graphicsLayer { alpha = (intro * 2f).coerceAtMost(1f) }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(4f, 6f), size = size)
                drawVistaGlass(Offset.Zero, size, corner = 2.dp)
            }
    )
    items.forEachIndexed { index, item ->
        val rel = index - slid
        if (abs(rel) > 5f) return@forEachIndexed
        // The one in the glass, its name in the glass's corner.
        val inGlass = (1f - abs(rel) * 2f).coerceIn(0f, 1f)
        if (inGlass > 0f) {
            Box(
                modifier = Modifier
                    .offset(x = boxX, y = boxY)
                    .size(boxW, boxH)
                    .graphicsLayer { alpha = inGlass * reveal }
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 8f, end = u * 13f, start = u * 10f)
                )
                Box(modifier = Modifier.align(Alignment.Center).padding(top = u * 26f)) {
                    VistaItemSign(item, inGlass = true, u = u)
                }
            }
        }
        // The others, each its name over a faint sign, in a slot of their own.
        val outside = (abs(rel) * 2f).coerceIn(0f, 1f)
        if (outside > 0f) {
            val slotCenter = centerX + rel * SLOT_W * (0.3f + 0.7f * reveal)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = u * (slotCenter - SLOT_W / 2f), y = u * 252f)
                    .width(u * SLOT_W)
                    .graphicsLayer { alpha = outside * reveal }
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(u * 12f))
                VistaItemSign(item, inGlass = false, u = u)
            }
        }
    }
}

/** What stands for a thing: a strip of picture, an app's banner, or a sign. */
@Composable
private fun VistaItemSign(item: VistaItem, inGlass: Boolean, u: Dp) {
    val dim = if (inGlass) 1f else 0.42f
    when {
        item.shutdown -> Row(horizontalArrangement = Arrangement.spacedBy(u * 6f)) {
            ShutdownButton(Icons.Default.Close, red = false, lit = false, u = u * (if (inGlass) 0.72f else 0.6f), dim = dim)
            ShutdownButton(Icons.Default.PowerSettingsNew, red = inGlass, lit = inGlass, u = u * (if (inGlass) 0.72f else 0.6f), dim = dim)
            ShutdownButton(Icons.Outlined.Bedtime, red = false, lit = false, u = u * (if (inGlass) 0.72f else 0.6f), dim = dim)
        }
        item.image != null && item.banner -> Image(
            item.image, null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(u * (if (inGlass) 118f else 76f), u * (if (inGlass) 44f else 30f))
                .clip(RoundedCornerShape(2.dp))
                .graphicsLayer { alpha = if (inGlass) 1f else 0.55f }
        )
        item.image != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                item.image, null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(u * (if (inGlass) 120f else 56f), u * (if (inGlass) 30f else 26f))
                    .graphicsLayer { alpha = if (inGlass) 1f else 0.5f }
            )
            if (item.progress != null && inGlass) {
                Box(modifier = Modifier.width(u * 120f).height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                    Box(modifier = Modifier.fillMaxWidth(item.progress).height(3.dp).background(Vista.Link))
                }
            }
        }
        item.icon != null -> Icon(
            item.icon, null,
            tint = Color.White.copy(alpha = dim),
            modifier = Modifier.size(u * (if (inGlass) 34f else 26f))
        )
    }
}

@Composable
private fun ShutdownButton(icon: ImageVector, red: Boolean, lit: Boolean, u: Dp, dim: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(u * 52f)
            .drawBehind {
                if (red) {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFD23A3A), Color(0xFF8E1414))))
                }
                if (lit) drawVistaGlass(Offset.Zero, size, corner = 2.dp, strength = 0.8f)
                else drawRect(Color.White.copy(alpha = 0.18f * dim), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
            }
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = if (lit) 1f else dim), modifier = Modifier.size(u * 30f))
    }
}

/** The panel that comes up over the shutdown tile: close, turn the TV off, sleep. */
@Composable
private fun VistaShutdownPanel(at: Int, u: Dp) {
    val labels = listOf(
        stringResource(R.string.vista_close),
        stringResource(R.string.vista_tv_off),
        stringResource(R.string.vista_sleep)
    )
    val icons = listOf(Icons.Default.Close, Icons.Default.PowerSettingsNew, Icons.Outlined.Bedtime)
    Box(
        modifier = Modifier
            .offset(x = u * 335f, y = u * 189f)
            .size(u * 262f, u * 130f)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(6f, 8f), size = size)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Vista.PanelTop.copy(alpha = 0.96f), Vista.PanelBottom.copy(alpha = 0.96f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                drawVistaGlass(Offset.Zero, size, corner = 3.dp, strength = 0.6f)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 10f, end = u * 10f)
        ) {
            Text(
                text = stringResource(R.string.vista_media_center),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = Color.White
            )
            Spacer(modifier = Modifier.width(u * 6f))
            VistaOrb(size = u * 22f)
        }
        Column(modifier = Modifier.padding(start = u * 14f, top = u * 54f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(u * 10f)) {
                icons.forEachIndexed { index, icon ->
                    ShutdownButton(icon, red = index == 1, lit = index == at, u = u * 0.75f, dim = 0.9f)
                }
            }
            Text(
                text = labels[at],
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontSize = 16.sp),
                color = Color.White,
                modifier = Modifier.padding(top = u * 6f)
            )
        }
    }
}

/** Close, turn off, sleep: what each of the panel's buttons does on a TV. */
internal fun shutdownAction(context: Context, at: Int) {
    val service = com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService.current
    when (at) {
        // Close: Media Center goes, and what was under it - Google TV's own home - comes back.
        0 -> context.findActivity()?.moveTaskToBack(true)
        1 -> if (service == null || !service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) {
            needService(context)
        }
        else -> if (service == null || !service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
            needService(context)
        }
    }
}

private fun needService(context: Context) {
    android.widget.Toast.makeText(context, R.string.tv_home_key_off, android.widget.Toast.LENGTH_LONG).show()
}

/**
 * The program library: every app on the TV as Vista showed its programs, in rows of three that run
 * off to the right, the one the remote is on lifted out of its row with its name under the lot.
 */
@Composable
private fun VistaProgramLibrary(apps: List<TvApp>, at: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val u = maxWidth / REFERENCE_WIDTH
        VistaLibraryTitle(stringResource(R.string.vista_program_library), modifier = Modifier.align(Alignment.TopEnd))
        Column(modifier = Modifier.padding(start = u * 110f, top = u * 82f)) {
            VistaPivotWord(text = stringResource(R.string.vista_by_name), chosen = true, onClick = {})
            Spacer(modifier = Modifier.height(u * 28f))
            if (apps.isEmpty()) {
                Text(
                    text = stringResource(R.string.vista_no_apps),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily),
                    color = Color.White
                )
                return@Column
            }
            val column = at / LIBRARY_ROWS
            val firstColumn = (column - LIBRARY_VISIBLE_COLUMNS + 1).coerceAtLeast(0)
            val shift by animateFloatAsState(firstColumn.toFloat(), tween(SLIDE_MILLIS, easing = FastOutSlowInEasing), label = "libraryShift")
            Box(modifier = Modifier.fillMaxWidth().height(u * (LIBRARY_ROWS * (LIBRARY_TILE_H + LIBRARY_GAP)))) {
                apps.forEachIndexed { index, app ->
                    val col = index / LIBRARY_ROWS
                    val row = index % LIBRARY_ROWS
                    val x = (col - shift) * (LIBRARY_TILE_W + LIBRARY_GAP)
                    if (x < -LIBRARY_TILE_W || x > 820f) return@forEachIndexed
                    val chosen = index == at
                    val lift by animateFloatAsState(if (chosen) 1.12f else 1f, tween(160), label = "libraryLift")
                    Box(
                        modifier = Modifier
                            .offset(x = u * x, y = u * (row * (LIBRARY_TILE_H + LIBRARY_GAP)))
                            .size(u * LIBRARY_TILE_W, u * LIBRARY_TILE_H)
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                                alpha = if (chosen) 1f else 0.82f
                            }
                            .background(Color(0xFF16406F))
                            .then(if (chosen) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f)) else Modifier)
                    ) {
                        val picture = app.banner ?: app.icon
                        if (picture != null) {
                            Image(
                                picture, null,
                                contentScale = if (app.banner != null) ContentScale.Crop else ContentScale.Fit,
                                modifier = if (app.banner != null) Modifier.fillMaxSize() else Modifier.align(Alignment.Center).size(u * 48f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(u * 18f))
            Text(
                text = apps.getOrNull(at)?.label.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
                color = Color.White
            )
        }
        VistaCounter(at + 1, apps.size, modifier = Modifier.align(Alignment.BottomEnd).padding(end = u * 72f, bottom = u * 52f))
    }
}

/** Vista's "2 | 3": which of how many the remote is on. */
@Composable
internal fun VistaCounter(at: Int, of: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val style = MaterialTheme.typography.titleLarge.copy(fontFamily = SelawikFamily, fontSize = 18.sp)
        Text("$at", style = style.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        Box(modifier = Modifier.padding(horizontal = 6.dp).width(1.dp).height(18.dp).background(Vista.Category))
        Text("$of", style = style, color = Vista.Category)
    }
}

/**
 * Media Center's logo, as Vista opened it: out of the dark a star of light, a faint ring sweeping
 * round it, the name beside the orb with its reflection under it on a dark floor; then the dark
 * gives way to the blue and Start builds itself.
 */
@Composable
fun VistaSplash(onDone: () -> Unit) {
    val context = LocalContext.current
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        VistaSounds.prepare(context)
        delay(SPLASH_SOUND_DELAY_MILLIS)
        VistaSounds.play(context, VistaSounds.Kind.START)
        time.animateTo(1f, tween(SPLASH_MILLIS, easing = LinearEasing))
        onDone()
    }
    val t = time.value
    // Black throughout; it fades out at the very end over Start's blue.
    val out = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out }
            .background(Color.Black)
            .focusable(false)
    ) {
        val logo = ((t - 0.12f) / 0.18f).coerceIn(0f, 1f) * (1f - ((t - 0.62f) / 0.2f).coerceIn(0f, 1f))
        val flare = ((t - 0.08f) / 0.12f).coerceIn(0f, 1f) * (1f - ((t - 0.3f) / 0.2f).coerceIn(0f, 1f))
        val ring = ((t - 0.05f) / 0.2f).coerceIn(0f, 1f) * (1f - ((t - 0.45f) / 0.2f).coerceIn(0f, 1f))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.5f, size.height * 0.5f)
            // The ring, faintly coloured at its edge, sweeping round as it fades.
            rotate(degrees = 40f + 120f * t, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, Color(0xFF6A4CC8).copy(alpha = 0.35f * ring), Color(0xFFD8662A).copy(alpha = 0.3f * ring), Color.Transparent),
                        center = c
                    ),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(c.x - size.height * 0.36f, c.y - size.height * 0.36f),
                    size = Size(size.height * 0.72f, size.height * 0.72f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.height * 0.03f)
                )
            }
            // The star of light where the orb will be.
            val star = Offset(c.x - size.width * 0.115f, c.y)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = flare), Color(0xFF8FE0FF).copy(alpha = 0.4f * flare), Color.Transparent), center = star, radius = size.height * 0.08f),
                radius = size.height * 0.08f,
                center = star
            )
            drawLine(Color.White.copy(alpha = 0.6f * flare), star - Offset(size.height * 0.06f, 0f), star + Offset(size.height * 0.06f, 0f), strokeWidth = 2f)
            drawLine(Color.White.copy(alpha = 0.6f * flare), star - Offset(0f, size.height * 0.05f), star + Offset(0f, size.height * 0.05f), strokeWidth = 2f)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = logo }
        ) {
            VistaLogoLine()
            // Its reflection on the floor.
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        scaleY = -1f
                        alpha = 0.28f
                    }
            ) { VistaLogoLine() }
        }
    }
}

@Composable
private fun VistaLogoLine() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VistaOrb(size = 30.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.vista_media_center),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Normal, fontSize = 26.sp),
            color = Color.White
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotate(
    degrees: Float,
    pivot: Offset,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) = withTransform({ rotate(degrees, pivot) }, block)

private fun floorMod(a: Int, n: Int): Int = ((a % n) + n) % n

/** The design is laid out for a 960dp-wide screen, the size of a 1080p television's, and scaled. */
private const val REFERENCE_WIDTH = 960f
/** Vista opened on "TV + Movies"; this opens on the same strip. */
private const val START_CATEGORY = 2
private const val BOX_X = 271f
private const val BOX_Y = 240f
private const val BOX_W = 180f
private const val BOX_H = 99f
private const val SLOT_W = 180f
private const val SHUTDOWN_ACTIONS = 3
private const val LIBRARY_ROWS = 3
private const val LIBRARY_VISIBLE_COLUMNS = 4
private const val LIBRARY_TILE_W = 168f
private const val LIBRARY_TILE_H = 94f
private const val LIBRARY_GAP = 10f
private const val TURN_MILLIS = 300
private const val SLIDE_MILLIS = 220
private const val REVEAL_MILLIS = 260
private const val INTRO_MILLIS = 650
private const val PAGE_MILLIS = 380
private const val SPLASH_MILLIS = 3_200
private const val SPLASH_SOUND_DELAY_MILLIS = 150L
private const val SPLASH_POLL_MILLIS = 50L
