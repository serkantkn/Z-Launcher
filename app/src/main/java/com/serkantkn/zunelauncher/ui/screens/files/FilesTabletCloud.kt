package com.serkantkn.zunelauncher.ui.screens.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.FileCategory
import com.serkantkn.zunelauncher.util.FilesView
import com.serkantkn.zunelauncher.util.categoryOf
import com.serkantkn.zunelauncher.util.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A drive on the tablet, laid out like the device's own folders: the header above it (the
 * folder's name, the back arrow, the search box) is the same, and so are the two views — the
 * details in columns, or the pictures in columns running off to the right. A hold chooses a
 * file and the bar along the bottom says what can be done with it, as for the device's files.
 *
 * It used to be the phone's drive page set into the tablet's frame, which wrote the folder's
 * name a second time under the header and listed the files in the phone's rows.
 */
@Composable
internal fun TabletCloudPage(
    viewModel: FilesHubViewModel,
    filesView: FilesView,
    selected: CloudItem?,
    bottomPadding: Dp,
    onOpen: (CloudItem) -> Unit,
    onLongClick: (CloudItem) -> Unit,
    onOpenAccount: (CloudAccount) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val accounts by viewModel.cloudAccounts.collectAsState()
    val active by viewModel.activeCloudAccount.collectAsState()
    val items by viewModel.cloudItems.collectAsState()
    val searchResults by viewModel.cloudSearchResults.collectAsState()
    val quota by viewModel.cloudQuota.collectAsState()
    val busy by viewModel.cloudBusy.collectAsState()
    val message by viewModel.cloudMessage.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(start = 60.dp)) {
        // ── What the drive says: how full it is, that it is loading, what went wrong ──
        quota?.takeIf { active != null }?.let { CloudQuotaBar(quota = it, modifier = Modifier.width(QUOTA_WIDTH)) }
        if (busy) {
            Text(
                text = stringResource(R.string.files_cloud_loading),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentColor,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (active == null) {
            // The accounts are on the left already; this is only for the moment before one is open.
            if (accounts.isEmpty()) {
                MetroEmpty(message = stringResource(R.string.files_cloud_none))
            } else {
                accounts.forEach { account ->
                    val source = remember { MutableInteractionSource() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .wpTilt(interactionSource = source)
                            .clickable(indication = null, interactionSource = source) { onOpenAccount(account) }
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = zuneColors.accentColor, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(account.displayName.ifBlank { account.email }, fontSize = 26.sp, fontWeight = FontWeight.Light, color = ink)
                            Text(account.email, style = MaterialTheme.typography.labelMedium, color = zuneColors.textMuted)
                        }
                    }
                }
            }
            return@Column
        }

        val shown = searchResults ?: items
        if (shown.isEmpty()) {
            if (!busy) MetroEmpty(message = stringResource(R.string.files_empty_folder))
            return@Column
        }
        if (filesView == FilesView.THUMBNAILS) {
            CloudThumbnails(shown, selected, bottomPadding, onOpen, onLongClick)
        } else {
            CloudDetails(shown, selected, bottomPadding, ink, onOpen, onLongClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudDetails(
    items: List<CloudItem>,
    selected: CloudItem?,
    bottomPadding: Dp,
    ink: Color,
    onOpen: (CloudItem) -> Unit,
    onLongClick: (CloudItem) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()) }
    val folder = stringResource(R.string.files_cloud_kind_folder)
    val google = stringResource(R.string.files_cloud_kind_google)
    val plain = stringResource(R.string.files_type_plain)
    val ofType = stringResource(R.string.files_type_file, "%s")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Spacer(modifier = Modifier.width(GLYPH + 14.dp))
        Heading(stringResource(R.string.files_col_name), Modifier.weight(1f))
        Heading(stringResource(R.string.files_col_modified), Modifier.width(DATE_W))
        Heading(stringResource(R.string.files_col_type), Modifier.width(TYPE_W))
        Heading(stringResource(R.string.files_col_size), Modifier.width(SIZE_W), alignEnd = true)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.1f)))
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        items(items, key = { it.id }) { item ->
            val chosen = selected?.id == item.id
            val kind = when {
                item.isFolder -> folder
                item.isGoogleDocument || item.isWebOnly -> google
                else -> extensionOf(item).takeIf { it.isNotBlank() }?.let { ofType.replace("%s", it.uppercase(Locale.ROOT)) } ?: plain
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(if (chosen) zuneColors.accentColor.copy(alpha = 0.18f) else Color.Transparent)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpen(item) },
                        onLongClick = { onLongClick(item) }
                    )
            ) {
                CloudGlyph(item, Modifier.size(GLYPH), 18.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                Cell(if (item.lastModified > 0) dateFormat.format(Date(item.lastModified)) else "", Modifier.width(DATE_W))
                Cell(kind, Modifier.width(TYPE_W))
                Cell(if (item.isFolder || item.size <= 0) "" else formatFileSize(item.size), Modifier.width(SIZE_W), alignEnd = true)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudThumbnails(
    items: List<CloudItem>,
    selected: CloudItem?,
    bottomPadding: Dp,
    onOpen: (CloudItem) -> Unit,
    onLongClick: (CloudItem) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        val rows = ((maxHeight + GAP) / (TILE + CAPTION + GAP)).toInt().coerceAtLeast(1)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier.fillMaxWidth().height((TILE + CAPTION) * rows + GAP * (rows - 1) + 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(GAP),
            contentPadding = PaddingValues(top = 6.dp, end = 40.dp)
        ) {
            gridItems(items, key = { it.id }) { item ->
                val source = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .width(TILE)
                        .wpTilt(interactionSource = source)
                        .combinedClickable(interactionSource = source, indication = null, onClick = { onOpen(item) }, onLongClick = { onLongClick(item) })
                ) {
                    Box(modifier = Modifier.size(TILE)) {
                        CloudGlyph(item, Modifier.fillMaxSize(), 44.dp)
                        // Drive's own small picture, where it gives one; the glyph shows until then.
                        item.thumbnailUrl?.takeIf { !item.isFolder }?.let { url ->
                            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        // The same mark as a chosen file on the device: a frame and a corner check.
                        if (selected?.id == item.id) {
                            val ink = if (zuneColors.isDark) Color.White else Color.Black
                            if (item.isFolder) SelectionMark(ink, tick = if (zuneColors.isDark) Color.Black else Color.White)
                            else SelectionMark(zuneColors.accentColor)
                        }
                    }
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/** The kind's glyph on the kind's colour; a Google file wears the web's globe. */
@Composable
private fun CloudGlyph(item: CloudItem, modifier: Modifier, glyphSize: Dp) {
    val zuneColors = LocalZuneColors.current
    val category = categoryOf(extensionOf(item), item.isFolder)
    val google = item.isGoogleDocument || item.isWebOnly
    val icon: ImageVector = if (google) Icons.Default.Language else glyphOf(category)
    val colour = if (google) Color(0xFF1565C0) else categoryColor(category, zuneColors.accentColor)
    Box(contentAlignment = Alignment.Center, modifier = modifier.background(colour)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(glyphSize))
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier, alignEnd: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = LocalZuneColors.current.textMuted,
        maxLines = 1,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun Cell(text: String, modifier: Modifier, alignEnd: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier.padding(end = 8.dp)
    )
}

private fun extensionOf(item: CloudItem): String = item.name.substringAfterLast('.', "").takeIf { '.' in item.name }.orEmpty()

private val QUOTA_WIDTH = 420.dp
private val GLYPH = 30.dp
private val DATE_W = 170.dp
private val TYPE_W = 150.dp
private val SIZE_W = 90.dp
private val TILE = 150.dp
private val CAPTION = 48.dp
private val GAP = 14.dp
