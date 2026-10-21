package com.serkantkn.zeta.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.AppInfo
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.Note
import com.serkantkn.zeta.data.model.RecentAlbum
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.data.repository.MusicBridge
import com.serkantkn.zeta.data.repository.NotesBridge
import com.serkantkn.zeta.data.repository.PicturesBridge
import com.serkantkn.zeta.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zeta.data.model.QuickplayPin
import com.serkantkn.zeta.data.model.Quickplay
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.PlayArrow
import com.serkantkn.zeta.ui.components.TileIconImage
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.screens.notes.noteColor
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import com.serkantkn.zeta.util.TileIconFace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Quickplay: the page to the right of the Zune list, as it was on the Zune HD — what you lately
 * played, took, wrote and opened, as pictures, with the app list one more page on.
 *
 * It had a corner of the Start list for a while; given a page of its own it can afford the
 * record's name beside its cover, six pictures instead of one, three notes instead of one and
 * the apps with their names. Nothing here is asked of the phone: the records and the apps are
 * what the launcher itself started, the pictures and notes what it already reads for its tiles.
 */
@Composable
fun QuickplayScreen(
    isCurrentPage: Boolean,
    /** True while a hub stands over the pager; its closing is what brings the page back in. */
    isHubOpen: Boolean,
    onHubSelected: (HubType) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Drawn above the page's name, in the same scroll: on a tablet, the pinned tiles stand over
     * quickplay in the one pane at the right of the Zune list.
     */
    top: (@Composable ColumnScope.() -> Unit)? = null,
    /** Room left at the top, under the status bar; less where something stands above. */
    topGap: androidx.compose.ui.unit.Dp = 36.dp,
    viewModel: HomeHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val state by viewModel.quickplay.collectAsState()
    val cornerStyle by viewModel.tileCornerStyle.collectAsState()
    val tileIconStyle by viewModel.tileIconStyle.collectAsState()
    val tileIconPack by viewModel.iconPackPackage.collectAsState()
    val tileIconOverrides by viewModel.tileIconOverrides.collectAsState()
    val face: (AppInfo) -> TileIconFace = { app ->
        viewModel.tileIconFace(app, tileIconStyle, tileIconPack, tileIconOverrides)
    }
    val shape = if (cornerStyle == TileCornerStyle.ROUNDED) RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp)
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    // Swiping between pages moves nothing here: the page is simply there, as a page of the Zune
    // was. The stagger is for coming *back* — from an app this page opened, or from a hub it
    // opened — the way Start comes back after a hub.
    val entrance = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // The Zune layout's own slide is quicker than the turnstile it replaces.
    val zuneMotion by rememberUpdatedState(com.serkantkn.zeta.ui.animation.LocalZuneMotion.current)
    fun enterMillis() = if (zuneMotion) com.serkantkn.zeta.ui.animation.ZUNE_ENTER_MILLIS else ENTRANCE_MILLIS
    fun exitMillis() = if (zuneMotion) com.serkantkn.zeta.ui.animation.ZUNE_EXIT_MILLIS else EXIT_MILLIS
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentlyShown by rememberUpdatedState(isCurrentPage)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentlyShown) {
                scope.launch {
                    entrance.snapTo(0f)
                    entrance.animateTo(1f, tween(enterMillis(), easing = LinearEasing))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isCurrentPage, isHubOpen) {
        if (!isCurrentPage) {
            entrance.snapTo(1f)
        } else if (!isHubOpen && entrance.value > 1f) {
            // A hub was opened from here and has now closed over it: the page turned away to
            // open it, so it comes back the same way.
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(enterMillis(), easing = LinearEasing))
        }
    }

    /**
     * Opens something from the page: the page turns away first, and comes back staggered. One
     * thing at a time — two quick taps used to open both — and with motion off, at once.
     */
    val animationsOn = LocalAnimationsEnabled.current
    var leaving by remember { mutableStateOf(false) }
    fun leaveFor(action: () -> Unit) {
        if (leaving) return
        if (!animationsOn) {
            action()
            return
        }
        leaving = true
        scope.launch {
            if (zuneMotion) {
                // What opens comes up while the page is still sliding away, as from the list.
                val away = launch { entrance.animateTo(2f, tween(exitMillis(), easing = LinearEasing)) }
                kotlinx.coroutines.delay(com.serkantkn.zeta.ui.animation.ZUNE_OPEN_AT_MILLIS)
                action()
                away.join()
            } else {
                entrance.animateTo(2f, tween(exitMillis(), easing = LinearEasing))
                action()
            }
            leaving = false
        }
    }

    // Quickplay's "new" reads the music afresh each time the page comes into view.
    LaunchedEffect(isCurrentPage) { if (isCurrentPage) viewModel.refreshNewMusic() }

    // Held, anything here can be pinned - or, pinned, taken off again.
    var pinTarget by remember { mutableStateOf<QuickplayPin?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun albumPin(album: RecentAlbum) = QuickplayPin(QuickplayPin.Kind.ALBUM, album.id.toString(), album.title, album.artist, album.artUri)
    fun photoPin(photo: com.serkantkn.zeta.data.model.MediaImage) = QuickplayPin(
        if (photo.isVideo) QuickplayPin.Kind.VIDEO else QuickplayPin.Kind.PHOTO,
        photo.uri.toString(), photo.displayName, art = photo.uri.toString()
    )
    fun notePin(note: Note) = QuickplayPin(QuickplayPin.Kind.NOTE, note.id, note.title, note.colorHex.orEmpty())
    fun appPin(app: AppInfo) = QuickplayPin(QuickplayPin.Kind.APP, app.packageName, app.label)
    fun openPin(pin: QuickplayPin) = leaveFor { openQuickplayItem(pin, context, viewModel, onHubSelected) }

    // Across a tablet three squares to a row would each be the size of a hand; the rows hold
    // twice as many there.
    val wide = com.serkantkn.zeta.ui.theme.LocalIsWideScreen.current
    val squares = if (wide) 6 else 3
    val appsPerRow = if (wide) 8 else 4

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        Spacer(modifier = Modifier.height(topInset + topGap))
        top?.invoke(this)
        Text(
            text = stringResource(R.string.zune_quickplay),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 60.sp,
                letterSpacing = (-2).sp,
                lineHeight = 66.sp
            ),
            color = ink,
            maxLines = 1,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .w10mStaggeredAnimation({ entrance.value }, 0)
        )

        if (state.isEmpty) {
            Text(
                text = stringResource(R.string.quickplay_empty),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, lineHeight = 26.sp),
                color = zuneColors.textMuted,
                modifier = Modifier.w10mStaggeredAnimation({ entrance.value }, 1)
            )
        }

        var slot = 1

        // ── Pins: what was chosen to stay, ahead of everything that merely happened ──
        if (state.pins.isNotEmpty()) {
            SectionWord(stringResource(R.string.quickplay_pins), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            state.pins.chunked(squares).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Gap)
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    row.forEach { pin ->
                        PinSquare(
                            pin = pin,
                            shape = shape,
                            face = viewModel.appFor(pin.key)?.takeIf { pin.kind == QuickplayPin.Kind.APP }?.let(face),
                            modifier = Modifier.weight(1f),
                            onLongClick = { pinTarget = pin },
                            onClick = { openPin(pin) }
                        )
                    }
                    repeat(squares - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            Spacer(modifier = Modifier.height(SectionGap - Gap))
        }

        // ── Records ──
        if (state.recentAlbums.isNotEmpty()) {
            SectionWord(stringResource(R.string.zune_last_played), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            val first = state.recentAlbums.first()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .w10mStaggeredAnimation({ entrance.value }, slot++),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AlbumSquare(first, shape, size = BigSquare, onLongClick = { pinTarget = albumPin(first) }) { leaveFor { playAlbum(first, onHubSelected) } }
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(
                        text = first.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp, lineHeight = 30.sp),
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = first.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ActionWord(stringResource(R.string.files_viewer_play), Modifier.padding(top = 10.dp)) {
                        leaveFor { playAlbum(first, onHubSelected) }
                    }
                }
            }
            val rest = state.recentAlbums.drop(1)
            if (rest.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier
                        .padding(top = Gap)
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    rest.forEach { album ->
                        AlbumSquare(album, shape, size = SmallSquare, onLongClick = { pinTarget = albumPin(album) }) {
                            leaveFor { playAlbum(album, onHubSelected) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(SectionGap))
        }

        // ── Pictures ──
        if (state.recentPhotos.isNotEmpty()) {
            SectionWord(stringResource(R.string.quickplay_photos), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            state.recentPhotos.chunked(squares).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Gap)
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    row.forEach { photo ->
                        Square(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = shape,
                            onLongClick = { pinTarget = photoPin(photo) },
                            onClick = {
                                leaveFor {
                                    PicturesBridge.openPhoto(photo.uri)
                                    onHubSelected(HubType.PICTURES)
                                }
                            }
                        ) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    repeat(squares - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            Spacer(modifier = Modifier.height(SectionGap - Gap))
        }

        // ── Notes ──
        if (state.recentNotes.isNotEmpty()) {
            SectionWord(stringResource(R.string.quickplay_notes), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            state.recentNotes.forEach { note ->
                NoteCard(
                    note = note,
                    shape = shape,
                    modifier = Modifier
                        .padding(bottom = Gap)
                        .w10mStaggeredAnimation({ entrance.value }, slot++),
                    onLongClick = { pinTarget = notePin(note) }
                ) {
                    leaveFor {
                        NotesBridge.open(note.id)
                        onHubSelected(HubType.NOTES)
                    }
                }
            }
            Spacer(modifier = Modifier.height(SectionGap - Gap))
        }

        // ── Apps ──
        if (state.recentApps.isNotEmpty()) {
            SectionWord(stringResource(R.string.zune_recent_apps), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            state.recentApps.chunked(appsPerRow).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Gap)
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    row.forEach { app ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Square(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                shape = shape,
                                onLongClick = { pinTarget = appPin(app) },
                                onClick = { leaveFor { viewModel.launchApp(app.packageName) } }
                            ) {
                                TileIconImage(
                                    face = face(app),
                                    size = 34.dp,
                                    ink = Color.White,
                                    contentDescription = app.label,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = zuneColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    repeat(appsPerRow - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        // ── New: what came onto the phone lately ──
        if (state.newAlbums.isNotEmpty() || state.newVideos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(SectionGap))
            SectionWord(stringResource(R.string.quickplay_new), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            if (state.newAlbums.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier
                        .padding(bottom = Gap)
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    state.newAlbums.forEach { album ->
                        AlbumSquare(album, shape, size = SmallSquare, onLongClick = { pinTarget = albumPin(album) }) {
                            leaveFor { playAlbum(album, onHubSelected) }
                        }
                    }
                }
            }
            if (state.newVideos.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    state.newVideos.forEach { video ->
                        Square(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = shape,
                            onLongClick = { pinTarget = photoPin(video) },
                            onClick = {
                                leaveFor {
                                    com.serkantkn.zeta.ui.screens.video.VideoPlayer.open(context, video.uri, video.displayName)
                                }
                            }
                        ) {
                            AsyncImage(
                                model = video.uri,
                                contentDescription = video.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            PlayMark(Modifier.align(Alignment.Center))
                        }
                    }
                    repeat(squares - state.newVideos.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        Text(
            text = stringResource(R.string.quickplay_hint),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textDim,
            modifier = Modifier.padding(top = 20.dp)
        )
        Spacer(modifier = Modifier.height(bottomInset + 32.dp))
    }

    // Back closes the sheet rather than the launcher passing it over.
    androidx.activity.compose.BackHandler(enabled = pinTarget != null) { pinTarget = null }
    pinTarget?.let { target ->
        val pinned = state.pins.any { it.sameAs(target) }
        com.serkantkn.zeta.ui.screens.messaging.MessagingSheet(
            title = target.title.ifBlank { stringResource(R.string.zune_quickplay) },
            onDismiss = { pinTarget = null }
        ) {
            com.serkantkn.zeta.ui.screens.messaging.SheetAction(
                label = stringResource(if (pinned) R.string.quickplay_unpin else R.string.quickplay_pin),
                color = LocalZuneColors.current.accentColor
            ) {
                viewModel.toggleQuickplayPin(target)
                pinTarget = null
            }
        }
    }
    }
}

/** Opens one thing quickplay holds - pinned, lately used or new - where it belongs. */
internal fun openQuickplayItem(
    item: QuickplayPin,
    context: android.content.Context,
    viewModel: HomeHubViewModel,
    onHubSelected: (HubType) -> Unit
) {
    when (item.kind) {
        QuickplayPin.Kind.ALBUM -> item.key.toLongOrNull()?.let {
            playAlbum(RecentAlbum(it, item.title, item.subtitle, item.art), onHubSelected)
        }
        QuickplayPin.Kind.PHOTO -> {
            PicturesBridge.openPhoto(android.net.Uri.parse(item.key))
            onHubSelected(HubType.PICTURES)
        }
        QuickplayPin.Kind.VIDEO -> com.serkantkn.zeta.ui.screens.video.VideoPlayer.open(
            context, android.net.Uri.parse(item.key), item.title
        )
        QuickplayPin.Kind.NOTE -> {
            NotesBridge.open(item.key)
            onHubSelected(HubType.NOTES)
        }
        QuickplayPin.Kind.APP -> viewModel.launchApp(item.key)
    }
}

/**
 * What the narrow strip beside the Zune list shows of quickplay: the pins first, then what was
 * lately played, taken, opened and written, then what is new - each once, at most [limit].
 */
internal fun quickplayStripItems(state: Quickplay, limit: Int): List<QuickplayPin> {
    val lately = buildList {
        state.recentAlbums.firstOrNull()?.let {
            add(QuickplayPin(QuickplayPin.Kind.ALBUM, it.id.toString(), it.title, it.artist, it.artUri))
        }
        state.recentPhotos.take(2).forEach {
            add(QuickplayPin(QuickplayPin.Kind.PHOTO, it.uri.toString(), it.displayName, art = it.uri.toString()))
        }
        state.recentApps.take(3).forEach { add(QuickplayPin(QuickplayPin.Kind.APP, it.packageName, it.label)) }
        state.recentNotes.firstOrNull()?.let {
            add(QuickplayPin(QuickplayPin.Kind.NOTE, it.id, it.title, it.colorHex.orEmpty()))
        }
        state.newAlbums.firstOrNull()?.let {
            add(QuickplayPin(QuickplayPin.Kind.ALBUM, it.id.toString(), it.title, it.artist, it.artUri))
        }
        state.newVideos.firstOrNull()?.let {
            add(QuickplayPin(QuickplayPin.Kind.VIDEO, it.uri.toString(), it.displayName, art = it.uri.toString()))
        }
    }
    val all = mutableListOf<QuickplayPin>()
    (state.pins + lately).forEach { item -> if (all.none { it.sameAs(item) }) all.add(item) }
    return all.take(limit)
}

/** One square of the strip: its picture, its note colour, or its app's icon on the accent. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuickplayStripSquare(
    item: QuickplayPin,
    face: TileIconFace?,
    cornerStyle: TileCornerStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val shape = RoundedCornerShape(if (cornerStyle == TileCornerStyle.ROUNDED) 6.dp else 0.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(
                if (item.kind == QuickplayPin.Kind.NOTE) noteColor(item.subtitle.ifBlank { null }, zuneColors.accentColor)
                else zuneColors.accentColor
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        when {
            item.art != null -> {
                // A record whose cover the store only claims to have shows its name under it.
                if (item.kind == QuickplayPin.Kind.ALBUM) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(3.dp)
                    )
                }
                AsyncImage(
                    model = item.art,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.kind == QuickplayPin.Kind.VIDEO) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(18.dp)
                    )
                }
            }
            face != null -> TileIconImage(
                face = face,
                size = 24.dp,
                ink = Color.White,
                contentDescription = item.title,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> Text(
                text = item.title.take(2),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun playAlbum(album: RecentAlbum, onHubSelected: (HubType) -> Unit) {
    MusicBridge.playAlbum(album.id)
    onHubSelected(HubType.MUSIC)
}

// ── Pieces ──────────────────────────────────────────────────────────────────

/** A section's name, small and in the accent, the way the Zune HD headed its quickplay groups. */
@Composable
private fun SectionWord(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
        color = LocalZuneColors.current.accentLink,
        modifier = modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun ActionWord(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
        color = LocalZuneColors.current.accentLink,
        modifier = modifier
            .combinedClickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

/** The accent underneath, the picture on top, the Windows Phone tilt on a press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Square(
    modifier: Modifier,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    background: Color = LocalZuneColors.current.accentColor,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .wpTilt(interactionSource)
            .clip(shape)
            .background(background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
private fun AlbumSquare(
    album: RecentAlbum,
    shape: RoundedCornerShape,
    size: androidx.compose.ui.unit.Dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Square(modifier = Modifier.size(size), shape = shape, onClick = onClick, onLongClick = onLongClick) {
        // The name underneath, the cover over it: a record whose cover the store only says it has
        // (most ripped ones) is never a blank square.
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light, fontSize = if (size < BigSquare) 12.sp else 15.sp),
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
        if (album.artUri != null) {
            AsyncImage(
                model = album.artUri,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** A note as the notes hub colours it: its title, its first words, and when it was last touched. */
@Composable
private fun NoteCard(
    note: Note,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val colour = noteColor(note.colorHex, zuneColors.accentColor)
    val interactionSource = remember { MutableInteractionSource() }
    val touched = remember(note.updatedAt) {
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .clip(shape)
            .background(colour)
            .combinedClickable(interactionSource = interactionSource, indication = null, onLongClick = onLongClick, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Text(
            text = note.title.ifBlank { stringResource(R.string.zune_last_note) },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val body = note.content.ifBlank { note.items.joinToString(" · ") { it.text } }
        if (body.isNotBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = touched,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** A pin: its picture, or its note colour or app icon, with its name underneath. */
@Composable
private fun PinSquare(
    pin: QuickplayPin,
    shape: RoundedCornerShape,
    face: TileIconFace?,
    modifier: Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = modifier) {
        Square(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = shape,
            onLongClick = onLongClick,
            onClick = onClick,
            background = if (pin.kind == QuickplayPin.Kind.NOTE) noteColor(pin.subtitle.ifBlank { null }, zuneColors.accentColor)
                else zuneColors.accentColor
        ) {
            when {
                pin.art != null -> {
                    if (pin.kind == QuickplayPin.Kind.ALBUM) {
                        Text(
                            text = pin.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        )
                    }
                    AsyncImage(
                        model = pin.art,
                        contentDescription = pin.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (pin.kind == QuickplayPin.Kind.VIDEO) PlayMark(Modifier.align(Alignment.Center))
                }
                face != null -> TileIconImage(
                    face = face,
                    size = 34.dp,
                    ink = Color.White,
                    contentDescription = pin.title,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> Text(
                    text = pin.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                )
            }
        }
        Text(
            text = pin.title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = zuneColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** The triangle a video wears over its first frame. */
@Composable
private fun PlayMark(modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(36.dp)
            .background(Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape)
            .padding(4.dp)
    )
}

private val BigSquare = 150.dp
private val SmallSquare = 72.dp
private val Gap = 8.dp
private val SectionGap = 28.dp
private const val ENTRANCE_MILLIS = 700
private const val EXIT_MILLIS = 500
