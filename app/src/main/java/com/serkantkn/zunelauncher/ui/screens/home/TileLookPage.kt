package com.serkantkn.zunelauncher.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.TileInk
import com.serkantkn.zunelauncher.data.model.TileLook
import com.serkantkn.zunelauncher.ui.screens.settings.CustomColorPickerDialog
import com.serkantkn.zunelauncher.ui.screens.settings.SettingChoiceRow
import com.serkantkn.zunelauncher.ui.screens.settings.SettingGroup
import com.serkantkn.zunelauncher.ui.screens.settings.SettingPill
import com.serkantkn.zunelauncher.ui.screens.settings.SettingRowContent
import com.serkantkn.zunelauncher.ui.screens.settings.SettingSwitchRow
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.ui.theme.toColor
import kotlin.math.roundToInt

/**
 * One tile's own settings: what it is called, what colour it is, what it shows and how it moves.
 *
 * A page rather than a dialog, like the icon picker — there is a lot here and the tile itself
 * sits at the top, drawn with every change the moment it is made. Nothing is held back for a
 * save button: the board behind this page is the same board, so "bitti" simply closes it, and
 * "sıfırla" hands the tile back to the board's settings.
 */
@Composable
fun TileLookPage(
    /** The tile's own name, under the heading and standing in for an empty name field. */
    subject: String,
    look: TileLook,
    /** The board-wide transparency, where the slider starts when the tile has none of its own. */
    boardOpacity: Int,
    onChange: (TileLook) -> Unit,
    onPickIcon: () -> Unit,
    onPickPicture: () -> Unit,
    onClearPicture: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * Hubs only: what the tile opens — the name of the app it was pointed at, or null for the hub
     * itself — and the way to change it. Left null for tiles that are not hubs.
     */
    openTargetLabel: String? = null,
    onPickOpenTarget: (() -> Unit)? = null,
    /** The tile as it looks right now; the caller draws it, so it is the real thing. */
    preview: @Composable () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    BackHandler(onBack = onDismiss)

    var showColorPicker by remember { mutableStateOf(false) }
    // The name field's text lives here, not in the store: the store answers a keystroke late
    // and would put the cursor back a letter, so it is only ever written to, never read back —
    // except on a reset, which empties the field by hand.
    var nameDraft by remember { mutableStateOf(look.name.orEmpty()) }
    if (showColorPicker) {
        CustomColorPickerDialog(
            initialColor = look.color ?: zuneColors.accentColor.toArgb(),
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                onChange(look.copy(color = it))
                showColorPicker = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(LOOK_PAGE_Z)
            .background(if (zuneColors.isDark) Color.Black else Color.White)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {})
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            ) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.tile_look_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 34.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))
                // The tile, on its own, the width of a medium tile on the phone board.
                Box(modifier = Modifier.width(PREVIEW_WIDTH)) { preview() }
                Spacer(modifier = Modifier.height(28.dp))

                // ── What a tap opens (hubs) ──
                if (onPickOpenTarget != null) {
                    SettingGroup(title = stringResource(R.string.tile_look_open)) {
                        ArrowRow(
                            title = stringResource(R.string.hub_target_title),
                            subtitle = openTargetLabel ?: stringResource(R.string.hub_target_hub),
                            onClick = onPickOpenTarget
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ── Colour ──
                SettingGroup(title = stringResource(R.string.tile_look_color)) {
                    ColorSwatches(
                        selected = look.color,
                        accent = zuneColors.accentColor,
                        onPick = { onChange(look.copy(color = it)) },
                        onPickCustom = { showColorPicker = true }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Name ──
                SettingGroup(title = stringResource(R.string.tile_look_name)) {
                    NameField(
                        value = nameDraft,
                        placeholder = subject,
                        ink = ink,
                        onChange = {
                            nameDraft = it
                            onChange(look.copy(name = it.takeIf { n -> n.isNotBlank() }))
                        }
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.tile_look_show_label),
                        subtitle = stringResource(R.string.tile_look_show_label_sub),
                        checked = look.showLabel,
                        onCheckedChange = { onChange(look.copy(showLabel = it)) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Icon ──
                SettingGroup(title = stringResource(R.string.tile_look_icon)) {
                    ArrowRow(
                        title = stringResource(R.string.tile_look_icon_pick),
                        subtitle = when (look.icon) {
                            TileIcon.Default -> stringResource(R.string.tile_look_icon_own)
                            is TileIcon.Glyph -> stringResource(R.string.tile_look_icon_glyph)
                            is TileIcon.Pack -> stringResource(R.string.tile_icon_pack_section)
                            is TileIcon.Picture -> stringResource(R.string.tile_look_icon_picture)
                        },
                        onClick = onPickIcon
                    )
                    LookSlider(
                        label = stringResource(R.string.tile_look_icon_scale),
                        value = "%${(look.iconScale * 100).roundToInt()}",
                        sliderValue = look.iconScale,
                        valueRange = TileLook.MIN_ICON_SCALE..TileLook.MAX_ICON_SCALE,
                        steps = 10,
                        onValueChange = { onChange(look.copy(iconScale = (it * 10f).roundToInt() / 10f)) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Picture ──
                SettingGroup(title = stringResource(R.string.tile_look_picture)) {
                    ArrowRow(
                        title = stringResource(R.string.tile_look_picture_pick),
                        subtitle = stringResource(R.string.tile_look_picture_sub),
                        onClick = onPickPicture
                    )
                    if (look.picture != null) {
                        ArrowRow(
                            title = stringResource(R.string.tile_look_picture_clear),
                            subtitle = "",
                            onClick = onClearPicture,
                            trailingIcon = Icons.Default.Close
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Live face ──
                SettingGroup(title = stringResource(R.string.tile_look_animation)) {
                    SettingChoiceRow(
                        title = stringResource(R.string.tile_look_follow_settings),
                        subtitle = stringResource(R.string.tile_look_follow_settings_sub),
                        selected = look.animation == null,
                        onClick = { onChange(look.copy(animation = null)) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_tile_anim_slide),
                        subtitle = stringResource(R.string.settings_tile_anim_slide_sub),
                        selected = look.animation == TileAnimation.SLIDE,
                        onClick = { onChange(look.copy(animation = TileAnimation.SLIDE)) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_tile_anim_flip),
                        subtitle = stringResource(R.string.settings_tile_anim_flip_sub),
                        selected = look.animation == TileAnimation.FLIP,
                        onClick = { onChange(look.copy(animation = TileAnimation.FLIP)) }
                    )
                    SettingChoiceRow(
                        title = stringResource(R.string.settings_tile_anim_off),
                        subtitle = stringResource(R.string.settings_tile_anim_off_sub),
                        selected = look.animation == TileAnimation.NONE,
                        onClick = { onChange(look.copy(animation = TileAnimation.NONE)) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Notifications ──
                SettingGroup(title = stringResource(R.string.tile_look_notifications)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.tile_look_notifications_show),
                        subtitle = stringResource(R.string.tile_look_notifications_sub),
                        checked = look.notifications,
                        onCheckedChange = { onChange(look.copy(notifications = it)) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Transparency ──
                SettingGroup(title = stringResource(R.string.settings_tile_transparency)) {
                    val opacity = look.opacity ?: boardOpacity
                    LookSlider(
                        label = stringResource(R.string.tile_look_this_tile),
                        value = "%${100 - opacity}",
                        sliderValue = (100 - opacity).toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onChange(look.copy(opacity = (100 - it).roundToInt().coerceIn(0, 100))) }
                    )
                    if (look.opacity != null) {
                        BackToSettingLink(onClick = { onChange(look.copy(opacity = null)) })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // ── Ink ──
                SettingGroup(title = stringResource(R.string.settings_tile_ink)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SettingPill(
                            label = stringResource(R.string.tile_look_follow_settings),
                            selected = look.ink == null,
                            onClick = { onChange(look.copy(ink = null)) },
                            modifier = Modifier.weight(1.3f)
                        )
                        SettingPill(
                            label = stringResource(R.string.settings_tile_ink_dark),
                            selected = look.ink == TileInk.DARK,
                            onClick = { onChange(look.copy(ink = TileInk.DARK)) },
                            modifier = Modifier.weight(1f)
                        )
                        SettingPill(
                            label = stringResource(R.string.settings_tile_ink_light),
                            selected = look.ink == TileInk.LIGHT,
                            onClick = { onChange(look.copy(ink = TileInk.LIGHT)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Reset · done ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 14.dp)
            ) {
                MetroButton(
                    label = stringResource(R.string.common_reset),
                    filled = false,
                    // A hub pointed at another app has something to reset too.
                    enabled = !look.isDefault || openTargetLabel != null,
                    onClick = {
                        nameDraft = ""
                        onReset()
                    },
                    modifier = Modifier.weight(1f)
                )
                MetroButton(
                    label = stringResource(R.string.common_done),
                    filled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// PIECES
// ════════════════════════════════════════════════════════════

/**
 * The accent palette as squares, the theme's own colour first and a custom one last. Squares,
 * not the circles the theme page uses: these are tiles being coloured.
 */
@Composable
private fun ColorSwatches(
    selected: Int?,
    accent: Color,
    onPick: (Int?) -> Unit,
    onPickCustom: () -> Unit
) {
    val palette = remember {
        AccentColor.entries.filter { it != AccentColor.DYNAMIC && it != AccentColor.CUSTOM }
    }
    val paletteArgb = remember(palette) { palette.map { it.toColor().toArgb() } }
    val customSelected = selected != null && selected !in paletteArgb

    // Theme colour, the fourteen named ones, then custom: sixteen, in two rows of eight.
    val cells: List<@Composable () -> Unit> = buildList {
        add {
            Swatch(color = accent, selected = selected == null, onClick = { onPick(null) }, label = stringResource(R.string.tile_look_color_theme))
        }
        palette.forEachIndexed { index, entry ->
            add {
                Swatch(color = entry.toColor(), selected = selected == paletteArgb[index], onClick = { onPick(paletteArgb[index]) })
            }
        }
        add {
            Swatch(
                color = if (customSelected) Color(selected!!) else null,
                selected = customSelected,
                onClick = onPickCustom,
                label = stringResource(R.string.settings_accent_custom)
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        cells.chunked(SWATCHES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell -> Box(modifier = Modifier.weight(1f)) { cell() } }
                repeat(SWATCHES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null
) {
    val zuneColors = LocalZuneColors.current
    val outline = if (zuneColors.isDark) Color.White else Color.Black
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SWATCH_SIZE)
                .then(
                    if (color != null) Modifier.background(color)
                    else Modifier.background(
                        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    )
                )
                .border(if (selected) 3.dp else 0.5.dp, if (selected) outline else outline.copy(alpha = 0.25f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(8.dp).background(outline))
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A Windows Phone text box: a line of type on a plain field, with a cross to empty it. */
@Composable
private fun NameField(
    value: String,
    placeholder: String,
    ink: Color,
    onChange: (String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val draft = value
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .background(if (zuneColors.isDark) Color.White else Color.Black.copy(alpha = 0.06f))
            .border(2.dp, if (zuneColors.isDark) Color.White else zuneColors.textMuted.copy(alpha = 0.5f))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        val fieldInk = if (zuneColors.isDark) Color.Black else ink
        Box(modifier = Modifier.weight(1f)) {
            if (draft.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = fieldInk.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = { onChange(it.take(MAX_NAME_LENGTH)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fieldInk),
                cursorBrush = SolidColor(zuneColors.accentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (draft.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_clear),
                tint = fieldInk,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChange("") }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun ArrowRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
    ) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                Icon(imageVector = trailingIcon, contentDescription = null, tint = LocalZuneColors.current.textMuted)
            }
        )
    }
}

@Composable
private fun LookSlider(
    label: String,
    value: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = zuneColors.accentColor)
        }
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = zuneColors.accentColor,
                activeTrackColor = zuneColors.accentColor,
                inactiveTrackColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                activeTickColor = Color.White.copy(alpha = 0.5f),
                inactiveTickColor = zuneColors.textMuted.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun BackToSettingLink(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.tile_look_back_to_setting),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.accentColor,
        modifier = Modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** The flat rectangular button of the edit bar, filled or outlined. */
@Composable
private fun MetroButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val alpha = if (enabled) 1f else 0.35f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .then(
                if (filled) Modifier.background(zuneColors.accentColor)
                else Modifier.border(2.dp, ink.copy(alpha = alpha))
            )
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
            color = if (filled) Color.White else ink.copy(alpha = alpha)
        )
    }
}

private val PREVIEW_WIDTH = 156.dp
private val SWATCH_SIZE = 34.dp
private const val SWATCHES_PER_ROW = 8
private const val MAX_NAME_LENGTH = 24

/** Above the board and its edit bar; below the icon picker, which opens over this page. */
private const val LOOK_PAGE_Z = 45f
