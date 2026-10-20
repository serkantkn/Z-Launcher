package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.theme.findActivity
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One of the dashboard's tiles: where it stands in its section, in 1280 × 720 pixels, and what it is. */
internal class XboxTile(
    val key: String,
    val label: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val icon: ImageVector? = null,
    val image: ImageBitmap? = null,
    /** Shown large in place of a sign: the temperature, say. */
    val big: String? = null,
    val open: () -> Unit
) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2
}

/** A section of the dashboard: its word along the top and its block of tiles. */
internal class XboxSection(val key: String, val title: String, val tiles: List<XboxTile>) {
    val width: Float get() = tiles.maxOf { it.x + it.w }
}

/** The things on the dashboard a section's tiles open, beyond the hubs. */
internal class XboxActions(
    val openApps: () -> Unit,
    val openShutdown: () -> Unit
)

/**
 * The sections, laid out as the dashboard laid its own: home with three tiles down its left, a
 * large one in the middle, two at its right and three along its foot; the others two-by-two
 * beside a large one; settings in two rows of four.
 */
@Composable
internal fun xboxSections(
    places: List<TvPlace>,
    apps: List<TvApp>,
    onOpenHub: (HubType) -> Unit,
    actions: XboxActions
): List<XboxSection> {
    val context = LocalContext.current
    val byKey = places.associateBy { it.key }
    fun tile(key: String, label: String, x: Float, y: Float, w: Float, h: Float, icon: ImageVector?, image: ImageBitmap? = null, big: String? = null, open: () -> Unit) =
        XboxTile(key, label, x, y, w, h, icon, image, big, open)
    fun place(key: String, label: String, x: Float, y: Float, w: Float, h: Float, icon: ImageVector, image: Boolean = false, big: String? = null): XboxTile? =
        byKey[key]?.let { tile(key, label, x, y, w, h, icon, if (image) it.image else null, big, it.open) }
    fun app(app: TvApp, key: String, x: Float, y: Float, w: Float, h: Float) =
        tile(key, app.label, x, y, w, h, Icons.Default.Apps, app.banner ?: app.icon) {
            runCatching { context.startActivity(app.launch) }
        }

    val pictures = stringResource(HubType.PICTURES.titleRes)
    val music = stringResource(R.string.zune_row_music)
    val videos = stringResource(R.string.music_tab_videos)
    val weather = stringResource(HubType.WEATHER.titleRes)
    val news = stringResource(HubType.NEWS.titleRes)
    val clock = stringResource(HubType.CLOCK.titleRes)
    val files = stringResource(HubType.FILES.titleRes)
    val myApps = stringResource(R.string.xbox_my_apps)
    val continueLabel = stringResource(R.string.tv_continue)
    val shuffle = stringResource(R.string.xbox_shuffle)
    val slideShow = stringResource(R.string.xbox_slide_show)
    val androidSettings = stringResource(R.string.tv_android_settings)
    val launcherSettings = stringResource(R.string.xbox_launcher_settings)
    val look = stringResource(R.string.xbox_look)
    val sleep = stringResource(R.string.vista_sleep)
    val turnOff = stringResource(R.string.xbox_turn_off)
    val weatherBig = byKey["weather"]?.line?.substringBefore(' ')

    val left = byKey["continue"]
    val playing = byKey["music"]?.let { it.image }

    // ── home ──
    val home = buildList {
        add(place("pictures", pictures, 0f, 0f, S, H, Icons.Default.Photo))
        add(place("music", music, 0f, R2, S, H, Icons.Default.MusicNote))
        add(place("videos", videos, 0f, R3, S, H, Icons.Default.Movie))
        add(
            when {
                left != null -> tile("home_big", "$continueLabel: ${left.line.orEmpty()}", C2, 0f, BIG_W, BIG_H, Icons.Default.PlayArrow, left.image, open = left.open)
                byKey["pictures"]?.image != null -> place("pictures", pictures, C2, 0f, BIG_W, BIG_H, Icons.Default.Photo, image = true)?.let {
                    tile("home_big", it.label, it.x, it.y, it.w, it.h, it.icon, it.image, open = it.open)
                }
                else -> tile("home_big", shuffle, C2, 0f, BIG_W, BIG_H, Icons.Default.Shuffle) {
                    com.serkantkn.zunelauncher.data.repository.MusicBridge.shuffleAll()
                    onOpenHub(HubType.MUSIC)
                }
            }
        )
        add(place("weather", weather, C4, 0f, S, H, Icons.Default.WbSunny, big = weatherBig))
        add(place("news", news, C4, R2, S, H, Icons.Default.Article))
        add(tile("home_apps", myApps, C2, R3, M, H, Icons.Default.Apps, open = actions.openApps))
        add(
            apps.firstOrNull()?.let { app(it, "home_app", C3, R3, M, H) }
                ?: place("android_settings", androidSettings, C3, R3, M, H, Icons.Default.Tv)
        )
        add(place("clock", clock, C4, R3, S, H, Icons.Default.Alarm))
    }.filterNotNull()

    // ── tv + movies ──
    val movies = buildList {
        add(place("videos", videos, 0f, 0f, S, H, Icons.Default.Movie)?.renamed("tv_videos"))
        add(place("pictures", pictures, D2, 0f, S, H, Icons.Default.Photo)?.renamed("tv_pictures"))
        add(tile("tv_slides", slideShow, 0f, R2, S, H, Icons.Default.Slideshow) {
            com.serkantkn.zunelauncher.data.repository.PicturesBridge.playAll()
            onOpenHub(HubType.PICTURES)
        })
        add(place("files", files, D2, R2, S, H, Icons.Default.Folder)?.renamed("tv_files"))
        add(
            if (left != null) tile("tv_big", "$continueLabel: ${left.line.orEmpty()}", D3, 0f, WIDE_W, BIG_H, Icons.Default.PlayArrow, left.image, open = left.open)
            else place("videos", videos, D3, 0f, WIDE_W, BIG_H, Icons.Default.Movie)?.renamed("tv_big")
        )
    }.filterNotNull()

    // ── music ──
    val musicSection = buildList {
        add(place("music", music, 0f, 0f, S, H, Icons.Default.MusicNote)?.renamed("m_music"))
        add(tile("m_shuffle", shuffle, D2, 0f, S, H, Icons.Default.Shuffle) {
            com.serkantkn.zunelauncher.data.repository.MusicBridge.shuffleAll()
            onOpenHub(HubType.MUSIC)
        })
        add(place("videos", videos, 0f, R2, S, H, Icons.Default.Movie)?.renamed("m_videos"))
        add(place("files", files, D2, R2, S, H, Icons.Default.Folder)?.renamed("m_files"))
        add(
            byKey["music"]?.let { m ->
                tile("m_big", m.line ?: music, D3, 0f, WIDE_W, BIG_H, Icons.Default.MusicNote, playing, open = m.open)
            }
        )
    }.filterNotNull()

    // ── apps ──
    val appsSection = buildList {
        add(tile("a_mine", myApps, 0f, 0f, S, H, Icons.Default.Apps, open = actions.openApps))
        apps.getOrNull(1)?.let { add(app(it, "a_1", D2, 0f, S, H)) }
        apps.getOrNull(2)?.let { add(app(it, "a_2", 0f, R2, S, H)) }
        apps.getOrNull(3)?.let { add(app(it, "a_3", D2, R2, S, H)) }
        apps.getOrNull(0)?.let { add(app(it, "a_0", D3, 0f, WIDE_W, BIG_H)) }
    }

    // ── settings ──
    val settingsSection = buildList {
        add(place("android_settings", androidSettings, 0f, 0f, Q, Q, Icons.Default.Tv)?.renamed("s_system"))
        add(place("settings", launcherSettings, Q1, 0f, Q, Q, Icons.Default.Settings)?.renamed("s_launcher"))
        add(tile("s_look", look, Q2, 0f, Q, Q, Icons.Default.Palette) {
            com.serkantkn.zunelauncher.data.repository.SettingsBridge.open("LOOK")
            onOpenHub(HubType.SETTINGS)
        })
        add(place("files", files, Q3, 0f, Q, Q, Icons.Default.Folder)?.renamed("s_files"))
        add(place("clock", clock, 0f, QR, Q, Q, Icons.Default.Alarm)?.renamed("s_clock"))
        add(tile("s_exit", stringResource(R.string.xbox_exit), Q1, QR, Q, Q, Icons.AutoMirrored.Filled.ExitToApp) {
            context.findActivity()?.moveTaskToBack(true)
        })
        add(tile("s_sleep", sleep, Q2, QR, Q, Q, Icons.Outlined.Bedtime) { shutdownAction(context, 2) })
        add(tile("s_off", turnOff, Q3, QR, Q, Q, Icons.Default.PowerSettingsNew, open = actions.openShutdown))
    }.filterNotNull()

    return listOf(
        XboxSection("home", stringResource(R.string.xbox_home), home),
        XboxSection("tv", stringResource(R.string.xbox_tv_movies), movies),
        XboxSection("music", stringResource(R.string.xbox_music), musicSection),
        XboxSection("apps", stringResource(R.string.xbox_apps), appsSection),
        XboxSection("settings", stringResource(R.string.xbox_settings), settingsSection)
    ).filter { it.tiles.isNotEmpty() }
}

