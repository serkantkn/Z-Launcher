package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.theme.findActivity
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
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
    /** What it stands for, for its details on a held press; null for an action of the dashboard's own. */
    val item: XboxItem? = null,
    /** The large tile's turns, shown one after another in its place, as the dashboard's spotlight did. */
    val slides: List<XboxTile> = emptyList(),
    val open: () -> Unit = { item?.open() }
) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2

    /** What the tile shows at [turn]: itself, or one of its slides. */
    fun shown(turn: Int): XboxTile = if (slides.isEmpty()) this else slides[turn.mod(slides.size)]
}

/** A section of the dashboard: its word along the top and its block of tiles. */
internal class XboxSection(val key: String, val title: String, val tiles: List<XboxTile>) {
    val width: Float get() = tiles.maxOf { it.x + it.w }
}

/** The things on the dashboard a section's tiles open, beyond the hubs and apps. */
internal class XboxActions(
    val openCards: (XboxCards) -> Unit,
    val openShutdown: () -> Unit,
    val openGuide: () -> Unit,
    val openSystem: () -> Unit
)

/** Everything the Xbox design can open now, by key: the hubs, the TV's apps, the film left part way. */
@Composable
internal fun xboxItems(places: List<TvPlace>, apps: List<TvApp>, live: TvLive): Map<String, XboxItem> {
    val context = LocalContext.current
    val icons = mapOf(
        "pictures" to Icons.Default.Photo, "videos" to Icons.Default.Movie, "music" to Icons.Default.MusicNote,
        "weather" to Icons.Default.WbSunny, "news" to Icons.Default.Article, "clock" to Icons.Default.Alarm,
        "files" to Icons.Default.Folder, "settings" to Icons.Default.Settings, "android_settings" to Icons.Default.Tv
    )
    return buildMap {
        places.forEach { p ->
            if (p.key == "continue") {
                put(
                    "film:continue",
                    XboxItem("film:continue", p.line ?: p.title, p.title, Icons.Default.PlayArrow, p.image, false,
                        XboxItem.Kind.FILM, film = live.continueWatching, progress = p.progress, run = p.open)
                )
            } else {
                put("hub:${p.key}", XboxItem("hub:${p.key}", p.title, p.line, icons[p.key], p.image, false, XboxItem.Kind.HUB, run = p.open))
            }
        }
        apps.forEach { a ->
            put(
                "app:${a.packageName}",
                XboxItem("app:${a.packageName}", a.label, null, Icons.Default.Apps, a.banner ?: a.icon, a.banner != null,
                    XboxItem.Kind.APP, packageName = a.packageName) {
                    runCatching { context.startActivity(a.launch) }
                }
            )
        }
    }
}

/**
 * The sections, laid out as the dashboard laid its own: home with my pins, recent and pictures down
 * its left, the spotlight in the middle turning through what is new, two tiles at its right and
 * the last things opened along its foot; the others two-by-two beside a large one; settings in
 * two rows of four.
 */
