package com.serkantkn.zunelauncher.ui.screens.notes

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════
// COLOR / TEXT HELPERS
// ════════════════════════════════════════════════════════════

/** Metro accent swatches offered in the note color picker. First entry (null) = launcher accent. */
internal val notePalette: List<Pair<String?, Color>> = listOf(
    null to Color.Transparent,
    "#D81B60" to ZuneColors.Magenta,
    "#E91E90" to ZuneColors.Pink,
    "#FF6D00" to ZuneColors.Orange,
    "#9C27B0" to ZuneColors.Purple,
    "#2196F3" to ZuneColors.Blue,
    "#4CAF50" to ZuneColors.Green,
    "#00BCD4" to ZuneColors.Teal,
    "#E53935" to ZuneColors.Red,
    "#FFEB3B" to ZuneColors.Yellow,
    "#CDDC39" to ZuneColors.Lime,
    "#DC143C" to ZuneColors.Crimson,
    "#0050EF" to ZuneColors.Cobalt,
    "#F0A30A" to ZuneColors.Amber,
    "#825A2C" to ZuneColors.Brown
)

internal fun noteColor(colorHex: String?, accent: Color): Color {
    if (colorHex.isNullOrBlank()) return accent
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        accent
    }
}

internal fun formatNoteDate(timestamp: Long): String {
    return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal fun formatReminderDate(timestamp: Long): String {
    return SimpleDateFormat("d MMMM EEEE, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal fun wordCount(text: String): Int =
    text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

/**
 * Renders the editor's lightweight markdown subset for previews and cards:
 * `# başlık` (bold, larger), `- madde` / `* madde` (bullet), `**kalın**` (bold).
 */
internal fun renderNoteMarkdown(text: String, baseColor: Color, accent: Color): AnnotatedString {
    val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { index, rawLine ->
            var line = rawLine
            var lineStyle: SpanStyle? = null
            when {
                line.startsWith("# ") -> {
                    line = line.removePrefix("# ")
                    lineStyle = SpanStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, color = baseColor)
                }
                line.startsWith("## ") -> {
                    line = line.removePrefix("## ")
                    lineStyle = SpanStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, color = baseColor)
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    line = "•  " + line.drop(2)
                }
            }
            val body: AnnotatedString.Builder.() -> Unit = {
                var cursor = 0
                boldRegex.findAll(line).forEach { match ->
                    append(line.substring(cursor, match.range.first))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                        append(match.groupValues[1])
                    }
                    cursor = match.range.last + 1
                }
                append(line.substring(cursor))
            }
            if (lineStyle != null) withStyle(lineStyle) { body() } else body()
            if (index != lines.lastIndex) append("\n")
        }
    }
}

// ════════════════════════════════════════════════════════════
// BIOMETRIC GATE (locked notes)
// ════════════════════════════════════════════════════════════

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** True when the device has a screen lock / biometric the BiometricPrompt can use. */
internal fun canUseDeviceLock(context: Context): Boolean {
    val activity = context.findFragmentActivity() ?: return false
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
}

/**
 * Asks the user to verify with biometrics or the device credential before a locked note opens.
 * Falls through to [onFailure] with a Turkish reason when the device has no lock configured.
 */
internal fun authenticateForNote(
    context: Context,
    title: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        onFailure(context.getString(R.string.notes_auth_unavailable))
        return
    }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuth = BiometricManager.from(activity).canAuthenticate(authenticators)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        // No credential to verify against: do not lock the user out of their own note.
        onSuccess()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onFailure(errString.toString())
                }
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(context.getString(R.string.notes_auth_subtitle))
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(info)
}

// ════════════════════════════════════════════════════════════
// WINDOWS PHONE SQUARE CHECK BOX / RADIO
// ════════════════════════════════════════════════════════════

@Composable
internal fun WpCheckBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalZuneColors.current.accentColor,
    size: Dp = 22.dp
) {
    val zuneColors = LocalZuneColors.current
    val fillAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(160),
        label = "wp_checkbox_fill"
    )
    val borderColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)

    Box(
        modifier = modifier
            .size(size)
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(0.dp))
            .background(accent.copy(alpha = fillAlpha), RoundedCornerShape(0.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (fillAlpha > 0.3f) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White.copy(alpha = fillAlpha),
                modifier = Modifier.size(size * 0.8f)
            )
        }
    }
}