private fun XboxTile.renamed(key: String) = XboxTile(key, label, x, y, w, h, icon, image, big, open)

/**
 * The Xbox 360's dashboard as Start: the section words along the top, the chosen section's tiles
 * in the middle and its neighbours peeking in at the edges. Left and right walk the tiles and on
 * past a section's edge into the next, the words following; up from the top tiles goes onto the
 * words. Like Vista's, it takes the remote whole and draws its own focus: a tile lifted and lit.
 */
@Composable
internal fun XboxStart(
    sections: List<XboxSection>,
    apps: List<TvApp>,
    hubOpen: Boolean,
    homeResetSignal: Int,
    appsOpen: Boolean,
    onAppsOpen: (Boolean) -> Unit,
    shutdownOpen: Boolean,
    onShutdownOpen: (Boolean) -> Unit,
    focusModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableIntStateOf(0) }
    var tile by remember { mutableIntStateOf(0) }
    var onWords by remember { mutableStateOf(false) }
    var appAt by remember { mutableIntStateOf(0) }
    var offAt by remember { mutableIntStateOf(0) }
    var backHeld by remember { mutableStateOf(false) }
    val slide = remember { Animatable(0f) }
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (!TvDesign.splashShown) kotlinx.coroutines.delay(50)
        intro.animateTo(1f, tween(INTRO_MILLIS, easing = FastOutSlowInEasing))
    }
    // Where each section starts along the strip of sections.
    val starts = remember(sections.map { it.width }) {
        var x = 0f
        sections.map { s -> x.also { x += s.width + SECTION_GAP } }
    }
    val sectionNow = section.coerceIn(0, sections.lastIndex)
    LaunchedEffect(sectionNow, starts) {
        slide.animateTo(starts.getOrElse(sectionNow) { 0f }, tween(SLIDE_MILLIS, easing = FastOutSlowInEasing))
    }

    fun goTo(newSection: Int, newTile: Int) {
        section = newSection
        tile = newTile
    }
    fun toTop() {
        onShutdownOpen(false)
        onAppsOpen(false)
        onWords = false
        goTo(0, 0)
    }
    var homeSeen by remember { mutableIntStateOf(homeResetSignal) }
    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal != homeSeen) {
            homeSeen = homeResetSignal
            toTop()
        }
    }

    /** The tile in [dir] from the one the remote is on: the nearest whose middle lies that way. */
    fun neighbour(tiles: List<XboxTile>, from: XboxTile, dx: Int, dy: Int): Int? {
        var best: Int? = null
        var bestScore = Float.MAX_VALUE
        tiles.forEachIndexed { i, t ->
            if (t === from) return@forEachIndexed
            val ddx = t.cx - from.cx
            val ddy = t.cy - from.cy
            val along = if (dx != 0) ddx * dx else ddy * dy
            if (along <= 4f) return@forEachIndexed
            // Only a tile in line with this one, as on the dashboard: up from the large tile goes
            // onto the section words, not off to a small tile beside it.
            val overlap = if (dx != 0) !(t.y + t.h <= from.y + 1 || t.y >= from.y + from.h - 1) else !(t.x + t.w <= from.x + 1 || t.x >= from.x + from.w - 1)
            if (!overlap) return@forEachIndexed
            val across = if (dx != 0) abs(ddy) else abs(ddx)
            val score = along + across * 2f
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }
    /** Over a section's edge: the tile at that edge of [next] nearest in height to [from]. */
    fun enter(next: XboxSection, fromY: Float, fromLeft: Boolean): Int {
        val edge = if (fromLeft) next.tiles.minOf { it.x } else next.tiles.maxOf { it.x + it.w }
        val candidates = next.tiles.withIndex().filter { (_, t) -> if (fromLeft) t.x <= edge + 1 else t.x + t.w >= edge - 1 }
        return candidates.minByOrNull { (_, t) -> abs(t.cy - fromY) }?.index ?: 0
    }

    val keys = Modifier.onPreviewKeyEvent { event ->
        if (hubOpen) return@onPreviewKeyEvent false
        val down = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.Back -> {
                if (down) {
                    backHeld = when {
                        shutdownOpen -> { onShutdownOpen(false); true }
                        appsOpen -> { onAppsOpen(false); true }
                        onWords || sectionNow != 0 || tile != 0 -> { toTop(); true }
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
                val choose = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                when {
                    shutdownOpen -> when {
                        event.key == Key.DirectionUp -> offAt = (offAt - 1).coerceAtLeast(0)
                        event.key == Key.DirectionDown -> offAt = (offAt + 1).coerceAtMost(SHUTDOWN_LINES - 1)
                        choose -> {
                            onShutdownOpen(false)
                            when (offAt) {
                                0 -> shutdownAction(context, 1)
                                1 -> shutdownAction(context, 2)
                                2 -> shutdownAction(context, 0)
                            }
                        }
                    }
                    appsOpen -> if (apps.isNotEmpty()) when {
                        event.key == Key.DirectionLeft -> appAt = (appAt - 1).coerceAtLeast(0)
                        event.key == Key.DirectionRight -> appAt = (appAt + 1).coerceAtMost(apps.lastIndex)
                        choose -> apps.getOrNull(appAt)?.let { runCatching { context.startActivity(it.launch) } }
                    }
                    onWords -> when {
                        event.key == Key.DirectionLeft -> if (sectionNow > 0) goTo(sectionNow - 1, 0)
                        event.key == Key.DirectionRight -> if (sectionNow < sections.lastIndex) goTo(sectionNow + 1, 0)
                        event.key == Key.DirectionDown || choose -> {
                            onWords = false
                            val tiles = sections[sectionNow].tiles
                            tile = tiles.indices.minByOrNull { tiles[it].y * 4 + tiles[it].x } ?: 0
                        }
                    }
                    else -> {
                        val current = sections[sectionNow]
                        val from = current.tiles.getOrNull(tile) ?: current.tiles.first()
                        when (event.key) {
                            Key.DirectionLeft -> neighbour(current.tiles, from, -1, 0)?.let { tile = it }
                                ?: if (sectionNow > 0) goTo(sectionNow - 1, enter(sections[sectionNow - 1], from.cy, fromLeft = false)) else Unit
                            Key.DirectionRight -> neighbour(current.tiles, from, 1, 0)?.let { tile = it }
                                ?: if (sectionNow < sections.lastIndex) goTo(sectionNow + 1, enter(sections[sectionNow + 1], from.cy, fromLeft = true)) else Unit
                            Key.DirectionUp -> neighbour(current.tiles, from, 0, -1)?.let { tile = it } ?: run { onWords = true }
                            Key.DirectionDown -> neighbour(current.tiles, from, 0, 1)?.let { tile = it }
                            else -> {
                                if (from.key == "s_off") offAt = 0
                                from.open()
                            }
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
        val density = LocalDensity.current
        val pageShown by animateFloatAsState(if (appsOpen) 1f else 0f, tween(PAGE_MILLIS, easing = FastOutSlowInEasing), label = "xboxApps")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1f + 0.2f * pageShown
                    scaleY = 1f + 0.2f * pageShown
                    alpha = (1f - pageShown) * intro.value
                }
        ) {
            // The section words, along the top; the chosen one white.
            Row(
                modifier = Modifier.offset(x = u * WORDS_X, y = u * WORDS_Y),
                horizontalArrangement = Arrangement.spacedBy(u * 40f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sections.forEachIndexed { i, s ->
                    val chosen = i == sectionNow
                    val lit = chosen && onWords
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = SelawikFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = with(density) { (u * 33f).toSp() },
                            shadow = if (chosen) xboxShadow else null
                        ),
                        color = if (chosen) Color.White else Color.White.copy(alpha = 0.42f),
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            val grow = if (lit) 1.08f else 1f
                            scaleX = grow
                            scaleY = grow
                        }
                    )
                }
            }
            // The sections, side by side, slid so the chosen one stands in the middle.
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                sections.forEachIndexed { i, s ->
                    val sx = (starts[i] - slide.value) + GROUP_X
                    if (sx > 1300f || sx + s.width < -300f) return@forEachIndexed
                    val faint = if (i == sectionNow) 1f else 0.45f
                    s.tiles.forEachIndexed { ti, t ->
                        val focused = i == sectionNow && ti == tile && !onWords && !appsOpen && !shutdownOpen
                        XboxTileView(
                            tile = t,
                            focused = focused,
                            u = u,
                            modifier = Modifier
                                .offset(x = u * (sx + t.x), y = u * (GROUP_Y + t.y))
                                .graphicsLayer { alpha = faint }
                        )
                    }
                }
            }
            XboxLegend(back = false, modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f))
            XboxClock(modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 42f, end = u * 90f))
        }
        if (pageShown > 0f) {
            XboxAppsPage(
                apps = apps,
                at = appAt,
                u = u,
                modifier = Modifier.graphicsLayer {
                    val grow = 0.8f + 0.2f * pageShown
                    scaleX = grow
                    scaleY = grow
                    alpha = pageShown
                }
            )
        }
        if (shutdownOpen) XboxShutdownPanel(offAt, u)
    }
}

