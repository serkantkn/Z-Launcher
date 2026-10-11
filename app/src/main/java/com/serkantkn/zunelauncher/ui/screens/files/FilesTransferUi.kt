package com.serkantkn.zunelauncher.ui.screens.files

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.components.MetroProgressLine
import com.serkantkn.zunelauncher.ui.components.Win8MessageDialog
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.ConflictChoice
import com.serkantkn.zunelauncher.util.FileConflict
import com.serkantkn.zunelauncher.util.FileTransferProgress
import com.serkantkn.zunelauncher.util.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A copy or a move as the Files hub shows it while it goes, and the question it asks when a name
 * is already taken — once in the phone's Windows Phone dress and once in the tablet's Windows 8
 * one. The two say the same things and offer the same choices; only the look differs.
 */

// ── How far it has gone ─────────────────────────────────────────────────────

/** "3 items copying · 43% · 12 MB/s", in the reader's own order for the percent sign. */
@Composable
private fun pasteSummary(progress: FileTransferProgress): String {
    val count = progress.totalItems
    val parts = buildList {
        add(
            pluralStringResource(
                if (progress.isMove) R.plurals.files_transfer_moving else R.plurals.files_transfer_copying,
                count,
                count
            )
        )
        add(stringResource(R.string.files_transfer_percent, progress.percent))
        when {
            progress.isPaused -> add(stringResource(R.string.files_transfer_paused))
            // Nothing is said about speed until there is a speed to say; "0 B/s" reads as stuck.
            progress.bytesPerSecond > 0L ->
                add(stringResource(R.string.files_transfer_speed, formatFileSize(progress.bytesPerSecond)))
        }
    }
    return parts.joinToString(" · ")
}

/** The line eases between the snapshots rather than stepping ten times a second. */
@Composable
private fun animatedFraction(progress: FileTransferProgress): Float {
    val fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 200),
        label = "pasteFraction"
    )
    return fraction
}

/**
 * The phone's block under the header: what is going on in the accent, the file now being copied
 * under it, a thin line for how far, and the words that pause and stop it.
 */
@Composable
internal fun PhonePasteProgress(
    progress: FileTransferProgress,
    onPauseToggle: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pasteSummary(progress),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = zuneColors.accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TransferLink(
                text = stringResource(if (progress.isPaused) R.string.files_transfer_resume else R.string.files_transfer_pause),
                color = zuneColors.textMuted,
                onClick = onPauseToggle
            )
            // What was finished before stays; only the file half-way through goes.
            TransferLink(
                text = stringResource(R.string.common_cancel).lowercase(Locale.getDefault()),
                color = zuneColors.textMuted,
                onClick = onCancel
            )
        }
        if (progress.currentName.isNotEmpty()) {
            Text(
                text = progress.currentName,
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        MetroProgressLine(progress = animatedFraction(progress), thickness = 2.dp)
    }
}

/**
 * Windows 8's progress in place: one line under the header saying what is happening and to what,
 * a thin determinate line beneath it, and the links beside the words.
 */