@Composable
internal fun xboxSections(
    items: Map<String, XboxItem>,
    apps: List<TvApp>,
    recent: List<String>,
    onOpenHub: (HubType) -> Unit,
    actions: XboxActions
): List<XboxSection> {
    val context = LocalContext.current
    fun item(key: String, x: Float, y: Float, w: Float, h: Float, label: String? = null, icon: ImageVector? = null, image: Boolean = false, big: String? = null, newKey: String = key): XboxTile? =
        items[key]?.let { XboxTile(newKey, label ?: it.label, x, y, w, h, icon ?: it.icon, if (image || it.kind == XboxItem.Kind.APP) it.image else null, big, it) }
    fun action(key: String, label: String, x: Float, y: Float, w: Float, h: Float, icon: ImageVector, open: () -> Unit) =
        XboxTile(key, label, x, y, w, h, icon, open = open)

    val shuffle = stringResource(R.string.xbox_shuffle)
    val myApps = stringResource(R.string.xbox_my_apps)
    val continueLabel = stringResource(R.string.tv_continue)
    val shuffleAll = {
        com.serkantkn.zunelauncher.data.repository.MusicBridge.shuffleAll()
        onOpenHub(HubType.MUSIC)
    }
    val weatherLine = items["hub:weather"]?.line
    val weatherBig = weatherLine?.substringBefore(' ')

    // ── home ──
    val spotlight = buildList {
        items["film:continue"]?.let { add(XboxTile("spot_film", "$continueLabel: ${it.label}", C2, 0f, BIG_W, BIG_H, it.icon, it.image, item = it)) }
        items["hub:music"]?.takeIf { it.line != null }?.let { add(XboxTile("spot_music", it.line!!, C2, 0f, BIG_W, BIG_H, it.icon, it.image, item = it)) }
        items["hub:pictures"]?.takeIf { it.image != null }?.let { add(XboxTile("spot_pictures", it.label, C2, 0f, BIG_W, BIG_H, it.icon, it.image, item = it)) }
        items["hub:weather"]?.takeIf { weatherLine != null }?.let { add(XboxTile("spot_weather", weatherLine!!.substringAfter(' ', it.label), C2, 0f, BIG_W, BIG_H, it.icon, big = weatherBig, item = it)) }
        items["hub:news"]?.takeIf { it.line != null }?.let { add(XboxTile("spot_news", it.line!!, C2, 0f, BIG_W, BIG_H, it.icon, item = it)) }
        if (size < 2) add(action("spot_shuffle", shuffle, C2, 0f, BIG_W, BIG_H, Icons.Default.Shuffle, shuffleAll))
    }
    // Along the foot, the last two things opened that home does not show already.
    val onHome = setOf("hub:pictures", "hub:weather", "hub:news", "hub:clock")
    val lately = recent.filter { it !in onHome && items.containsKey(it) }.take(2)
    val home = buildList {
        add(action("home_pins", stringResource(R.string.xbox_pins), 0f, 0f, S, H, Icons.Default.PushPin) { actions.openCards(XboxCards.PINS) })
        add(action("home_recent", stringResource(R.string.xbox_recent), 0f, R2, S, H, Icons.Default.History) { actions.openCards(XboxCards.RECENT) })
        add(item("hub:pictures", 0f, R3, S, H))
        add(XboxTile("home_big", spotlight.first().label, C2, 0f, BIG_W, BIG_H, slides = spotlight) {})
        add(item("hub:weather", C4, 0f, S, H, big = weatherBig))
        add(item("hub:news", C4, R2, S, H))
        add(lately.getOrNull(0)?.let { item(it, C2, R3, M, H, newKey = "home_r0") } ?: action("home_apps", myApps, C2, R3, M, H, Icons.Default.Apps) { actions.openCards(XboxCards.APPS) })
        add(lately.getOrNull(1)?.let { item(it, C3, R3, M, H, newKey = "home_r1") } ?: item("hub:music", C3, R3, M, H, image = true, newKey = "home_r1"))
        add(item("hub:clock", C4, R3, S, H))
    }.filterNotNull()

    // ── tv + movies ──
    val movies = buildList {
        add(item("hub:videos", 0f, 0f, S, H, newKey = "tv_videos"))
        add(item("hub:pictures", D2, 0f, S, H, newKey = "tv_pictures"))
        add(action("tv_slides", stringResource(R.string.xbox_slide_show), 0f, R2, S, H, Icons.Default.Slideshow) {
            com.serkantkn.zunelauncher.data.repository.PicturesBridge.playAll()
            onOpenHub(HubType.PICTURES)
        })
        add(item("hub:files", D2, R2, S, H, newKey = "tv_files"))
        add(
            items["film:continue"]?.let { XboxTile("tv_big", "$continueLabel: ${it.label}", D3, 0f, WIDE_W, BIG_H, it.icon, it.image, item = it) }
                ?: item("hub:videos", D3, 0f, WIDE_W, BIG_H, newKey = "tv_big")
        )
    }.filterNotNull()

    // ── music ──
    val musicSection = buildList {
        add(item("hub:music", 0f, 0f, S, H, newKey = "m_music"))
        add(action("m_shuffle", shuffle, D2, 0f, S, H, Icons.Default.Shuffle, shuffleAll))
        add(item("hub:videos", 0f, R2, S, H, newKey = "m_videos"))
        add(item("hub:files", D2, R2, S, H, newKey = "m_files"))
        add(items["hub:music"]?.let { XboxTile("m_big", it.line ?: it.label, D3, 0f, WIDE_W, BIG_H, it.icon, it.image, item = it) })
    }.filterNotNull()

    // ── apps ──
    val appsSection = buildList {
        add(action("a_mine", myApps, 0f, 0f, S, H, Icons.Default.Apps) { actions.openCards(XboxCards.APPS) })
        apps.getOrNull(1)?.let { add(item("app:${it.packageName}", D2, 0f, S, H, newKey = "a_1")) }
        apps.getOrNull(2)?.let { add(item("app:${it.packageName}", 0f, R2, S, H, newKey = "a_2")) }
        apps.getOrNull(3)?.let { add(item("app:${it.packageName}", D2, R2, S, H, newKey = "a_3")) }
        apps.getOrNull(0)?.let { add(item("app:${it.packageName}", D3, 0f, WIDE_W, BIG_H, newKey = "a_0")) }
    }.filterNotNull()

    // ── settings ──
    val settingsSection = buildList {
        add(item("hub:android_settings", 0f, 0f, Q, Q, newKey = "s_system"))
        add(item("hub:settings", Q1, 0f, Q, Q, label = stringResource(R.string.xbox_launcher_settings), newKey = "s_launcher"))
        add(action("s_console", stringResource(R.string.xbox_system_settings), Q2, 0f, Q, Q, Icons.Default.Tune, actions.openSystem))
        add(action("s_guide", stringResource(R.string.xbox_guide), Q3, 0f, Q, Q, Icons.Default.Menu, actions.openGuide))
        add(item("hub:clock", 0f, QR, Q, Q, newKey = "s_clock"))
        add(action("s_exit", stringResource(R.string.xbox_exit), Q1, QR, Q, Q, Icons.AutoMirrored.Filled.ExitToApp) {
            context.findActivity()?.moveTaskToBack(true)
        })
        add(action("s_sleep", stringResource(R.string.vista_sleep), Q2, QR, Q, Q, Icons.Outlined.Bedtime) { shutdownAction(context, 2) })
        add(action("s_off", stringResource(R.string.xbox_turn_off), Q3, QR, Q, Q, Icons.Default.PowerSettingsNew, actions.openShutdown))
    }.filterNotNull()

    return listOf(
        XboxSection("home", stringResource(R.string.xbox_home), home),
        XboxSection("tv", stringResource(R.string.xbox_tv_movies), movies),
        XboxSection("music", stringResource(R.string.xbox_music), musicSection),
        XboxSection("apps", stringResource(R.string.xbox_apps), appsSection),
        XboxSection("settings", stringResource(R.string.xbox_settings), settingsSection)
    ).filter { it.tiles.isNotEmpty() }
}

/** A page over the dashboard: a row of cards, a thing's details, or turning off. */
private sealed interface XboxPage {
    data class Cards(val kind: XboxCards) : XboxPage
    data class Detail(val item: XboxItem) : XboxPage
    data object Shutdown : XboxPage
    data object Search : XboxPage
    data object System : XboxPage
}