/** A tile: Xbox green with its sign and its name, or a picture with its name on a dark band. */
@Composable
private fun XboxTileView(tile: XboxTile, focused: Boolean, u: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val lift by animateFloatAsState(if (focused) 1.07f else 1f, tween(140), label = "xboxLift")
    Box(
        modifier = modifier
            .size(u * tile.w, u * tile.h)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                shadowElevation = if (focused) 18f else 0f
            }
            .background(if (focused && tile.image == null) Xbox.GreenLit else Xbox.Green)
    ) {
        val labelStyle = MaterialTheme.typography.titleMedium.copy(
            fontFamily = SelawikFamily,
            fontWeight = FontWeight.Normal,
            fontSize = with(density) { (u * 19f).toSp() }
        )
        if (tile.image != null) {
            Image(tile.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = u * 10f, vertical = u * 4f)
            ) {
                Text(tile.label, style = labelStyle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            when {
                tile.big != null -> Text(
                    tile.big,
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = with(density) { (u * 46f).toSp() }),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = u * 16f)
                )
                tile.icon != null -> Icon(
                    tile.icon, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = u * 20f).size(u * minOf(52f, tile.h * 0.36f))
                )
            }
            Text(
                tile.label,
                style = labelStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 10f, end = u * 8f, bottom = u * 8f)
            )
        }
    }
}

