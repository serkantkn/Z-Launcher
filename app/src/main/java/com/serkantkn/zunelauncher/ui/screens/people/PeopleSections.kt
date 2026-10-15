package com.serkantkn.zunelauncher.ui.screens.people

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.ui.components.ZuneEmptyState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DeviceOwner
import java.util.Locale

/*
 * The sections of the people hub as the Windows Phone 7 people hub drew them: "all" with its
 * owner's card on top and the alphabet in accent tiles, "what's new" with what everybody has been
 * saying, and "recent" as a wall of faces.
 */

/**
 * The owner's card at the top of "all": their picture large, and beside it who they are. Windows
 * Phone wrote the owner's latest status there; with no feed of the owner's own, it is their name.
 */
@Composable
internal fun PeopleOwnerCard(owner: DeviceOwner, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    if (owner.name == null && owner.photo == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 18.dp)
    ) {
        Box(
            modifier = Modifier.size(OWNER_PHOTO).background(zuneColors.accentColor),
            contentAlignment = Alignment.Center
        ) {
            val photo = owner.photo
            if (photo != null) {
                Image(
                    bitmap = remember(photo) { photo.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = owner.name.orEmpty().take(1).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = owner.name ?: stringResource(R.string.people_me),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.people_me),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = zuneColors.textMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * A letter of the list as Windows Phone marked it: a small tile in the accent with the letter in
 * its bottom-left corner. A tap opens the whole alphabet to jump with.
 */
@Composable
internal fun PeopleLetterTile(letter: Char, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 6.dp)
            .size(LETTER_TILE)
            .background(zuneColors.accentColor)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = letter.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 30.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 2.dp)
        )
    }
}

/**
 * "what's new": who said what, the newest first. The name large, then where and when in small
 * type, then what they said; their picture in a small square at the right, where Windows Phone
 * kept the button to answer.
 */
@Composable
internal fun PeopleWhatsNewPage(
    news: List<PersonNews>,
    onOpen: (PersonNews) -> Unit,
    onOpenPerson: (ContactModel) -> Unit
) {
    if (news.isEmpty()) {
        ZuneEmptyState(stringResource(R.string.people_whats_new_empty))
        return
    }
    val zuneColors = LocalZuneColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(news, key = { it.message.id }) { item ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.contact.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.message.appName} · ${relativeTime(item.message.timestamp)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = zuneColors.accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.message.text.isNotBlank()) {
                        Text(
                            text = item.message.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                PersonSquare(
                    contact = item.contact,
                    size = NEWS_PHOTO,
                    modifier = Modifier.clickable { onOpenPerson(item.contact) }
                )
            }
        }
    }
}

/**
 * "recent": the people last in touch as a wall of square tiles, three across - their picture, or
 * with none, a tile in the accent with their name written in its corner.
 */
@Composable
internal fun PeopleRecentGrid(
    recent: List<ContactModel>,
    emptyText: String,
    columns: Int,
    onOpen: (ContactModel) -> Unit
) {
    if (recent.isEmpty()) {
        ZuneEmptyState(emptyText)
        return
    }
    val zuneColors = LocalZuneColors.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(RECENT_GAP),
        verticalArrangement = Arrangement.spacedBy(RECENT_GAP)
    ) {
        items(recent, key = { it.id }) { contact ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(zuneColors.accentColor)
                    .clickable { onOpen(contact) }
            ) {
                if (contact.photoUri != null) {
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, end = 6.dp, bottom = 5.dp)
                    )
                }
            }
        }
    }
}

/** Somebody's picture in a square, or the accent with their initial. */
@Composable
private fun PersonSquare(contact: ContactModel, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = modifier.size(size).background(zuneColors.accentColor),
        contentAlignment = Alignment.Center
    ) {
        if (contact.photoUri != null) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = contact.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = contact.name.take(1).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                color = Color.White
            )
        }
    }
}

/** "5 minutes ago", in the launcher's language, in lower case as everything small was. */
@Composable
private fun relativeTime(timestamp: Long): String {
    val locale = Locale.getDefault()
    return remember(timestamp) {
        DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            .toString()
            .lowercase(locale)
    }
}

private val OWNER_PHOTO = 96.dp
private val LETTER_TILE = 48.dp
private val NEWS_PHOTO = 48.dp
private val RECENT_GAP = 8.dp
