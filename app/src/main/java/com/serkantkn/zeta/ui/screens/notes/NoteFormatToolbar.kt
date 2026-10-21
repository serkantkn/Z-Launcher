package com.serkantkn.zeta.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.NoteFont
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens

/**
 * The tools for dressing a note's words, docked over the bar at the bottom of the editor.
 *
 * Two rows. The first is what is reached for most - bold, italic, underline, strike-through, the
 * three alignments, the two kinds of list, and taking it all off again. The second runs sideways:
 * headings and sizes, a colour for the words, a highlight behind them, and the typeface of the
 * whole note. Everything acts on what is selected, or on what is typed next when nothing is.
 *
 * Metro buttons throughout: square, a thin ring at rest and filled with the accent while what
 * they do is in force where the cursor is.
 */
@Composable
internal fun NoteFormatToolbar(
    state: RichTextState,
    font: NoteFont?,
    onFont: (NoteFont?) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val span = state.currentSpanStyle
    val paragraph = state.currentParagraphStyle
    val decoration = span.textDecoration
    val heading = state.currentHeadingStyle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (zuneColors.isDark) Color(0xFF151515) else Color(0xFFF0F0F0))
            .padding(vertical = 8.dp)
    ) {
        // ── What is reached for most ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
        ) {
            ToolButton(Icons.Default.FormatBold, stringResource(R.string.notes_bold), span.fontWeight == FontWeight.Bold, accent) {
                state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
            }
            ToolButton(Icons.Default.FormatItalic, stringResource(R.string.notes_italic), span.fontStyle == FontStyle.Italic, accent) {
                state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
            }
            ToolButton(Icons.Default.FormatUnderlined, stringResource(R.string.notes_underline), decoration?.contains(TextDecoration.Underline) == true, accent) {
                state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
            }
            ToolButton(Icons.Default.FormatStrikethrough, stringResource(R.string.notes_strike), decoration?.contains(TextDecoration.LineThrough) == true, accent) {
                state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            }
            Gap()
            listOf(
                Triple(TextAlign.Start, Icons.Default.FormatAlignLeft, R.string.notes_align_left),
                Triple(TextAlign.Center, Icons.Default.FormatAlignCenter, R.string.notes_align_center),
                Triple(TextAlign.End, Icons.Default.FormatAlignRight, R.string.notes_align_right)
            ).forEach { (align, icon, label) ->
                val current = paragraph.textAlign.takeIf { it != TextAlign.Unspecified } ?: TextAlign.Start
                val isOn = current == align || (align == TextAlign.Start && current == TextAlign.Left) ||
                    (align == TextAlign.End && current == TextAlign.Right)
                ToolButton(icon, stringResource(label), isOn, accent) {
                    state.addParagraphStyle(ParagraphStyle(textAlign = align))
                }
            }
            Gap()
            ToolButton(Icons.Default.FormatListBulleted, stringResource(R.string.notes_bullet), state.isUnorderedList, accent) {
                state.toggleUnorderedList()
            }
            ToolButton(Icons.Default.FormatListNumbered, stringResource(R.string.notes_numbered), state.isOrderedList, accent) {
                state.toggleOrderedList()
            }
            Gap()
            ToolButton(Icons.Default.FormatClear, stringResource(R.string.notes_clear_format), false, accent) {
                state.clearSpanStyles()
                state.setHeadingStyle(HeadingStyle.Normal)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Size, colour, highlight, typeface ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
        ) {
            ToolLabel(stringResource(R.string.notes_size))
            WordButton(stringResource(R.string.notes_heading), heading == HeadingStyle.H1, accent) {
                state.setHeadingStyle(if (heading == HeadingStyle.H1) HeadingStyle.Normal else HeadingStyle.H1)
            }
            WordButton(stringResource(R.string.notes_subheading), heading == HeadingStyle.H2, accent) {
                state.setHeadingStyle(if (heading == HeadingStyle.H2) HeadingStyle.Normal else HeadingStyle.H2)
            }
            SIZES.forEach { (size, label) ->
                val current = span.fontSize
                val isOn = if (size == TextUnit.Unspecified) current == TextUnit.Unspecified && heading == HeadingStyle.Normal else current == size
                WordButton(stringResource(label), isOn, accent) {
                    if (current != TextUnit.Unspecified) state.removeSpanStyle(SpanStyle(fontSize = current))
                    if (size != TextUnit.Unspecified) state.addSpanStyle(SpanStyle(fontSize = size))
                }
            }

            Gap()
            ToolLabel(stringResource(R.string.notes_text_colour))
            Swatch(null, span.color == Color.Unspecified, accent) {
                if (span.color != Color.Unspecified) state.removeSpanStyle(SpanStyle(color = span.color))
            }
            TEXT_COLOURS.forEach { colour ->
                Swatch(colour, span.color == colour, accent) {
                    if (span.color != Color.Unspecified) state.removeSpanStyle(SpanStyle(color = span.color))
                    if (span.color != colour) state.addSpanStyle(SpanStyle(color = colour))
                }
            }

            Gap()
            ToolLabel(stringResource(R.string.notes_highlight))
            Swatch(null, span.background == Color.Unspecified, accent) {
                if (span.background != Color.Unspecified) state.removeSpanStyle(SpanStyle(background = span.background))
            }
            HIGHLIGHTS.forEach { colour ->
                Swatch(colour, span.background == colour, accent) {
                    if (span.background != Color.Unspecified) state.removeSpanStyle(SpanStyle(background = span.background))
                    if (span.background != colour) state.addSpanStyle(SpanStyle(background = colour))
                }
            }

            Gap()
            ToolLabel(stringResource(R.string.notes_typeface))
            NoteFont.entries.forEach { choice ->
                WordButton(
                    label = stringResource(choice.labelRes),
                    isOn = (font ?: NoteFont.SANS) == choice,
                    accent = accent,
                    fontFamily = choice.family
                ) { onFont(choice.takeUnless { it == NoteFont.SANS }) }
            }
        }
    }
}

