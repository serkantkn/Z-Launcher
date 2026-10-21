package com.serkantkn.zeta.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.FileItemModel
import com.serkantkn.zeta.data.model.StorageVolumeInfo
import com.serkantkn.zeta.data.repository.CloudDocument
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroRule
import com.serkantkn.zeta.ui.components.MetroSubScreen
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.util.FileCategory
import com.serkantkn.zeta.util.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Files hub's other pages: the kinds of thing on the phone, the places worth a shortcut, and
 * the cloud, reached through the phone's own document picker.
 */

// ── Kinds ───────────────────────────────────────────────────────────────────

/**
 * The standard folders, each in the colour its kind is drawn in everywhere else in the hub — so
 * the page is a legend for the coloured bars as well as a way into them.
 */
@Composable
internal fun CategoriesPage(
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        Triple("Download", stringResource(R.string.files_cat_downloads), FileCategory.DOCUMENT),
        Triple("Pictures", stringResource(R.string.files_cat_pictures), FileCategory.IMAGE),
        Triple("DCIM", stringResource(R.string.files_cat_camera), FileCategory.IMAGE),
        Triple("Movies", stringResource(R.string.files_cat_videos), FileCategory.VIDEO),
        Triple("Music", stringResource(R.string.files_cat_music), FileCategory.AUDIO),
        Triple("Documents", stringResource(R.string.files_cat_documents), FileCategory.DOCUMENT)
    )

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(entries.size) { index ->
            val (folder, label, category) = entries[index]
            ColourRow(
                label = label,
                subtitle = folder,
                category = category,
                onClick = { onOpen(folder) }
            )
        }
    }
}

// ── Places ──────────────────────────────────────────────────────────────────

/** The volumes, then the handful of folders worth reaching in one tap. */
@Composable
internal fun QuickAccessPage(
    volumes: List<StorageVolumeInfo>,
    onOpenVolume: (StorageVolumeInfo) -> Unit,
    onOpen: (File) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    recycleBinCount: Int = 0,
    onOpenRecycleBin: () -> Unit = {}
) {
    val shortcuts = listOf(
        android.os.Environment.DIRECTORY_DOWNLOADS to stringResource(R.string.files_cat_downloads),
        android.os.Environment.DIRECTORY_DCIM to stringResource(R.string.files_cat_camera),
        android.os.Environment.DIRECTORY_DOCUMENTS to stringResource(R.string.files_cat_documents)
    )

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { MetroRule(title = stringResource(R.string.files_volumes)) }
        items(volumes.size) { index ->
            val volume = volumes[index]
            StorageBar(volume = volume, onClick = { onOpenVolume(volume) })
        }
        item { MetroRule(title = stringResource(R.string.files_tab_quick)) }
        items(shortcuts.size) { index ->
            val (directory, label) = shortcuts[index]
            ColourRow(
                label = label,
                subtitle = directory,
                category = FileCategory.FOLDER,
                onClick = { onOpen(android.os.Environment.getExternalStoragePublicDirectory(directory)) }
            )
        }
        item { RecycleBinQuickRow(count = recycleBinCount, onClick = onOpenRecycleBin) }
    }
}

@Composable
private fun ColourRow(
    label: String,
    subtitle: String,
    category: FileCategory,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(categoryColor(category, zuneColors.accentColor))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = FileRowNameSize
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = zuneColors.textDim
        )
    }
}

// ── The cloud ───────────────────────────────────────────────────────────────

/**
 * The cloud, as the phone's own picker reaches it: a line on how, the one large action that opens
 * the picker, which cloud apps the phone has, and the documents opened that way before.
 *
 * There is nothing to sign in to here. The picker lists Drive, OneDrive, Dropbox or whatever else
 * is installed, and each of those apps has already signed itself in. The tablet sets the same
 * page in its place pane, so both layouts carry the same things.
 */