/** A "show me" or "sort" choice of a page of cards. */
private class XboxChoice(val label: String, val keep: (XboxItem) -> Boolean = { true }, val order: (List<XboxItem>) -> List<XboxItem> = { it })

/** One of a details page's green command tiles. */
private class XboxCommand(val label: String, val icon: ImageVector, val run: () -> Unit)

/**
 * The Xbox 360's dashboard as Start: the section words along the top, the chosen section's tiles
 * in the middle and its neighbours peeking in at the edges. Left and right walk the tiles and on
 * past a section's edge into the next, the words following; up from the top tiles goes onto the
 * words. A press opens; a held press opens a thing's details, where it is pinned to home. Like
 * Media Center's, it takes the remote whole and draws its own focus: a tile lifted and lit.
 */
@Composable
internal fun XboxStart(
    items: Map<String, XboxItem>,
    apps: List<TvApp>,
    hubOpen: Boolean,
    homeResetSignal: Int,
    onOpenHub: (HubType) -> Unit,
    focusModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pins by XboxShelf.pins.collectAsState()
    val recent by XboxShelf.recent.collectAsState()
    var pages by remember { mutableStateOf(listOf<XboxPage>()) }
    val page = pages.lastOrNull()
    fun push(next: XboxPage) {
        pages = pages + next
    }
    fun pop() {
        pages = pages.dropLast(1)
    }
    var cardAt by remember { mutableIntStateOf(0) }
    var commandAt by remember { mutableIntStateOf(0) }
    var offAt by remember { mutableIntStateOf(0) }
    // A page of cards: what it shows and in what order, and the remote on its two choosers.
    var showAt by remember { mutableIntStateOf(0) }
    var sortAt by remember { mutableIntStateOf(0) }
    var chooser by remember { mutableStateOf<Int?>(null) }
    var dropdown by remember { mutableStateOf(false) }
    var dropAt by remember { mutableIntStateOf(0) }
    // Search: the box, what is typed, what is found.
    val boardFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val boxFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var searchText by remember { mutableStateOf("") }
    var searchAt by remember { mutableIntStateOf(0) }
    var onBox by remember { mutableStateOf(true) }
    val found by androidx.compose.runtime.produceState(emptyList<XboxItem>(), searchText, items) {
        delay(SEARCH_SETTLE_MILLIS)
        val text = searchText
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { XboxSearch.find(context, text, items, onOpenHub) }
        searchAt = 0
    }
    fun openSearch() {
        onBox = true
        push(XboxPage.Search)
    }
    LaunchedEffect(page == XboxPage.Search, onBox) {
        if (page == XboxPage.Search && onBox) {
            delay(BOX_FOCUS_DELAY_MILLIS)
            runCatching { boxFocus.requestFocus() }
        }
    }
    val pinnedText = stringResource(R.string.xbox_pinned)
    val unpinnedText = stringResource(R.string.xbox_unpinned)
    val homeText = stringResource(R.string.xbox_home)
    /** Pins or unpins, and says so in the corner, as the dashboard did. */
    fun togglePin(item: XboxItem) {
        if (!item.pinnable) return
        val pinned = item.key !in XboxShelf.pins.value
        if (pinned) XboxShelf.pin(item.key) else XboxShelf.unpin(item.key)
        com.serkantkn.zunelauncher.data.link.TvLinkNotices.showSystem(
            context, item.label, if (pinned) pinnedText else unpinnedText, homeText,
            if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin
        )
    }

    val system = remember { XboxSystemState() }
    val systemLines = xboxSystemLines()

    val actions = XboxActions(
        openCards = { kind -> cardAt = 0; showAt = 0; sortAt = 0; chooser = null; dropdown = false; push(XboxPage.Cards(kind)) },
        openShutdown = { offAt = 0; push(XboxPage.Shutdown) },
        openGuide = { XboxGuide.show() },
        openSystem = { system.reset(); push(XboxPage.System) }
    )
    val sections = xboxSections(items, apps, recent, onOpenHub, actions)

    var section by remember { mutableIntStateOf(0) }
    var tile by remember { mutableIntStateOf(0) }
    var onWords by remember { mutableStateOf(false) }
    // Up from the section words: the profile in the top right.
    var onProfile by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var backHeld by remember { mutableStateOf(false) }
    val slide = remember { Animatable(0f) }
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (!TvDesign.splashShown) delay(50)
        intro.animateTo(1f, tween(INTRO_MILLIS, easing = FastOutSlowInEasing))
    }
    // The spotlight turns while the dashboard is what is on the screen.
    var turn by remember { mutableIntStateOf(0) }
    LaunchedEffect(hubOpen, page == null) {
        while (!hubOpen && page == null) {
            delay(SPOTLIGHT_MILLIS)
            turn++
        }
    }
    // A page of cards asked for by the guide.
    val pending by XboxShelf.pendingPage.collectAsState()
    LaunchedEffect(pending) {
        val kind = pending ?: return@LaunchedEffect
        delay(PENDING_DELAY_MILLIS)
        pages = emptyList()
        if (kind == XboxCards.SEARCH) openSearch() else actions.openCards(kind)
        XboxShelf.consumePage()
    }
    val pendingSystem by XboxShelf.pendingSystem.collectAsState()
    LaunchedEffect(pendingSystem) {
        if (!pendingSystem) return@LaunchedEffect
        delay(PENDING_DELAY_MILLIS)
        pages = emptyList()
        actions.openSystem()
        XboxShelf.pendingSystem.value = false
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
        pages = emptyList()
        onWords = false
        onProfile = false
        goTo(0, 0)
    }
    var homeSeen by remember { mutableIntStateOf(homeResetSignal) }
    LaunchedEffect(homeResetSignal) {
        if (homeResetSignal != homeSeen) {
            homeSeen = homeResetSignal
            toTop()
        }
    }

    val choiceAll = stringResource(R.string.xbox_all_media)
    val choiceApps = stringResource(R.string.xbox_apps)
    val choiceHubs = stringResource(R.string.xbox_show_hubs)
    val choicePinned = stringResource(R.string.xbox_pins)
    val byName = stringResource(R.string.vista_by_name)
    val byInstall = stringResource(R.string.xbox_sort_installed)
    val byUse = stringResource(R.string.xbox_sort_used)
    val yourOrder = stringResource(R.string.xbox_sort_yours)
    val newest = stringResource(R.string.xbox_sort_newest)
    val installedAt = remember(apps) {
        apps.associate { a -> a.packageName to runCatching { context.packageManager.getPackageInfo(a.packageName, 0).firstInstallTime }.getOrDefault(0L) }
    }
    val nameOrder: (List<XboxItem>) -> List<XboxItem> = { list -> list.sortedBy { it.label.lowercase() } }
    fun shows(kind: XboxCards): List<XboxChoice> = when (kind) {
        XboxCards.APPS -> listOf(XboxChoice(choiceAll), XboxChoice(choicePinned, { it.key in pins }))
        XboxCards.PINS, XboxCards.RECENT -> listOf(
            XboxChoice(choiceAll),
            XboxChoice(choiceApps, { it.kind == XboxItem.Kind.APP }),
            XboxChoice(choiceHubs, { it.kind == XboxItem.Kind.HUB })
        )
        XboxCards.SEARCH -> emptyList()
    }
    fun sorts(kind: XboxCards): List<XboxChoice> = when (kind) {
        XboxCards.APPS -> listOf(
            XboxChoice(byName, order = nameOrder),
            XboxChoice(byInstall, order = { list -> list.sortedByDescending { installedAt[it.packageName] ?: 0L } }),
            XboxChoice(byUse, order = { list -> list.sortedBy { recent.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } } })
        )
        XboxCards.PINS -> listOf(XboxChoice(yourOrder), XboxChoice(byName, order = nameOrder))
        XboxCards.RECENT -> listOf(XboxChoice(newest), XboxChoice(byName, order = nameOrder))
        XboxCards.SEARCH -> emptyList()
    }
    fun cards(kind: XboxCards): List<XboxItem> {
        val all = when (kind) {
            XboxCards.APPS -> apps.mapNotNull { items["app:${it.packageName}"] }
            XboxCards.PINS -> pins.mapNotNull { items[it] }
            XboxCards.RECENT -> recent.mapNotNull { items[it] }
            XboxCards.SEARCH -> found
        }
        val show = shows(kind).getOrNull(showAt)
        val sort = sorts(kind).getOrNull(sortAt)
        return (sort?.order ?: { it })(all.filter { show?.keep?.invoke(it) ?: true })
    }
    /** The thing the remote is on, whatever page it is on. */
    fun current(): XboxItem? = when (val p = page) {
        null -> if (onWords) null else sections[sectionNow].tiles.getOrNull(tile)?.shown(turn)?.item
        is XboxPage.Cards -> if (chooser == null) cards(p.kind).getOrNull(cardAt) else null
        XboxPage.Search -> if (onBox) null else found.getOrNull(searchAt)
        else -> null
    }

    val texts = XboxCommandTexts(
        open = stringResource(R.string.xbox_open),
        resume = stringResource(R.string.xbox_resume),
        fromStart = stringResource(R.string.xbox_from_start),
        pin = stringResource(R.string.xbox_pin),
        unpin = stringResource(R.string.xbox_unpin),
        front = stringResource(R.string.xbox_move_front),
        info = stringResource(R.string.xbox_app_info),
        uninstall = stringResource(R.string.xbox_uninstall),
        videos = stringResource(R.string.vista_video_library)
    )
    fun commands(item: XboxItem): List<XboxCommand> = buildList {
        when (item.kind) {
            XboxItem.Kind.MEDIA -> add(XboxCommand(texts.open, item.icon ?: Icons.Default.PlayArrow) { item.open() })
            XboxItem.Kind.FILM -> {
                add(XboxCommand(texts.resume, Icons.Default.PlayArrow) { item.open() })
                item.film?.let { film ->
                    add(XboxCommand(texts.fromStart, Icons.Default.Replay) {
                        com.serkantkn.zunelauncher.ui.screens.video.VideoResumeStore(context)
                            .remember(com.serkantkn.zunelauncher.ui.screens.video.VideoResumeStore.keyOf(film.uri), 0L, 0L)
                        com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer.open(context, film.uri, film.title)
                    })
                }
                items["hub:videos"]?.let { v -> add(XboxCommand(texts.videos, Icons.Default.Movie) { v.open() }) }
            }
            else -> {
                add(XboxCommand(texts.open, item.icon ?: Icons.Default.PlayArrow) { item.open() })
                // Read from the state, so the tile turns from "pin" to "unpin" as it is pressed.
                if (item.key in pins) {
                    add(XboxCommand(texts.unpin, Icons.Outlined.PushPin) { togglePin(item) })
                    if (pins.firstOrNull() != item.key) add(XboxCommand(texts.front, Icons.Default.VerticalAlignTop) { XboxShelf.moveToFront(item.key) })
                } else {
                    add(XboxCommand(texts.pin, Icons.Default.PushPin) { togglePin(item) })
                }
                item.packageName?.let { pkg ->
                    add(XboxCommand(texts.info, Icons.Default.Info) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    })
                    add(XboxCommand(texts.uninstall, Icons.Default.Delete) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                }
            }
        }
    }

    /** The tile in the way of travel: only one in line with this one, the nearest. */
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
    /** Over a section's edge: the tile at that edge of [next] nearest in height to [fromY]. */
    fun enter(next: XboxSection, fromY: Float, fromLeft: Boolean): Int {
        val edge = if (fromLeft) next.tiles.minOf { it.x } else next.tiles.maxOf { it.x + it.w }
        val candidates = next.tiles.withIndex().filter { (_, t) -> if (fromLeft) t.x <= edge + 1 else t.x + t.w >= edge - 1 }
        return candidates.minByOrNull { (_, t) -> abs(t.cy - fromY) }?.index ?: 0
    }

    fun choose() {
        when (val p = page) {
            null -> if (onProfile) {
                    // The profile: the TV and the phone paired with it, in the launcher's settings.
                    com.serkantkn.zunelauncher.data.repository.SettingsBridge.open("CONNECTIVITY")
                    onOpenHub(HubType.SETTINGS)
                } else if (!onWords) sections[sectionNow].tiles.getOrNull(tile)?.shown(turn)?.open?.invoke()
                else {
                    onWords = false
                    val tiles = sections[sectionNow].tiles
                    tile = tiles.indices.minByOrNull { tiles[it].y * 4 + tiles[it].x } ?: 0
                }
            is XboxPage.Cards -> when {
                dropdown -> {
                    if (chooser == 0) showAt = dropAt else sortAt = dropAt
                    dropdown = false
                    cardAt = 0
                }
                chooser != null -> {
                    dropAt = if (chooser == 0) showAt else sortAt
                    dropdown = true
                }
                else -> cards(p.kind).getOrNull(cardAt)?.open()
            }
            XboxPage.Search -> found.getOrNull(searchAt)?.open()
            XboxPage.System -> system.choose(systemLines)
            is XboxPage.Detail -> commands(p.item).getOrNull(commandAt)?.run?.invoke()
            XboxPage.Shutdown -> {
                pop()
                when (offAt) {
                    0 -> shutdownAction(context, 1)
                    1 -> shutdownAction(context, 2)
                    2 -> shutdownAction(context, 0)
                }
            }
        }
    }
    fun hold() {
        val item = current() ?: return
        commandAt = 0
        push(XboxPage.Detail(item))
    }
    /** Ⓨ: on my pins, move to front; anywhere else, pin or unpin. */
    fun yButton() {
        val item = current() ?: return
        val p = page
        if (p is XboxPage.Cards && p.kind == XboxCards.PINS) XboxShelf.moveToFront(item.key) else togglePin(item)
    }
    fun move(key: Key) {
        when (val p = page) {
            XboxPage.Shutdown -> when (key) {
                Key.DirectionUp -> offAt = (offAt - 1).coerceAtLeast(0)
                Key.DirectionDown -> offAt = (offAt + 1).coerceAtMost(SHUTDOWN_LINES - 1)
                else -> {}
            }
            is XboxPage.Cards -> {
                val last = cards(p.kind).lastIndex
                val options = if (chooser == 0) shows(p.kind) else sorts(p.kind)
                when {
                    dropdown -> when (key) {
                        Key.DirectionUp -> dropAt = (dropAt - 1).coerceAtLeast(0)
                        Key.DirectionDown -> dropAt = (dropAt + 1).coerceAtMost(options.lastIndex)
                        else -> {}
                    }
                    chooser != null -> when (key) {
                        Key.DirectionLeft -> chooser = 0
                        Key.DirectionRight -> if (sorts(p.kind).isNotEmpty()) chooser = 1
                        Key.DirectionDown -> chooser = null
                        else -> {}
                    }
                    else -> when (key) {
                        Key.DirectionLeft -> cardAt = (cardAt - 1).coerceAtLeast(0)
                        Key.DirectionRight -> cardAt = (cardAt + 1).coerceAtMost(last.coerceAtLeast(0))
                        Key.DirectionUp -> chooser = if (shows(p.kind).size > 1) 0 else 1
                        else -> {}
                    }
                }
            }
            XboxPage.System -> system.move(key, systemLines)
            XboxPage.Search -> when (key) {
                Key.DirectionLeft -> searchAt = (searchAt - 1).coerceAtLeast(0)
                Key.DirectionRight -> searchAt = (searchAt + 1).coerceAtMost(found.lastIndex.coerceAtLeast(0))
                Key.DirectionUp -> onBox = true
                else -> {}
            }
            is XboxPage.Detail -> {
                val count = commands(p.item).size
                commandAt = when (key) {
                    Key.DirectionLeft -> if (commandAt % 2 == 1) commandAt - 1 else commandAt
                    Key.DirectionRight -> if (commandAt % 2 == 0 && commandAt + 1 < count) commandAt + 1 else commandAt
                    Key.DirectionUp -> if (commandAt >= 2) commandAt - 2 else commandAt
                    Key.DirectionDown -> if (commandAt + 2 < count) commandAt + 2 else commandAt
                    else -> commandAt
                }
            }
            null -> if (onProfile) {
                if (key == Key.DirectionDown || key == Key.DirectionLeft) onProfile = false
            } else if (onWords) {
                when (key) {
                    Key.DirectionUp -> onProfile = true
                    Key.DirectionLeft -> if (sectionNow > 0) goTo(sectionNow - 1, 0)
                    Key.DirectionRight -> if (sectionNow < sections.lastIndex) goTo(sectionNow + 1, 0)
                    Key.DirectionDown -> choose()
                    else -> {}
                }
            } else {
                val current = sections[sectionNow]
                val from = current.tiles.getOrNull(tile) ?: current.tiles.first()
                when (key) {
                    // Off home's left edge is the search, as on the dashboard.
                    Key.DirectionLeft -> neighbour(current.tiles, from, -1, 0)?.let { tile = it }
                        ?: if (sectionNow > 0) goTo(sectionNow - 1, enter(sections[sectionNow - 1], from.cy, fromLeft = false)) else openSearch()
                    Key.DirectionRight -> neighbour(current.tiles, from, 1, 0)?.let { tile = it }
                        ?: if (sectionNow < sections.lastIndex) goTo(sectionNow + 1, enter(sections[sectionNow + 1], from.cy, fromLeft = true)) else Unit
                    Key.DirectionUp -> neighbour(current.tiles, from, 0, -1)?.let { tile = it } ?: run { onWords = true }
                    Key.DirectionDown -> neighbour(current.tiles, from, 0, 1)?.let { tile = it }
                    else -> {}
                }
            }
        }
    }

    val keys = Modifier.onPreviewKeyEvent { event ->
        if (hubOpen) return@onPreviewKeyEvent false
        val down = event.type == KeyEventType.KeyDown
        // In the search box the keys are the box's: typing, the TV's keyboard. Down leaves for the
        // cards, Back for the dashboard.
        if (page == XboxPage.Search && onBox) {
            when (event.key) {
                Key.DirectionDown -> {
                    if (down && found.isNotEmpty()) {
                        onBox = false
                        searchAt = 0
                        runCatching { boardFocus.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }
                Key.Back -> {
                    if (down) backHeld = true
                    else if (backHeld) {
                        backHeld = false
                        pop()
                        runCatching { boardFocus.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }
                else -> return@onPreviewKeyEvent false
            }
        }
        when (event.key) {
            Key.ButtonX, Key(android.view.KeyEvent.KEYCODE_PROG_BLUE) -> {
                if (down && event.nativeKeyEvent.repeatCount == 0) hold()
                true
            }
            Key.ButtonY, Key(android.view.KeyEvent.KEYCODE_PROG_YELLOW) -> {
                if (down && event.nativeKeyEvent.repeatCount == 0) yButton()
                true
            }
            Key.Back -> {
                if (down) {
                    backHeld = when {
                        page == XboxPage.System && system.back() -> true
                        dropdown -> { dropdown = false; true }
                        chooser != null -> { chooser = null; true }
                        page != null -> { pop(); true }
                        onWords || onProfile || sectionNow != 0 || tile != 0 -> { toTop(); true }
                        else -> false
                    }
                    backHeld
                } else {
                    val was = backHeld
                    backHeld = false
                    was
                }
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                // A press acts when it is let go; held, it opens the details instead.
                if (down) {
                    if (event.nativeKeyEvent.repeatCount >= 1 && !held) {
                        held = true
                        hold()
                    }
                } else {
                    if (held) held = false else choose()
                }
                true
            }
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                if (down) move(event.key)
                true
            }
            else -> false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(focusModifier)
            .focusRequester(boardFocus)
            .tvOwnFocus()
            .then(keys)
            .focusable()
    ) {
        val u = maxWidth / REFERENCE_WIDTH
        val density = LocalDensity.current
        val overBoard = page is XboxPage.Cards || page is XboxPage.Detail || page == XboxPage.Search || page == XboxPage.System
        val pageShown by animateFloatAsState(if (overBoard) 1f else 0f, tween(PAGE_MILLIS, easing = FastOutSlowInEasing), label = "xboxPage")
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
                        val focused = i == sectionNow && ti == tile && !onWords && !onProfile && page == null
                        XboxTileView(
                            tile = t,
                            turn = turn,
                            focused = focused,
                            u = u,
                            modifier = Modifier
                                .offset(x = u * (sx + t.x), y = u * (GROUP_Y + t.y))
                                .graphicsLayer { alpha = faint }
                        )
                    }
                }
            }
            // The search, waiting behind home's left edge.
            if (sectionNow == 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = u * 0f, y = u * 300f)
                        .size(u * 85f, u * 64f)
                        .background(Color(0xFF6E7476).copy(alpha = 0.75f))
                ) {
                    Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(u * 30f))
                }
            }
            XboxLegend(back = false, hold = true, pin = stringResource(R.string.xbox_pin_short), guide = true, modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f))
            XboxProfile(lit = onProfile && page == null, u = u, modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 30f, end = u * 90f))
        }
        val shownPage = pages.lastOrNull { it is XboxPage.Cards || it is XboxPage.Detail || it == XboxPage.Search || it == XboxPage.System }
        if (pageShown > 0f && shownPage != null) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val grow = 0.8f + 0.2f * pageShown
                    scaleX = grow
                    scaleY = grow
                    alpha = pageShown
                }
            ) {
                when (shownPage) {
                    is XboxPage.Cards -> XboxCardsPage(
                        kind = shownPage.kind,
                        cards = cards(shownPage.kind),
                        at = cardAt,
                        u = u,
                        shows = shows(shownPage.kind).map { it.label },
                        showAt = showAt,
                        sorts = sorts(shownPage.kind).map { it.label },
                        sortAt = sortAt,
                        chooser = chooser,
                        dropdown = dropdown,
                        dropAt = dropAt
                    )
                    XboxPage.Search -> XboxSearchPage(
                        text = searchText,
                        onText = { searchText = it },
                        onDone = {
                            if (found.isNotEmpty()) {
                                onBox = false
                                searchAt = 0
                                runCatching { boardFocus.requestFocus() }
                            }
                        },
                        results = found,
                        at = searchAt,
                        onBox = onBox,
                        boxFocus = boxFocus,
                        u = u
                    )
                    is XboxPage.Detail -> XboxDetailPage(shownPage.item, commands(shownPage.item), commandAt, u)
                    XboxPage.System -> XboxSystemPage(systemLines, system, u)
                    else -> {}
                }
            }
        }
        if (page == XboxPage.Shutdown) XboxShutdownPanel(offAt, u)
    }
}