@Composable
internal fun TabletPasteProgress(
    progress: FileTransferProgress,
    onPauseToggle: () -> Unit,
    onCancel: () -> Unit,
    ink: Color,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val summary = pasteSummary(progress)
    Column(modifier = modifier.widthIn(max = 640.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (progress.currentName.isEmpty()) summary else "$summary — ${progress.currentName}",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            TransferLink(
                text = stringResource(if (progress.isPaused) R.string.files_transfer_resume else R.string.files_transfer_pause),
                color = zuneColors.accentLink,
                onClick = onPauseToggle
            )
            TransferLink(
                text = stringResource(R.string.common_cancel).lowercase(Locale.getDefault()),
                color = zuneColors.accentLink,
                onClick = onCancel
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        MetroProgressLine(progress = animatedFraction(progress), thickness = 2.dp)
    }
}

@Composable
private fun TransferLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

// ── A name already taken ────────────────────────────────────────────────────

/**
 * Asks what to do about a name that is already used where the paste is going, while the paste
 * waits. Closing the question any other way — back, a tap outside — skips just this one thing,
 * the answer that changes nothing on the disk.
 */
@Composable
internal fun PasteConflictDialog(viewModel: FilesHubViewModel, isWideScreen: Boolean) {
    val conflict by viewModel.pasteConflict.collectAsState()
    val current = conflict ?: return
    // Each question gets its own dialog, so an answer meant for one never lands on the next.
    key(current.id) {
        if (isWideScreen) {
            TabletConflictDialog(current, viewModel::resolveConflict)
        } else {
            PhoneConflictDialog(current, viewModel::resolveConflict)
        }
    }
}

@Composable
private fun conflictTitle(conflict: FileConflict): String = stringResource(
    if (conflict.existingIsDirectory) R.string.files_conflict_title_folder else R.string.files_conflict_title_file
)

private fun conflictDetails(size: Long, modified: Long): String {
    val date = if (modified <= 0L) "" else
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(modified))
    return listOf(formatFileSize(size), date).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun PhoneConflictDialog(
    conflict: FileConflict,
    onResolve: (ConflictChoice, Boolean) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var applyToAll by remember { mutableStateOf(false) }
    // The flip dialog reports its own closing after every button too; only the first word counts.
    var answered by remember { mutableStateOf(false) }
    ZuneFlipDialog(
        onDismissRequest = {
            if (!answered) {
                answered = true
                onResolve(ConflictChoice.SKIP, false)
            }
        },
        title = conflictTitle(conflict)
    ) {
        val muted = Color.White.copy(alpha = 0.6f)
        Text(
            text = conflict.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = zuneColors.accentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConflictSide(
            label = stringResource(R.string.files_conflict_incoming),
            details = conflictDetails(conflict.sourceSize, conflict.sourceModified),
            labelColor = muted,
            textColor = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))
        ConflictSide(
            label = stringResource(R.string.files_conflict_existing),
            details = conflictDetails(conflict.existingSize, conflict.existingModified),
            labelColor = muted,
            textColor = Color.White
        )
        if (conflict.existingIsDirectory) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.files_conflict_folder_note),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }
        if (conflict.othersRemaining > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            ApplyToAllToggle(
                text = pluralStringResource(R.plurals.files_conflict_apply_all, conflict.othersRemaining, conflict.othersRemaining),
                checked = applyToAll,
                onToggle = { applyToAll = !applyToAll },
                frame = Color.White,
                fill = zuneColors.accentColor,
                textColor = Color.White
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        // Three answers do not fit side by side on a phone; Windows Phone stacked them, full width.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                ConflictChoice.REPLACE to R.string.files_conflict_replace,
                ConflictChoice.KEEP_BOTH to R.string.files_conflict_keep_both,
                ConflictChoice.SKIP to R.string.files_conflict_skip
            ).forEach { (choice, label) ->
                ZuneDialogButton(
                    text = stringResource(label),
                    onClick = {
                        if (!answered) {
                            answered = true
                            val all = applyToAll
                            dismissWithAnim { onResolve(choice, all) }
                        }
                    },
                    borderColor = if (choice == ConflictChoice.REPLACE) zuneColors.accentColor else zuneColors.textMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TabletConflictDialog(
    conflict: FileConflict,
    onResolve: (ConflictChoice, Boolean) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    var applyToAll by remember { mutableStateOf(false) }
    Win8MessageDialog(
        title = conflictTitle(conflict),
        confirmLabel = stringResource(R.string.files_conflict_replace),
        secondaryLabel = stringResource(R.string.files_conflict_keep_both),
        dismissLabel = stringResource(R.string.files_conflict_skip),
        onConfirm = { onResolve(ConflictChoice.REPLACE, applyToAll) },
        onSecondary = { onResolve(ConflictChoice.KEEP_BOTH, applyToAll) },
        onDismiss = { onResolve(ConflictChoice.SKIP, applyToAll) }
    ) {
        Text(
            text = conflict.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(16.dp))
        // The two side by side, as Windows 8's own "replace or skip" put them.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ConflictSide(
                label = stringResource(R.string.files_conflict_incoming),
                details = conflictDetails(conflict.sourceSize, conflict.sourceModified),
                labelColor = zuneColors.textMuted,
                textColor = fg,
                modifier = Modifier.weight(1f)
            )
            ConflictSide(
                label = stringResource(R.string.files_conflict_existing),
                details = conflictDetails(conflict.existingSize, conflict.existingModified),
                labelColor = zuneColors.textMuted,
                textColor = fg,
                modifier = Modifier.weight(1f)
            )
        }
        if (conflict.existingIsDirectory) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.files_conflict_folder_note),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )
        }
        if (conflict.othersRemaining > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            ApplyToAllToggle(
                text = pluralStringResource(R.plurals.files_conflict_apply_all, conflict.othersRemaining, conflict.othersRemaining),
                checked = applyToAll,
                onToggle = { applyToAll = !applyToAll },
                frame = fg,
                fill = zuneColors.accentColor,
                textColor = fg
            )
        }
    }
}

@Composable
private fun ConflictSide(
    label: String,
    details: String,
    labelColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = labelColor)
        Text(
            text = details,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = textColor
        )
    }
}

/** A square that is either empty or filled with a tick, the way both systems drew a check box. */
@Composable
private fun ApplyToAllToggle(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    frame: Color,
    fill: Color,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .then(if (checked) Modifier.background(fill) else Modifier.border(2.dp, frame))
        ) {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}