@Composable
internal fun CloudPage(
    documents: List<CloudDocument>,
    cloudApps: List<String>,
    message: String?,
    contentPadding: PaddingValues,
    onPick: () -> Unit,
    onOpen: (CloudDocument) -> Unit,
    onLongClick: (CloudDocument) -> Unit,
    modifier: Modifier = Modifier,
    selected: CloudDocument? = null
) {
    val zuneColors = LocalZuneColors.current

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.files_cloud_picker_intro),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item(key = "pick") {
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = stringResource(R.string.files_cloud_open_from),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .wpTilt(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onPick)
            )
        }
        item(key = "apps") {
            Text(
                text = if (cloudApps.isEmpty()) {
                    stringResource(R.string.files_cloud_no_apps)
                } else {
                    stringResource(R.string.files_cloud_apps, cloudApps.joinToString(", "))
                },
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (message != null) {
            item(key = "message") {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.accentColor,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
        item(key = "recent") { MetroRule(title = stringResource(R.string.files_cloud_recent)) }
        if (documents.isEmpty()) {
            item(key = "empty") { MetroEmpty(message = stringResource(R.string.files_cloud_recent_empty)) }
        }
        documents.forEach { document ->
            item(key = "doc_${document.uri}") {
                CloudDocumentRow(
                    document = document,
                    isSelected = selected?.uri == document.uri,
                    onClick = { onOpen(document) },
                    onLongClick = { onLongClick(document) }
                )
            }
        }
    }
}

// ── What a file is ──────────────────────────────────────────────────────────

/** Everything the phone knows about one file, including how big a folder really is. */
/**
 * What the details say about a file, as label and value: where it is, how big (a folder's is
 * measured, and says so meanwhile), what is in a folder, when it changed, its kind, whether it
 * can be written. Shared by the phone's details page and the tablet's side panel.
 */
@Composable
internal fun fileFacts(item: FileItemModel, measured: Pair<Long, Int>?): List<Pair<String, String>> {
    val measuring = stringResource(R.string.files_measuring)
    return buildList {
        add(stringResource(R.string.files_details_path) to item.file.absolutePath)
        add(
            stringResource(R.string.files_details_size) to
                (measured?.let { (bytes, _) -> formatFileSize(bytes) } ?: measuring)
        )
        if (item.isDirectory) {
            add(
                stringResource(R.string.files_details_contains) to
                    (measured?.let { (_, count) -> count.toString() } ?: measuring)
            )
        }
        add(
            stringResource(R.string.files_details_modified) to
                SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
        )
        if (!item.isDirectory && item.extension.isNotBlank()) {
            add(stringResource(R.string.files_details_kind) to item.extension.uppercase(Locale.ROOT))
        }
        add(
            stringResource(R.string.files_details_readable) to stringResource(
                if (item.file.canWrite()) R.string.files_details_writable else R.string.files_details_read_only
            )
        )
    }
}

@Composable
internal fun FileDetailsScreen(
    item: FileItemModel,
    measure: suspend () -> Pair<Long, Int>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    var measured by remember(item.file.absolutePath) { mutableStateOf<Pair<Long, Int>?>(null) }
    LaunchedEffect(item.file.absolutePath) { measured = measure() }

    val rows = fileFacts(item, measured)

    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_files),
        title = stringResource(R.string.files_details),
        onClose = onClose,
        modifier = modifier
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "preview") {
                // What the file holds, as the tablet's panel shows it.
                FilePreview(
                    item = item,
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 16.dp)
                )
            }
            item(key = "name") {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            rows.forEach { (label, value) ->
                item(key = "detail_$label") {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        DetailLine(label = label, value = value)
                    }
                }
            }
            item(key = "tail") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val zuneColors = LocalZuneColors.current
    Column {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    count: Int,
    row: @Composable (Int) -> Unit
) {
    repeat(count) { index -> item(key = "row_$index") { row(index) } }
}