private class XboxCommandTexts(
    val open: String, val resume: String, val fromStart: String, val pin: String, val unpin: String,
    val front: String, val info: String, val uninstall: String, val videos: String
)

/** A tile: Xbox green with its sign and its name, or a picture with its name on a dark band; the spotlight turning. */
@Composable
private fun XboxTileView(tile: XboxTile, turn: Int, focused: Boolean, u: Dp, modifier: Modifier) {
    val lift by animateFloatAsState(if (focused) 1.07f else 1f, tween(140), label = "xboxLift")
    Box(
        modifier = modifier
            .size(u * tile.w, u * tile.h)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                shadowElevation = if (focused) 18f else 0f
            }
    ) {
        if (tile.slides.size > 1) {
            AnimatedContent(
                targetState = turn.mod(tile.slides.size),
                transitionSpec = { (fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 8 }) togetherWith fadeOut(tween(450)) },
                label = "xboxSpotlight",
                modifier = Modifier.fillMaxSize()
            ) { i -> XboxTileFace(tile.slides[i], focused, u) }
        } else {
            XboxTileFace(tile.shown(turn), focused, u)
        }
    }
}

@Composable
private fun XboxTileFace(tile: XboxTile, focused: Boolean, u: Dp) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize().background(if (focused && tile.image == null) Xbox.GreenLit else Xbox.Green)) {
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
            tile.item?.progress?.let { p ->
                Box(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(p).height(u * 4f).background(Xbox.GreenLit))
            }
        } else {
            when {
                tile.big != null -> Text(
                    tile.big,
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = with(density) { (u * minOf(46f, tile.h * 0.4f)).toSp() }),
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
                maxLines = if (tile.h > 200f) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 10f, end = u * 8f, bottom = u * 8f)
            )
        }
    }
}