/**
 * "my apps", as the dashboard showed "my pins": a row of tall white cards running off to the
 * right, each a picture with its name under it and a small sign of what it is; the one the remote
 * is on lifted out of the row. Which of how many in the top right.
 */
@Composable
private fun XboxAppsPage(apps: List<TvApp>, at: Int, u: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shift by animateFloatAsState((at - 1).coerceAtLeast(0).toFloat(), tween(SLIDE_MILLIS, easing = FastOutSlowInEasing), label = "xboxAppsShift")
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.offset(x = u * 104f, y = u * 80f)) {
            Text("⌄ " + stringResource(R.string.xbox_show_me), style = small(density, u, 18f), color = Color.White)
            Text(stringResource(R.string.xbox_all_apps), style = small(density, u, 24f), color = Color.White.copy(alpha = 0.8f))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 78f, end = u * 95f)) {
            Text(stringResource(R.string.xbox_my_apps), style = small(density, u, 32f), color = Color.White)
            if (apps.isNotEmpty()) {
                Text(stringResource(R.string.xbox_n_of_m, at + 1, apps.size), style = small(density, u, 19f), color = Color.White)
            }
        }
        if (apps.isEmpty()) {
            Text(stringResource(R.string.vista_no_apps), style = small(density, u, 22f), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            apps.forEachIndexed { i, app ->
                val x = 64f + (i - shift) * CARD_STEP
                if (x < -CARD_W || x > 1300f) return@forEachIndexed
                val chosen = i == at
                val lift by animateFloatAsState(if (chosen) 1.07f else 1f, tween(140), label = "xboxCard")
                Column(
                    modifier = Modifier
                        .offset(x = u * x, y = u * 178f)
                        .size(u * CARD_W, u * 398f)
                        .graphicsLayer {
                            scaleX = lift
                            scaleY = lift
                            shadowElevation = if (chosen) 22f else 4f
                        }
                        .background(Color(0xFFEDEDED))
                        .padding(u * 9f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(u * 280f).background(Color(0xFFA9AEB0))
                    ) {
                        val picture = app.banner ?: app.icon
                        if (picture != null) {
                            Image(
                                picture, null,
                                contentScale = if (app.banner != null) ContentScale.Fit else ContentScale.Inside,
                                modifier = if (app.banner != null) Modifier.fillMaxWidth() else Modifier.size(u * 96f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(u * 10f))
                    Text(app.label, style = small(density, u, 19f), color = Xbox.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Apps, null, tint = Xbox.InkDim, modifier = Modifier.align(Alignment.End).size(u * 18f))
                }
            }
        }
        XboxLegend(modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f))
    }
}

@Composable
private fun small(density: androidx.compose.ui.unit.Density, u: Dp, px: Float) =
    MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Normal, fontSize = with(density) { (u * px).toSp() }, shadow = xboxShadow)

