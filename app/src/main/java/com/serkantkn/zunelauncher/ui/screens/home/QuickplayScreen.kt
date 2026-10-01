package com.serkantkn.zunelauncher.ui.screens.home

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
import androidx.compose.runtime.remember
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
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.model.RecentAlbum
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.repository.MusicBridge
import com.serkantkn.zunelauncher.data.repository.NotesBridge
import com.serkantkn.zunelauncher.data.repository.PicturesBridge
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.components.TileIconImage
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.screens.notes.noteColor
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.TileIconFace
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
    onHubSelected: (HubType) -> Unit,
    modifier: Modifier = Modifier,
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

    // The page comes in the way the others do: everything staggers up as it is swiped to.
    val entrance = remember { Animatable(1f) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(ENTRANCE_MILLIS, easing = LinearEasing))
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        Spacer(modifier = Modifier.height(topInset + 36.dp))
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
                AlbumSquare(first, shape, size = BigSquare) { playAlbum(first, onHubSelected) }
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
                        playAlbum(first, onHubSelected)
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
                    rest.forEach { album -> AlbumSquare(album, shape, size = SmallSquare) { playAlbum(album, onHubSelected) } }
                }
            }
            Spacer(modifier = Modifier.height(SectionGap))
        }

        // ── Pictures ──
        if (state.recentPhotos.isNotEmpty()) {
            SectionWord(stringResource(R.string.quickplay_photos), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            state.recentPhotos.chunked(3).forEach { row ->
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
                            onClick = {
                                PicturesBridge.openPhoto(photo.uri)
                                onHubSelected(HubType.PICTURES)
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
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
                        .w10mStaggeredAnimation({ entrance.value }, slot++)
                ) {
                    NotesBridge.open(note.id)
                    onHubSelected(HubType.NOTES)
                }
            }
            Spacer(modifier = Modifier.height(SectionGap - Gap))
        }

        // ── Apps ──
        if (state.recentApps.isNotEmpty()) {
            SectionWord(stringResource(R.string.zune_recent_apps), Modifier.w10mStaggeredAnimation({ entrance.value }, slot++))
            state.recentApps.chunked(4).forEach { row ->
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
                                onClick = { viewModel.launchApp(app.packageName) }
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
                    repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
        color = LocalZuneColors.current.accentColor,
        modifier = modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun ActionWord(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
        color = LocalZuneColors.current.accentColor,
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
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .wpTilt(interactionSource)
            .clip(shape)
            .background(LocalZuneColors.current.accentColor)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        content = content
    )
}

@Composable
private fun AlbumSquare(album: RecentAlbum, shape: RoundedCornerShape, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Square(modifier = Modifier.size(size), shape = shape, onClick = onClick) {
        if (album.artUri != null) {
            AsyncImage(
                model = album.artUri,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
    }
}

/** A note as the notes hub colours it: its title, its first words, and when it was last touched. */
@Composable
private fun NoteCard(note: Note, shape: RoundedCornerShape, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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

private val BigSquare = 150.dp
private val SmallSquare = 72.dp
private val Gap = 8.dp
private val SectionGap = 28.dp
private const val ENTRANCE_MILLIS = 700