/**
 * A page of cards, as the dashboard showed "my pins": a row of tall white cards running off to the
 * right, each a picture with its name under it and a small sign of what it is; the one the remote
 * is on lifted out of the row. Which of how many in the top right.
 */
@Composable
private fun XboxCardsPage(
    kind: XboxCards,
    cards: List<XboxItem>,
    at: Int,
    u: Dp,
    shows: List<String>,
    showAt: Int,
    sorts: List<String>,
    sortAt: Int,
    chooser: Int?,
    dropdown: Boolean,
    dropAt: Int
) {
    val density = LocalDensity.current
    val shift by animateFloatAsState((at - 1).coerceAtLeast(0).toFloat(), tween(SLIDE_MILLIS, easing = FastOutSlowInEasing), label = "xboxCardsShift")
    val title = stringResource(
        when (kind) {
            XboxCards.APPS -> R.string.xbox_my_apps
            XboxCards.PINS -> R.string.xbox_pins
            XboxCards.RECENT -> R.string.xbox_recent
            XboxCards.SEARCH -> R.string.xbox_search
        }
    )
    Box(modifier = Modifier.fillMaxSize()) {
        // "show me" and "sort", as the dashboard set them over its rows of cards; up goes onto them.
        Row(modifier = Modifier.zIndex(1f).offset(x = u * 104f, y = u * 76f), horizontalArrangement = Arrangement.spacedBy(u * 40f)) {
            listOf(
                Triple(stringResource(R.string.xbox_show_me), shows, showAt),
                Triple(stringResource(R.string.xbox_sort), sorts, sortAt)
            ).forEachIndexed { i, (name, options, chosen) ->
                if (options.isEmpty()) return@forEachIndexed
                val on = chooser == i
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val grow = if (on) 1.06f else 1f
                            scaleX = grow
                            scaleY = grow
                        }
                        .background(if (on && !dropdown) Xbox.Green else Color.Transparent)
                        .padding(horizontal = u * 8f, vertical = u * 4f)
                ) {
                    Text("⌄ $name", style = small(density, u, 18f), color = Color.White)
                    Text(options.getOrElse(chosen) { "" }, style = small(density, u, 24f), color = Color.White.copy(alpha = if (on) 1f else 0.8f))
                    if (on && dropdown) {
                        Column(modifier = Modifier.padding(top = u * 6f).background(Xbox.Panel).width(u * 260f)) {
                            options.forEachIndexed { j, option ->
                                Text(
                                    option,
                                    style = small(density, u, 20f).copy(shadow = null),
                                    color = if (j == dropAt) Color.White else Xbox.Ink,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (j == dropAt) Xbox.Green else Color.Transparent)
                                        .padding(horizontal = u * 14f, vertical = u * 7f)
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 78f, end = u * 95f)) {
            Text(title, style = small(density, u, 32f), color = Color.White)
            if (cards.isNotEmpty()) {
                Text(stringResource(R.string.xbox_n_of_m, at.coerceAtMost(cards.lastIndex) + 1, cards.size), style = small(density, u, 19f), color = Color.White)
            }
        }
        if (cards.isEmpty()) {
            Text(
                stringResource(
                    when {
                        // Something is there, only not under what is shown.
                        showAt != 0 -> R.string.xbox_none_shown
                        else -> when (kind) {
                            XboxCards.APPS -> R.string.vista_no_apps
                            XboxCards.PINS -> R.string.xbox_no_pins
                            XboxCards.RECENT -> R.string.xbox_no_recent
                            XboxCards.SEARCH -> R.string.xbox_search_none
                        }
                    }
                ),
                style = small(density, u, 22f),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = u * 200f)
            )
        }
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            cards.forEachIndexed { i, item ->
                val x = 64f + (i - shift) * CARD_STEP
                if (x < -CARD_W || x > 1300f) return@forEachIndexed
                val chosen = i == at && chooser == null
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
                        modifier = Modifier.fillMaxWidth().height(u * 280f).background(if (item.image == null) Xbox.Green else Color(0xFFA9AEB0))
                    ) {
                        when {
                            item.image != null -> Image(
                                item.image, null,
                                contentScale = if (item.banner) ContentScale.Fit else if (item.kind == XboxItem.Kind.APP) ContentScale.Inside else ContentScale.Crop,
                                modifier = if (item.banner) Modifier.fillMaxWidth() else if (item.kind == XboxItem.Kind.APP) Modifier.size(u * 96f) else Modifier.fillMaxSize()
                            )
                            item.icon != null -> Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(u * 72f))
                        }
                    }
                    Spacer(modifier = Modifier.height(u * 10f))
                    Text(item.label, style = small(density, u, 19f).copy(shadow = null), color = Xbox.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        when (item.kind) {
                            XboxItem.Kind.APP -> Icons.Default.Apps
                            XboxItem.Kind.HUB -> item.icon ?: Icons.Default.Apps
                            XboxItem.Kind.FILM -> Icons.Default.Movie
                            XboxItem.Kind.MEDIA -> item.icon ?: Icons.Default.Movie
                        },
                        null, tint = Xbox.InkDim, modifier = Modifier.align(Alignment.End).size(u * 18f)
                    )
                }
            }
        }
        XboxLegend(
            hold = true,
            pin = stringResource(if (kind == XboxCards.PINS) R.string.xbox_move_front else R.string.xbox_pin_short),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f)
        )
    }
}