/** The family a note's typeface is drawn in. */
internal val NoteFont.family: FontFamily
    get() = when (this) {
        NoteFont.SANS -> FontFamily.Default
        NoteFont.SERIF -> FontFamily.Serif
        NoteFont.MONO -> FontFamily.Monospace
        NoteFont.HAND -> FontFamily.Cursive
    }

private val NoteFont.labelRes: Int
    get() = when (this) {
        NoteFont.SANS -> R.string.notes_font_sans
        NoteFont.SERIF -> R.string.notes_font_serif
        NoteFont.MONO -> R.string.notes_font_mono
        NoteFont.HAND -> R.string.notes_font_hand
    }

/** Unspecified is "normal": the size the note is written in. */
private val SIZES = listOf(
    14.sp to R.string.notes_size_small,
    TextUnit.Unspecified to R.string.notes_size_normal,
    22.sp to R.string.notes_size_large,
    28.sp to R.string.notes_size_huge
)

/** Windows Phone's own accents, which read on a light page and a dark one alike. */
private val TEXT_COLOURS = listOf(
    Color(0xFFE51400), Color(0xFFF09609), Color(0xFF60A917), Color(0xFF1BA1E2),
    Color(0xFF0050EF), Color(0xFFAA00FF), Color(0xFFD80073), Color(0xFF825A2C)
)

/** Highlighter colours, see-through enough to read black or white words through. */
private val HIGHLIGHTS = listOf(
    Color(0x80FFEB3B), Color(0x8076FF03), Color(0x8018FFFF), Color(0x80FF80AB)
)

@Composable
private fun ToolButton(icon: ImageVector, label: String, isOn: Boolean, accent: Color, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .wpTilt(source)
            .then(if (isOn) Modifier.background(accent) else Modifier.border(1.5.dp, ink.copy(alpha = 0.35f)))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = if (isOn) Color.White else ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun WordButton(
    label: String,
    isOn: Boolean,
    accent: Color,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(36.dp)
            .wpTilt(source)
            .then(if (isOn) Modifier.background(accent) else Modifier.border(1.5.dp, ink.copy(alpha = 0.35f)))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily ?: MaterialTheme.typography.labelLarge.fontFamily),
            color = if (isOn) Color.White else ink
        )
    }
}

/** A square of a colour; null is "none", drawn as a crossed-out square. */
@Composable
private fun Swatch(colour: Color?, isOn: Boolean, accent: Color, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .border(if (isOn) 3.dp else 1.dp, if (isOn) accent else ink.copy(alpha = 0.3f))
            .padding(if (isOn) 5.dp else 3.dp)
            .background(colour ?: Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        if (colour == null) {
            Text(text = "∅", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ToolLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalZuneColors.current.textMuted,
        modifier = Modifier.padding(end = 2.dp)
    )
}

@Composable
private fun Gap() = Spacer(modifier = Modifier.width(10.dp))
