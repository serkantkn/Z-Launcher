package com.serkantkn.zeta.ui.tv

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.repository.MusicBridge
import java.util.Locale

/**
 * The dashboard's search, kept behind its left edge: the TV's apps and the launcher's hubs by name,
 * and the TV's own songs, albums and videos. What cannot be read - no permission yet - is left out.
 */
internal object XboxSearch {
    fun find(context: Context, query: String, items: Map<String, XboxItem>, onOpenHub: (HubType) -> Unit): List<XboxItem> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        val found = items.values
            .filter { it.kind != XboxItem.Kind.FILM && fold(it.label).contains(q) }
            .sortedBy { if (fold(it.label).startsWith(q)) 0 else 1 }
            .toMutableList()
        found += runCatching { albums(context, q, onOpenHub) }.getOrDefault(emptyList())
        found += runCatching { songs(context, q, onOpenHub) }.getOrDefault(emptyList())
        found += runCatching { videos(context, q) }.getOrDefault(emptyList())
        return found.take(MAX_RESULTS)
    }

    /**
     * Folded for matching: lower case, Turkish letters to their plain ones and any other marks
     * dropped, so "parca" finds "Parça" - a TV's keyboard is often typed without them.
     */
    internal fun fold(text: String): String {
        val lower = text.trim().lowercase(Locale.ROOT)
            .replace('ı', 'i').replace('ç', 'c').replace('ğ', 'g').replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD).replace(MARKS, "")
    }

    // The media store's LIKE knows nothing of these letters, so every row is read (a TV's library
    // is small, and only three columns) and matched here.
    private fun albums(context: Context, q: String, onOpenHub: (HubType) -> Unit): List<XboxItem> =
        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST),
            null, null,
            "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < PER_KIND) {
                    val title = c.getString(1).orEmpty()
                    val artist = c.getString(2)
                    if (!fold(title).contains(q) && !fold(artist.orEmpty()).contains(q)) continue
                    val id = c.getLong(0)
                    add(XboxItem("album:$id", title, artist, Icons.Default.Album, null, false, XboxItem.Kind.MEDIA) {
                        MusicBridge.playAlbum(id)
                        onOpenHub(HubType.MUSIC)
                    })
                }
            }
        }.orEmpty()

    private fun songs(context: Context, q: String, onOpenHub: (HubType) -> Unit): List<XboxItem> =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < PER_KIND) {
                    val title = c.getString(1).orEmpty()
                    val artist = c.getString(2)
                    if (!fold(title).contains(q) && !fold(artist.orEmpty()).contains(q)) continue
                    val id = c.getLong(0)
                    add(XboxItem("song:$id", title, artist, Icons.Default.MusicNote, null, false, XboxItem.Kind.MEDIA) {
                        MusicBridge.playTrack(MusicBridge.Track.FromLibrary(id))
                        onOpenHub(HubType.MUSIC)
                    })
                }
            }
        }.orEmpty()

    private fun videos(context: Context, q: String): List<XboxItem> =
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME),
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < PER_KIND) {
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: c.getString(2).orEmpty()
                    if (!fold(title).contains(q)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                    add(XboxItem("video:${c.getLong(0)}", title, null, Icons.Default.Movie, null, false, XboxItem.Kind.MEDIA) {
                        com.serkantkn.zeta.ui.screens.video.VideoPlayer.open(context, uri, title)
                    })
                }
            }
        }.orEmpty()

    private val MARKS = Regex("\\p{Mn}+")
    private const val MAX_RESULTS = 40
    private const val PER_KIND = 12
}

/**
 * The search page: the box at the top - typed into with the TV's keyboard - and what is found as a
 * row of cards under it, which of how many in the corner. Down goes from the box to the cards,
 * up from the cards to the box.
 */
@Composable
internal fun XboxSearchPage(
    text: String,
    onText: (String) -> Unit,
    onDone: () -> Unit,
    results: List<XboxItem>,
    at: Int,
    onBox: Boolean,
    boxFocus: FocusRequester,
    u: Dp
) {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Normal)
    fun px(v: Float) = with(density) { (u * v).toSp() }
    val shift by animateFloatAsState((at - 1).coerceAtLeast(0).toFloat(), tween(260, easing = FastOutSlowInEasing), label = "xboxSearchShift")
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.xbox_search),
            style = style.copy(fontSize = px(32f), shadow = xboxShadow),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 78f, end = u * 95f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .offset(x = u * 104f, y = u * 80f)
                .width(u * 640f)
                .background(Xbox.Panel)
                .border(if (onBox) 3.dp else 0.dp, if (onBox) Xbox.GreenLit else Color.Transparent)
                .padding(horizontal = u * 14f, vertical = u * 10f)
        ) {
            Icon(Icons.Default.Search, null, tint = Xbox.InkDim, modifier = Modifier.size(u * 26f))
            Spacer(modifier = Modifier.width(u * 10f))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.xbox_search_hint), style = style.copy(fontSize = px(21f)), color = Xbox.InkDim)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = style.copy(fontSize = px(21f), color = Xbox.Ink),
                    cursorBrush = SolidColor(Xbox.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(boxFocus)
                )
            }
        }
        if (text.isNotBlank()) {
            Text(
                if (results.isEmpty()) stringResource(R.string.xbox_search_none) else stringResource(R.string.xbox_n_of_m, at.coerceAtMost(results.lastIndex) + 1, results.size),
                style = style.copy(fontSize = px(19f), shadow = xboxShadow),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 122f, end = u * 95f)
            )
        }
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            results.forEachIndexed { i, item ->
                val x = 64f + (i - shift) * 228f
                if (x < -222f || x > 1300f) return@forEachIndexed
                val chosen = i == at && !onBox
                val lift by animateFloatAsState(if (chosen) 1.07f else 1f, tween(140), label = "xboxSearchCard")
                Column(
                    modifier = Modifier
                        .offset(x = u * x, y = u * 190f)
                        .size(u * 222f, u * 330f)
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
                        modifier = Modifier.fillMaxWidth().height(u * 200f).background(if (item.image == null) Xbox.Green else Color(0xFFA9AEB0))
                    ) {
                        val image = item.image
                        if (image != null) {
                            androidx.compose.foundation.Image(
                                image, null,
                                contentScale = if (item.banner) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Inside,
                                modifier = if (item.banner) Modifier.fillMaxWidth() else Modifier.size(u * 96f)
                            )
                        } else if (item.icon != null) {
                            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(u * 64f))
                        }
                    }
                    Spacer(modifier = Modifier.height(u * 8f))
                    Text(item.label, style = style.copy(fontSize = px(19f)), color = Xbox.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.line?.let { Text(it, style = style.copy(fontSize = px(15f)), color = Xbox.InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        XboxLegend(modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f))
    }
}
