package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.BrowserDownload
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Windows Phone Metro style Download Manager screen for Internet Hub.
 */
@Composable
fun BrowserDownloadsScreen(
    downloads: List<BrowserDownload>,
    onClose: () -> Unit,
    onOpenDownload: (BrowserDownload) -> Unit,
    onRemoveDownload: (Long) -> Unit,
    onClearAll: () -> Unit,
    onRefreshStatus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current

    BackHandler { onClose() }

    androidx.compose.runtime.LaunchedEffect(downloads) {
        val hasActive = downloads.any {
            it.status == DownloadManager.STATUS_RUNNING ||
            it.status == DownloadManager.STATUS_PENDING ||
            it.status == DownloadManager.STATUS_PAUSED
        }
        if (hasActive) {
            while (true) {
                kotlinx.coroutines.delay(500L)
                onRefreshStatus()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── Header (WP Metro style) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 32.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                Text(
                    text = stringResource(R.string.hub_internet),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f)
                    else Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = stringResource(R.string.browser_downloads),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 46.sp,
                        letterSpacing = (-1).sp,
                        lineHeight = 50.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // ─── Downloads List ───
            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = stringResource(R.string.browser_downloads_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.id }) { download ->
                        DownloadItemRow(
                            download = download,
                            onClick = { onOpenDownload(download) },
                            onRemove = { onRemoveDownload(download.id) }
                        )
                    }
                }
            }

            // ─── Bottom Action Bar ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (zuneColors.isDark) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
                    )
                    .padding(
                        horizontal = ZuneDimens.ScreenPaddingHorizontal,
                        vertical = 12.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (downloads.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .border(
                                    2.dp,
                                    zuneColors.textMuted.copy(alpha = 0.5f),
                                    RoundedCornerShape(0.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onClearAll
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.common_clear_all),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(2.dp, zuneColors.accentColor, RoundedCornerShape(0.dp))
                            .background(zuneColors.accentColor, RoundedCornerShape(0.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClose
                            )
                    ) {
                        Text(
                            text = stringResource(R.string.common_close),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItemRow(
    download: BrowserDownload,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val itemBg = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFF0F0F0)

    val icon = remember(download.mimeType, download.fileName) {
        val mime = download.mimeType?.lowercase() ?: ""
        val name = download.fileName.lowercase()
        when {
            mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif") -> Icons.Default.Image
            mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") -> Icons.Default.Movie
            mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".wav") -> Icons.Default.MusicNote
            mime.contains("zip") || mime.contains("rar") || name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> Icons.Default.FolderZip
            else -> Icons.Default.InsertDriveFile
        }
    }

    val isRunning = download.status == DownloadManager.STATUS_RUNNING || download.status == DownloadManager.STATUS_PENDING
    val isSuccess = download.status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed = download.status == DownloadManager.STATUS_FAILED

    val formattedDate = remember(download.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        sdf.format(Date(download.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBg, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isSuccess) zuneColors.accentColor
                        else if (zuneColors.isDark) Color(0xFF333333) else Color(0xFFDDDDDD)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = download.fileName,
                    tint = if (isSuccess) Color.White else zuneColors.accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.fileName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val statusText = when {
                    isRunning -> {
                        if (download.totalBytes > 0) {
                            val percent = ((download.downloadedBytes.toFloat() / download.totalBytes) * 100).toInt()
                            "%$percent • ${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}"
                        } else {
                            stringResource(R.string.browser_downloading_lower)
                        }
                    }
                    isSuccess -> stringResource(R.string.browser_download_done, formatBytes(download.totalBytes.coerceAtLeast(download.downloadedBytes)), formattedDate)
                    isFailed -> stringResource(R.string.browser_download_failed_status, formattedDate)
                    else -> formattedDate
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (isFailed) MaterialTheme.colorScheme.error else zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Remove Button
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.files_delete_cap),
                    tint = zuneColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Active Download Progress Bar
        if (isRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            val progressFloat = if (download.totalBytes > 0) {
                (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progressFloat },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = zuneColors.accentColor,
                trackColor = zuneColors.textMuted.copy(alpha = 0.2f)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val sizes = arrayOf("B", "KB", "MB", "GB")
    val i = (Math.log(bytes.toDouble()) / Math.log(k)).toInt().coerceIn(0, 3)
    val value = bytes / Math.pow(k, i.toDouble())
    return String.format(Locale.ENGLISH, "%.1f %s", value, sizes[i])
}
