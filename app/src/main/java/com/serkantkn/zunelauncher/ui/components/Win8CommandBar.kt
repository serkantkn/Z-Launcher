package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * One command on the Windows 8 bar. [menu], when given, is what the command opens: a small
 * flyout just above it (the order, the view) rather than doing something at once.
 */
data class Win8Command(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    val onClick: () -> Unit = {}
)

/**
 * The Windows 8 app bar: a band across the bottom, each command a ringed glyph with its name
 * under it. Commands for what is chosen stand on the left and the page's own on the right, the
 * way Windows 8 split them; a bar with more than fits scrolls sideways rather than dropping any.
 */
@Composable
fun Win8CommandBar(
    left: List<Win8Command>,
    right: List<Win8Command>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onAnyCommand: () -> Unit = {}
) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFEDEDED))
                // The bar takes its own taps so they do not fall through to the page.
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .padding(bottom = navBottom)
        ) {
            // At least as wide as the screen, so the two groups sit at its two ends; wider, and
            // it scrolls.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .widthIn(min = maxWidth)
                        .padding(horizontal = 40.dp, vertical = 12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(BAR_GAP)) {
                        left.forEach { CommandButton(it, fg, onAnyCommand) }
                    }
                    Spacer(modifier = Modifier.width(48.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(BAR_GAP)) {
                        right.forEach { CommandButton(it, fg, onAnyCommand) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandButton(command: Win8Command, fg: Color, onAnyCommand: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val tint = if (command.enabled) fg else fg.copy(alpha = 0.35f)
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .clickable(
                    enabled = command.enabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (command.menu != null) {
                        menuOpen = true
                    } else {
                        command.onClick()
                        onAnyCommand()
                    }
                }
        ) {
            Box(
                modifier = Modifier.size(40.dp).border(2.dp, tint.copy(alpha = tint.alpha * 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = command.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = command.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = tint,
                maxLines = 1
            )
        }
        val menu = command.menu
        if (menu != null) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = if (zuneColors.isDark) Color(0xFF2B2B2B) else Color.White,
                shape = RectangleShape
            ) {
                menu { menuOpen = false; onAnyCommand() }
            }
        }
    }
}

/** A choice in a command's flyout, the current one in the accent. */
@Composable
fun Win8MenuItem(text: String, chosen: Boolean = false, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = if (chosen) zuneColors.accentColor else fg
            )
        },
        onClick = onClick
    )
}

// ════════════════════════════════════════════════════════════
// THE MESSAGE BAND
// ════════════════════════════════════════════════════════════

/**
 * Windows 8's message dialog: a band right across the middle of the screen, the rest of it
 * dimmed, the words on the left of a centred column and the buttons at its foot on the right —
 * the primary one filled in the accent.
 */
@Composable
fun Win8MessageDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                // The band stays above the keyboard, buttons and all.
                .imePadding()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (zuneColors.isDark) Color(0xFF1F1F1F) else Color.White)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                    .padding(vertical = 28.dp)
            ) {
                Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 32.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp),
                        color = fg
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    content()
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BandButton(
                            text = confirmLabel,
                            filled = true,
                            fill = if (destructive) Color(0xFFE51400) else zuneColors.accentColor,
                            fg = fg,
                            enabled = confirmEnabled,
                            onClick = onConfirm
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BandButton(text = dismissLabel, filled = false, fill = fg, fg = fg, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

/** The band with a box to type in, the name already chosen in it, as Windows 8 asked for one. */
@Composable
fun Win8TextDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    // The name without its extension is chosen, so typing replaces just that, as Windows did.
    val stem = initial.substringBeforeLast('.', initial).length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, stem))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Win8MessageDialog(
        title = title,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = { if (value.text.isNotBlank()) onConfirm(value.text.trim()) },
        onDismiss = onDismiss,
        confirmEnabled = value.text.isNotBlank()
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            // A file's name is typed as it is meant: nothing corrected, nothing capitalised.
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onConfirm(value.text.trim()) }),
            textStyle = TextStyle(color = fg, fontSize = 18.sp),
            cursorBrush = SolidColor(zuneColors.accentColor),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, zuneColors.accentColor)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .focusRequester(focus)
        )
    }
}

@Composable
private fun BandButton(
    text: String,
    filled: Boolean,
    fill: Color,
    fg: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(min = 110.dp)
            .then(
                if (filled) Modifier.background(fill.copy(alpha = alpha))
                else Modifier.border(2.dp, fg.copy(alpha = 0.6f))
            )
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = if (filled) Color.White.copy(alpha = alpha) else fg
        )
    }
}

private val BAR_GAP = 18.dp
