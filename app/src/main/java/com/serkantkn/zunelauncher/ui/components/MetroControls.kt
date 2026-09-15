package com.serkantkn.zunelauncher.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.util.Locale

/**
 * The Windows Phone building blocks the hubs are drawn from.
 *
 * Two ideas run through all of them. Numbers are set in tabular figures, because a digit that is
 * narrower than its neighbours makes a running counter jitter sideways twice a second — the reason
 * every real stopwatch face is monospaced. And nothing is round: progress is a line across the
 * page, a switch is a rectangle, a chosen day is a square. Metro has no circles.
 *
 * They were written for the Clock hub and they suit every hub, so they live here rather than
 * inside one of them.
 */

// ── Numbers ─────────────────────────────────────────────────────────────────

/** Figures that all take the same width, so a counter counts without shuffling. */
@Composable
fun metroDigitStyle(
    fontSize: TextUnit,
    weight: FontWeight = FontWeight.Thin,
    letterSpacing: TextUnit = (-3).sp
): TextStyle = MaterialTheme.typography.displayLarge.copy(
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = fontSize,
    letterSpacing = letterSpacing,
    fontFeatureSettings = "tnum"
)

/** The one enormous number a page is about. */
@Composable
fun MetroBigNumber(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val zuneColors = LocalZuneColors.current
    Text(
        text = text,
        style = metroDigitStyle(fontSize),
        color = color ?: if (zuneColors.isDark) Color.White else Color.Black,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

// ── Rules and headings ──────────────────────────────────────────────────────

/**
 * A Zune section rule: a word in the accent colour with a hairline running off to the edge, and
 * optionally something to press at the end of it.
 */
@Composable
fun MetroRule(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ZuneDimens.SpacingLg, bottom = ZuneDimens.SpacingSm)
    ) {
        Text(
            text = title.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = zuneColors.accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(zuneColors.accentColor.copy(alpha = 0.35f))
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = actionLabel.lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * A page with nothing on it, saying so — and where possible offering the one thing that would fix
 * that, because an empty page with no way off it is a dead end.
 */
@Composable
fun MetroEmpty(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = modifier.fillMaxWidth().padding(top = ZuneDimens.SpacingLg)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel.lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onAction)
            )
        }
    }
}

/** A line of warning in the accent colour, for something the phone will not let the hub do. */
@Composable
fun MetroNotice(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(zuneColors.accentColor.copy(alpha = 0.16f))
            .clickable(onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = actionLabel.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge,
            color = zuneColors.accentColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ── Metro controls ──────────────────────────────────────────────────────────

/**
 * Progress as a line across the page rather than a ring.
 *
 * Windows Phone drew a timer's progress as a bar, not a circle; the circle in the old hub was
 * Material's, and it was the one shape in the whole launcher that looked borrowed.
 */
@Composable
fun MetroProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(zuneColors.textMuted.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(thickness)
                .background(zuneColors.accentColor)
        )
    }
}

/** One day of the week as a square that is either filled in or outlined. Nothing else. */
@Composable
fun MetroDaySquare(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = modifier
            .size(40.dp)
            .border(2.dp, if (selected) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.5f))
            .background(if (selected) zuneColors.accentColor else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = if (selected) Color.White else zuneColors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** A duration offered as one tap: a rectangle with the number in it. */
@Composable
fun MetroChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .wpTilt(interactionSource)
            .border(2.dp, if (selected) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.45f))
            .background(if (selected) zuneColors.accentColor else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else zuneColors.textMuted
        )
    }
}

/** A row in a settings-like list inside the hub: a title, a value under it, both pressable. */
@Composable
fun MetroValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** A row with something to switch on or off at the end of it. */
@Composable
fun MetroSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textMuted
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        com.serkantkn.zunelauncher.ui.screens.settings.WindowsPhoneSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