/**
 * A thing's page, as the dashboard showed a game's: its name in the top right, "overview" at the
 * left, its commands as green tiles two by two - play, pin to home - its picture beside them and
 * what is known of it beyond.
 */
@Composable
private fun XboxDetailPage(item: XboxItem, commands: List<XboxCommand>, at: Int, u: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val facts = remember(item.key, item.line) { detailFacts(context, item) }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            item.label,
            style = small(density, u, 34f),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 40f, end = u * 95f, start = u * 400f)
        )
        Text(
            stringResource(R.string.xbox_overview),
            style = small(density, u, 28f),
            color = Color.White,
            modifier = Modifier.offset(x = u * 104f, y = u * 88f)
        )
        commands.forEachIndexed { i, command ->
            val col = i % 2
            val row = i / 2
            val chosen = i == at
            val lift by animateFloatAsState(if (chosen) 1.06f else 1f, tween(140), label = "xboxCommand")
            Box(
                modifier = Modifier
                    .offset(x = u * (104f + col * (CMD_W + G)), y = u * (150f + row * (CMD_H + G)))
                    .size(u * CMD_W, u * CMD_H)
                    .graphicsLayer {
                        scaleX = lift
                        scaleY = lift
                        shadowElevation = if (chosen) 16f else 0f
                    }
                    .background(if (chosen) Xbox.GreenLit else Xbox.Green)
            ) {
                Icon(command.icon, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(start = u * 10f, top = u * 10f).size(u * 28f))
                Text(
                    command.label,
                    style = small(density, u, 15f).copy(shadow = null, lineHeight = with(density) { (u * 17f).toSp() }),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 8f, end = u * 6f, bottom = u * 6f)
                )
            }
        }
        val pictureX = 104f + 2 * (CMD_W + G) + 14f
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(x = u * pictureX, y = u * 150f)
                .size(u * PICTURE_W, u * (2 * CMD_H + G))
                .background(if (item.image == null) Xbox.Green else Color(0xFF2B2F30))
        ) {
            when {
                item.image != null -> Image(
                    item.image, null,
                    contentScale = if (item.kind == XboxItem.Kind.APP && !item.banner) ContentScale.Inside else ContentScale.Crop,
                    modifier = if (item.kind == XboxItem.Kind.APP && !item.banner) Modifier.size(u * 120f) else Modifier.fillMaxSize()
                )
                item.icon != null -> Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(u * 80f))
            }
            item.progress?.let { p ->
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(u * 5f).background(Xbox.GreenLit))
            }
        }
        Column(
            modifier = Modifier
                .offset(x = u * (pictureX + PICTURE_W + 24f), y = u * 150f)
                .width(u * (1180f - pictureX - PICTURE_W - 24f)),
            verticalArrangement = Arrangement.spacedBy(u * 6f)
        ) {
            facts.forEach { line ->
                Text(line, style = small(density, u, 19f), color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        XboxLegend(modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f))
    }
}