/** Metro radio row used inside flip dialogs (sort mode, etc.). */
@Composable
internal fun WpRadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(zuneColors.accentColor)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = Color.White
        )
    }
}

/** Metro "chip": sharp rectangle, filled when selected. */
@Composable
internal fun WpTagChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalZuneColors.current.accentColor,
    onRemove: (() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    val border = if (zuneColors.isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(BorderStroke(1.5.dp, if (selected) accent else border), RoundedCornerShape(0.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text.lowercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
        )
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_remove),
                tint = if (selected) Color.White else zuneColors.textMuted,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onRemove() }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// NOTE CARD (list row)
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(
    note: Note,
    index: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleItem: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    isPinnedToStart: Boolean = false
) {
    val zuneColors = LocalZuneColors.current
    val accent = zuneColors.accentColor
    val stripe = noteColor(note.colorHex, accent)
    val textColor = MaterialTheme.colorScheme.onBackground

    // Staggered slide-in entrance, same curve as SettingGroup
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12) * 40).toLong())
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300)),
        modifier = modifier.fillMaxWidth()
    ) {
        val cardBackground = if (zuneColors.isDark) zuneColors.overlay
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackground, RoundedCornerShape(0.dp))
                .then(
                    if (isSelected) Modifier.border(BorderStroke(2.dp, accent), RoundedCornerShape(0.dp))
                    else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .height(IntrinsicSize.Min)
        ) {
            // Color stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripe)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        WpCheckBox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.padding(end = 12.dp),
                            size = 20.dp
                        )
                    }
                    Text(
                        text = note.displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 22.sp
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.notes_locked),
                            tint = zuneColors.textMuted,
                            modifier = Modifier.padding(start = 8.dp).size(16.dp)
                        )
                    }
                    if (isPinnedToStart) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.notes_on_start),
                            tint = zuneColors.textMuted,
                            modifier = Modifier.padding(start = 8.dp).size(14.dp).alpha(0.7f)
                        )
                    }
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.notes_pinned),
                            tint = stripe,
                            modifier = Modifier.padding(start = 8.dp).size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (note.isLocked) {
                    Text(
                        text = stringResource(R.string.notes_locked_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted
                    )
                } else if (note.isChecklist) {
                    val visibleItems = note.items.take(4)
                    visibleItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            WpCheckBox(
                                checked = item.isChecked,
                                onCheckedChange = { if (!isSelectionMode) onToggleItem(item.id) },
                                accent = stripe,
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            ChecklistText(text = item.text, isChecked = item.isChecked, color = textColor)
                            if (item.reminderAt != null) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = stripe,
                                    modifier = Modifier.padding(start = 6.dp).size(12.dp)
                                )
                            }
                        }
                    }
                    if (note.items.size > visibleItems.size) {
                        Text(
                            text = stringResource(R.string.notes_more_items, note.items.size - visibleItems.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textDim,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } else if (note.content.isNotBlank()) {
                    Text(
                        text = renderNoteMarkdown(note.content.trim().take(220), zuneColors.textMuted, textColor),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Attachments & links summary
                if (!note.isLocked && (note.imagePaths.isNotEmpty() || note.audioPath != null ||
                        note.linkedContactName != null || note.reminderAt != null)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.imagePaths.isNotEmpty()) {
                            MetaBadge(Icons.Default.Image, "${note.imagePaths.size}", zuneColors.textDim)
                        }
                        if (note.audioPath != null) {
                            MetaBadge(Icons.Default.Mic, "ses", zuneColors.textDim)
                        }
                        if (note.linkedContactName != null) {
                            MetaBadge(Icons.Default.Person, note.linkedContactName.lowercase(), zuneColors.textDim)
                        }
                        if (note.reminderAt != null) {
                            MetaBadge(Icons.Default.Notifications, formatNoteDate(note.reminderAt), stripe)
                        }
                    }
                }

                if (!note.isLocked && note.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = note.tags.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = stripe.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        note.isInTrash -> stringResource(R.string.notes_deleted_at, formatNoteDate(note.deletedAt ?: note.updatedAt))
                        note.isChecklist -> stringResource(R.string.notes_completed_at, note.checkedCount, note.items.size, formatNoteDate(note.updatedAt))
                        else -> formatNoteDate(note.updatedAt)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textDim
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(icon: ImageVector, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Checklist row text with an animated strike-through when checked. */
@Composable
internal fun ChecklistText(
    text: String,
    isChecked: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val checkedAlpha by animateFloatAsState(
        targetValue = if (isChecked) 0.45f else 1f,
        animationSpec = tween(200),
        label = "checklist_text_alpha"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Light,
            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
        ),
        color = if (isChecked) zuneColors.textMuted else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.alpha(checkedAlpha)
    )
}

// ════════════════════════════════════════════════════════════
// ATTACHMENT ROWS (editor)
// ════════════════════════════════════════════════════════════

@Composable
internal fun ImageStrip(
    paths: List<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (paths.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(paths, key = { it }) { path ->
            Box(modifier = Modifier.size(96.dp)) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(0.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove(path) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notes_remove_image), tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun AudioRow(
    path: String,
    isPlaying: Boolean,
    accent: Color,
    onTogglePlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)), RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent)
                .clickable { onTogglePlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.notes_stop) else stringResource(R.string.notes_play),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isPlaying) stringResource(R.string.notes_audio_playing) else stringResource(R.string.notes_audio_note),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.notes_remove_audio),
            tint = zuneColors.textDim,
            modifier = Modifier
                .size(18.dp)
                .clickable { onRemove() }
        )
    }
}