/**
 * Turning off, as the dashboard asked it: a menu in the middle, its lines on white with the
 * chosen one green, a grey pane beside saying what it does.
 */
@Composable
private fun XboxShutdownPanel(at: Int, u: Dp) {
    val density = LocalDensity.current
    val lines = listOf(
        stringResource(R.string.vista_tv_off),
        stringResource(R.string.vista_sleep),
        stringResource(R.string.xbox_exit)
    )
    val explain = listOf(
        stringResource(R.string.xbox_tv_off_sub),
        stringResource(R.string.xbox_sleep_sub),
        stringResource(R.string.xbox_exit_sub)
    )
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.align(Alignment.Center).width(u * 880f)) {
            Text(
                stringResource(R.string.xbox_turn_off),
                style = small(density, u, 26f),
                color = Color.White,
                modifier = Modifier.padding(bottom = u * 10f)
            )
            Row(modifier = Modifier.fillMaxWidth().height(u * 300f).drawBehind {
                drawRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0f, 8f), size = size)
            }) {
                Column(modifier = Modifier.weight(1f).fillMaxSize().background(Xbox.Panel)) {
                    lines.forEachIndexed { i, line ->
                        Text(
                            line,
                            style = small(density, u, 22f).copy(shadow = null),
                            color = if (i == at) Color.White else Xbox.Ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (i == at) Xbox.GreenLine else Color.Transparent)
                                .padding(horizontal = u * 30f, vertical = u * 10f)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxSize().background(Xbox.PanelSide).padding(u * 30f)) {
                    Text(lines[at], style = small(density, u, 26f), color = Color.White)
                    Spacer(modifier = Modifier.height(u * 14f))
                    Text(explain[at], style = small(density, u, 19f), color = Color.White.copy(alpha = 0.9f))
                }
            }
            XboxLegend(modifier = Modifier.padding(top = u * 16f))
        }
    }
}