/** What a details page says of a thing: an app's version and when it came, a film's place, a hub's line. */
private fun detailFacts(context: Context, item: XboxItem): List<String> = buildList {
    when (item.kind) {
        XboxItem.Kind.APP -> item.packageName?.let { pkg ->
            runCatching {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                info.versionName?.let { add(context.getString(R.string.xbox_version, it)) }
                add(context.getString(R.string.xbox_installed, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(info.firstInstallTime))))
            }
            add(pkg)
        }
        XboxItem.Kind.FILM -> item.film?.let { film ->
            add(film.title)
            add("${clock(film.positionMs)} / ${clock(film.durationMs)}")
        }
        XboxItem.Kind.HUB, XboxItem.Kind.MEDIA -> item.line?.let { add(it) }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun small(density: Density, u: Dp, px: Float) =
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
/** A details page: its command tiles and its picture. */
private const val CMD_W = 176f
private const val CMD_H = 96f
private const val PICTURE_W = 320f
private const val SHUTDOWN_LINES = 3
private const val SLIDE_MILLIS = 260
private const val PAGE_MILLIS = 340
private const val INTRO_MILLIS = 500
/** How long the spotlight shows each of its turns. */
private const val SPOTLIGHT_MILLIS = 7_000L
private const val PENDING_DELAY_MILLIS = 150L
private const val SEARCH_SETTLE_MILLIS = 250L
private const val BOX_FOCUS_DELAY_MILLIS = 120L