// ════════════════════════════════════════════════════════════
// EMPTY STATE
// ════════════════════════════════════════════════════════════

@Composable
internal fun NotesEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = zuneColors.accentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// DIALOGS
// ════════════════════════════════════════════════════════════

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            ZuneDialogButton(
                text = confirmText,
                onClick = { dismissWithAnim { onConfirm() } },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
internal fun DeleteNotesDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = if (count > 1) stringResource(R.string.notes_trash_many, count) else stringResource(R.string.notes_trash_one),
        message = stringResource(R.string.notes_trash_message),
        confirmText = stringResource(R.string.notes_trash_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
internal fun DeleteForeverDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = if (count > 1) stringResource(R.string.notes_forever_many, count) else stringResource(R.string.notes_forever_one),
        message = stringResource(R.string.notes_forever_message),
        confirmText = stringResource(R.string.notes_forever_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
internal fun ClearArchiveDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.notes_clear_archive_cap),
        message = stringResource(R.string.notes_clear_archive_message, count),
        confirmText = stringResource(R.string.common_clear_cap),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
internal fun EmptyTrashDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.notes_empty_trash_cap),
        message = stringResource(R.string.notes_empty_trash_message, count),
        confirmText = stringResource(R.string.notes_empty_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
internal fun SortDialog(
    current: NoteSortMode,
    onSelect: (NoteSortMode) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notes_sort_cap),
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_close_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Column {
            NoteSortMode.entries.forEach { mode ->
                WpRadioRow(
                    text = stringResource(mode.titleRes),
                    selected = mode == current,
                    onClick = { dismissWithAnim { onSelect(mode) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteColorDialog(
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notes_color_cap),
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_close_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            notePalette.forEach { (hex, color) ->
                val swatch = if (hex == null) zuneColors.accentColor else color
                val isSelected = hex == selectedHex
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(swatch, RoundedCornerShape(0.dp))
                        .then(
                            if (isSelected) Modifier.border(BorderStroke(3.dp, Color.White), RoundedCornerShape(0.dp))
                            else Modifier
                        )
                        .clickable { dismissWithAnim { onSelect(hex) } },
                    contentAlignment = Alignment.Center
                ) {
                    if (hex == null) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsDialog(
    current: List<String>,
    allTags: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var selected by remember { mutableStateOf(current) }
    var newTag by remember { mutableStateOf("") }

    fun addTag() {
        val cleaned = newTag.trim().removePrefix("#").lowercase()
        if (cleaned.isNotEmpty() && cleaned !in selected) selected = selected + cleaned
        newTag = ""
    }

    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notes_tags_cap),
        confirmButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_save_cap),
                onClick = { addTag(); dismissWithAnim { onSave(selected) } },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)), RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (newTag.isEmpty()) {
                        Text(stringResource(R.string.notes_new_tag), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = stringResource(R.string.common_add),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier
                        .clickable { addTag() }
                        .padding(start = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            val union = (selected + allTags).distinct()
            if (union.isEmpty()) {
                Text(
                    text = stringResource(R.string.notes_no_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    union.forEach { tag ->
                        WpTagChip(
                            text = tag,
                            selected = tag in selected,
                            onClick = { selected = if (tag in selected) selected - tag else selected + tag }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContactPickerDialog(
    contacts: List<ContactModel>,
    currentContactId: String?,
    onPick: (ContactModel) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts else contacts.filter { it.name.contains(query, ignoreCase = true) }
    }
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notes_link_contact_cap),
        confirmButton = if (currentContactId != null) {
            { ZuneDialogButton(text = stringResource(R.string.notes_unlink_cap), onClick = { dismissWithAnim { onClear() } }, borderColor = zuneColors.accentColor) }
        } else null,
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_close_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)), RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_contacts), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.notes_no_contacts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { contact ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dismissWithAnim { onPick(contact) } }
                                .padding(vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (contact.id == currentContactId) zuneColors.accentColor else Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(1).uppercase(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reminder picker: quick day chips (bugün / yarın / 3 gün / 1 hafta) + a Material TimePicker,
 * wrapped in the Metro flip dialog like the Clock Hub's alarm dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderDialog(
    title: String,
    initial: Long?,
    onSet: (Long) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val base = remember { Calendar.getInstance().apply { initial?.let { timeInMillis = it } } }
    val timePickerState = rememberTimePickerState(
        initialHour = base.get(Calendar.HOUR_OF_DAY),
        initialMinute = base.get(Calendar.MINUTE),
        is24Hour = true
    )
    var dayOffset by remember {
        mutableIntStateOf(
            if (initial == null) 0 else {
                val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                (((initial - today.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()).coerceIn(0, 7)
            }
        )
    }
    val options = listOf(0 to stringResource(R.string.common_today), 1 to stringResource(R.string.notes_tomorrow), 3 to stringResource(R.string.notes_in_3_days), 7 to stringResource(R.string.notes_in_1_week))

    fun resolve(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            set(Calendar.MINUTE, timePickerState.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_set_cap),
                onClick = { dismissWithAnim { onSet(resolve()) } },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            if (onClear != null && initial != null) {
                ZuneDialogButton(text = stringResource(R.string.notes_remove_cap), onClick = { dismissWithAnim { onClear() } }, borderColor = zuneColors.textMuted)
            } else {
                ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (offset, label) ->
                    WpTagChip(
                        text = label,
                        selected = dayOffset == offset,
                        onClick = { dayOffset = offset }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        }
    }
}

/** stringResource(R.string.common_add) menu: attachments and links, as a Metro text list. */
@Composable
internal fun InsertDialog(
    hasAudio: Boolean,
    onPickImage: () -> Unit,
    onRecordAudio: () -> Unit,
    onSpeech: () -> Unit,
    onLinkContact: () -> Unit,
    onReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.common_add_cap),
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_close_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Column {
            listOf(
                Triple(Icons.Default.Image, stringResource(R.string.notes_insert_image), onPickImage),
                Triple(Icons.Default.Mic, if (hasAudio) stringResource(R.string.notes_rerecord_audio) else stringResource(R.string.notes_audio_note), onRecordAudio),
                Triple(Icons.Default.Mic, stringResource(R.string.notes_dictate), onSpeech),
                Triple(Icons.Default.Person, stringResource(R.string.notes_link_contact), onLinkContact),
                Triple(Icons.Default.Notifications, stringResource(R.string.notes_reminder), onReminder)
            ).forEach { (icon, label, action) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dismissWithAnim { action() } }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
                        color = Color.White
                    )
                }
            }
        }
    }
}
