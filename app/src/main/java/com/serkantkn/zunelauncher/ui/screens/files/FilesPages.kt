package com.serkantkn.zunelauncher.ui.screens.files

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudCrumb
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.CloudQuota
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.data.model.StorageVolumeInfo
import com.serkantkn.zunelauncher.ui.animation.ZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.animation.rememberZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.animation.zuneZoomAnchor
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.MetroRule
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.FileCategory
import com.serkantkn.zunelauncher.util.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Files hub's other pages: the kinds of thing on the phone, the places worth a shortcut, and
 * whatever drives have been signed in to.
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
    modifier: Modifier = Modifier
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

// ── Drives ──────────────────────────────────────────────────────────────────

/**
 * A drive, or the list of drives when none is open.
 *
 * Its own heading is the folder you are in, for the same reason the local page's is: it is where
 * the name of the folder you touched lands.
 */
@Composable
internal fun CloudPage(
    accounts: List<CloudAccount>,
    activeAccount: CloudAccount?,
    path: List<CloudCrumb>,
    items: List<CloudItem>,
    isSearchResult: Boolean,
    quota: CloudQuota?,
    isBusy: Boolean,
    message: String?,
    titleAnchor: ZuneZoomAnchor,
    titleAlpha: Float,
    contentPadding: PaddingValues,
    onSignInGoogle: () -> Unit,
    onOpenAccount: (CloudAccount) -> Unit,
    onSignOut: (CloudAccount) -> Unit,
    onOpenItem: (CloudItem, Rect?) -> Unit,
    onItemLongClick: (CloudItem) -> Unit,
    onCrumbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (activeAccount == null) {
            item {
                Text(
                    text = stringResource(R.string.files_cloud_intro),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item { MetroRule(title = stringResource(R.string.files_cloud_accounts)) }
            if (accounts.isEmpty()) {
                item { MetroEmpty(message = stringResource(R.string.files_cloud_no_accounts)) }
            }
            items(accounts.size) { index ->
                val account = accounts[index]
                CloudAccountRow(
                    account = account,
                    onOpen = { onOpenAccount(account) },
                    onSignOut = { onSignOut(account) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.files_cloud_google_sign_in),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSignInGoogle
                        )
                )
            }
            if (message != null) {
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            return@LazyColumn
        }

        // Inside a drive.
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isSearchResult) {
                        stringResource(R.string.files_search_results)
                    } else {
                        path.lastOrNull()?.title.orEmpty()
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = CLOUD_TITLE_SIZE
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .zuneZoomAnchor(titleAnchor)
                        .graphicsLayer { alpha = titleAlpha }
                )
                if (path.size > 1 || isSearchResult) {
                    Text(
                        text = stringResource(R.string.files_parent_dir),
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentColor,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCrumbClick
                            )
                    )
                }
                quota?.let { CloudQuotaBar(quota = it) }
                if (isBusy) {
                    Text(
                        text = stringResource(R.string.files_cloud_loading),
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentColor,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        if (items.isEmpty() && !isBusy) {
            item { MetroEmpty(message = stringResource(R.string.files_empty_folder)) }
        }

        items.forEach { item ->
            item(key = "cloud_${item.id}") {
                val anchor = rememberZuneZoomAnchor()
                CloudRow(
                    item = item,
                    nameAnchor = anchor,
                    onClick = { onOpenItem(item, anchor.bounds) },
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
    }
}

@Composable
private fun CloudAccountRow(
    account: CloudAccount,
    onOpen: () -> Unit,
    onSignOut: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(zuneColors.accentColor)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = FileRowNameSize
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = account.email,
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1
            )
        }
        Text(
            text = stringResource(R.string.files_cloud_sign_out).lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textMuted,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSignOut
                )
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        )
    }
}

// ── What a file is ──────────────────────────────────────────────────────────

/** Everything the phone knows about one file, including how big a folder really is. */
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

    val measuring = stringResource(R.string.files_measuring)
    val rows: List<Pair<String, String>> = buildList {
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
            add(stringResource(R.string.files_details_kind) to item.extension.uppercase(Locale.getDefault()))
        }
        add(
            stringResource(R.string.files_details_readable) to stringResource(
                if (item.file.canWrite()) R.string.files_details_writable else R.string.files_details_read_only
            )
        )
    }

    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_files),
        title = stringResource(R.string.files_details),
        onClose = onClose,
        modifier = modifier
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
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
