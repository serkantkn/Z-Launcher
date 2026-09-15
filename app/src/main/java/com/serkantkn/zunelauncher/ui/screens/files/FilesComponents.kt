package com.serkantkn.zunelauncher.ui.screens.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.CloudQuota
import com.serkantkn.zunelauncher.data.model.CloudTransfer
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.data.model.StorageVolumeInfo
import com.serkantkn.zunelauncher.ui.animation.ZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.animation.zuneZoomAnchor
import com.serkantkn.zunelauncher.ui.components.MetroProgressLine
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.FileCategory
import com.serkantkn.zunelauncher.util.category
import com.serkantkn.zunelauncher.util.categoryOf
import com.serkantkn.zunelauncher.util.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The pieces the Files hub is drawn from.
 *
 * The old rows were rounded grey cards with a circular badge on the left, which is Material's
 * shape language and not this launcher's. A row here is a line of type with a coloured bar down
 * its left, and the colour says what kind of thing it is — a folder in the accent, a picture, a
 * song, an archive each in their own — so the kind is readable without an icon at all.
 */

/** The size a row's name is set at, which is also where a flight to the title starts. */
internal val FileRowNameSize = 19.sp

// ── One file or folder ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileRow(
    item: FileItemModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    nameAnchor: ZuneZoomAnchor? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    subtitleOverride: String? = null
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val accent = categoryColor(item.category(), zuneColors.accentColor)

    val formattedDate = remember(item.lastModified) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
    }
    val folderLabel = stringResource(R.string.files_folder)
    val size = remember(item.size, item.isDirectory, folderLabel) {
        if (item.isDirectory) folderLabel else formatFileSize(item.size)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, if (isSelected) zuneColors.accentColor else zuneColors.textMuted)
                    .background(if (isSelected) zuneColors.accentColor else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = FileRowNameSize
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (nameAnchor != null) Modifier.zuneZoomAnchor(nameAnchor) else Modifier
            )
            Text(
                text = subtitleOverride ?: "$size • $formattedDate",
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (item.isDirectory && !selectionMode) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = zuneColors.textDim,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ── One file on a drive ─────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CloudRow(
    item: CloudItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    nameAnchor: ZuneZoomAnchor? = null
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val category = remember(item) {
        if (item.isFolder) FileCategory.FOLDER else categoryOf(item.name.substringAfterLast('.', ""))
    }
    val accent = categoryColor(category, zuneColors.accentColor)
    val formattedDate = remember(item.lastModified) {
        if (item.lastModified <= 0L) "" else
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(item.lastModified))
    }
    val folderLabel = stringResource(R.string.files_folder)
    val size = when {
        item.isFolder -> folderLabel
        item.size > 0L -> formatFileSize(item.size)
        else -> stringResource(R.string.files_cloud_document)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = FileRowNameSize
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (nameAnchor != null) Modifier.zuneZoomAnchor(nameAnchor) else Modifier
            )
            Text(
                text = listOf(size, formattedDate).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1
            )
        }
        if (item.isFolder) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = zuneColors.textDim,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ── Where you are ───────────────────────────────────────────────────────────

/** The path, each step of it pressable, in the Zune way: words, not chevrons in boxes. */
@Composable
internal fun FilesBreadcrumb(
    crumbs: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, (label, onClick) ->
            item(key = "crumb_${index}_$label") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index == crumbs.lastIndex) zuneColors.textMuted else zuneColors.accentColor,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = index != crumbs.lastIndex,
                                onClick = onClick
                            )
                            .padding(vertical = 4.dp)
                    )
                    if (index != crumbs.lastIndex) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.labelLarge,
                            color = zuneColors.textDim
                        )
                    }
                }
            }
        }
    }
}

// ── How full things are ─────────────────────────────────────────────────────

/** A volume and how much of it is gone, as a line rather than a ring. */
@Composable
internal fun StorageBar(
    volume: StorageVolumeInfo,
    onClick: (() -> Unit)? = null,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val used = remember(volume) { formatFileSize(volume.usedBytes) }
    val total = remember(volume) { formatFileSize(volume.totalBytes) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Under its own title the name would be the third copy of the same word on the page.
            if (showLabel) {
                Text(
                    text = volume.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = stringResource(R.string.files_storage_used, used, total),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = zuneColors.textMuted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        MetroProgressLine(progress = volume.fraction ?: 0f, thickness = 3.dp)
    }
}

/** The same line for a drive rather than a disc. */
@Composable
internal fun CloudQuotaBar(quota: CloudQuota, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val used = remember(quota) { formatFileSize(quota.usedBytes) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = if (quota.totalBytes > 0L) {
                stringResource(R.string.files_storage_used, used, formatFileSize(quota.totalBytes))
            } else {
                stringResource(R.string.files_cloud_used_only, used)
            },
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = zuneColors.textMuted
        )
        if (quota.fraction != null) {
            Spacer(modifier = Modifier.height(6.dp))
            MetroProgressLine(progress = quota.fraction!!, thickness = 3.dp)
        }
    }
}

/** What is being moved right now, and the word that stops it. */
@Composable
internal fun TransferBar(
    transfer: CloudTransfer,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(zuneColors.accentColor.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (transfer.isUpload) R.string.files_uploading else R.string.files_downloading,
                    transfer.name
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.common_cancel).lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentColor,
                modifier = Modifier.clickable(onClick = onCancel)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MetroProgressLine(progress = transfer.fraction ?: 0f, thickness = 3.dp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (transfer.totalBytes > 0L) {
                "${formatFileSize(transfer.transferredBytes)} / ${formatFileSize(transfer.totalBytes)}"
            } else {
                formatFileSize(transfer.transferredBytes)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = zuneColors.textMuted
        )
    }
}

// ── Colour by kind ──────────────────────────────────────────────────────────

/**
 * What colour a kind of file gets.
 *
 * Folders take the launcher's own accent, so the thing you are most often looking for is the thing
 * that matches everything else on the screen. The rest are fixed, because the point of them is to
 * be told apart from one another rather than to match the theme.
 */
internal fun categoryColor(category: FileCategory, accent: Color): Color = when (category) {
    FileCategory.FOLDER -> accent
    FileCategory.IMAGE -> Color(0xFF00A3A3)
    FileCategory.VIDEO -> Color(0xFF7B1FA2)
    FileCategory.AUDIO -> Color(0xFFF57C00)
    FileCategory.DOCUMENT -> Color(0xFF1565C0)
    FileCategory.ARCHIVE -> Color(0xFF8D6E63)
    FileCategory.APP -> Color(0xFF2E7D32)
    FileCategory.OTHER -> Color(0xFF9E9E9E)
}