// Where things stand on the dashboard, in pixels of its own 1280 × 720 picture.
private const val REFERENCE_WIDTH = 1280f
private const val WORDS_X = 157f
private const val WORDS_Y = 116f
private const val GROUP_X = 230f
private const val GROUP_Y = 182f
private const val SECTION_GAP = 130f
/** Tile sizes: small, medium, and the large one; rows and columns of home. */
private const val S = 172f
private const val M = 232f
private const val H = 128f
private const val G = 6f
private const val R2 = H + G
private const val R3 = 2 * (H + G)
private const val C2 = S + G
private const val C3 = C2 + M + G
private const val C4 = C3 + M + G
private const val BIG_W = 2 * M + G
private const val BIG_H = 2 * H + G
/** The two-by-two sections: their second column, and the large tile beside them. */
private const val D2 = S + G
private const val D3 = 2 * (S + G)
private const val WIDE_W = 352f
/** Settings: two rows of four squares. */
private const val Q = 150f
private const val Q1 = Q + G
private const val Q2 = 2 * (Q + G)
private const val Q3 = 3 * (Q + G)
private const val QR = Q + G
private const val CARD_W = 222f
private const val CARD_STEP = 228f
private const val SHUTDOWN_LINES = 3
private const val SLIDE_MILLIS = 260
private const val PAGE_MILLIS = 340
private const val INTRO_MILLIS = 500
