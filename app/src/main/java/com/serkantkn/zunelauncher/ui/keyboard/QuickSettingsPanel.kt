package com.serkantkn.zunelauncher.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.datastore.KeyboardDataStore
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.OneHandedMode
import com.serkantkn.zunelauncher.util.KeyboardLayouts

/** Everything the quick panel can change, so the service passes one object instead of ten flags. */
data class QuickSettingsState(
    val numberRow: Boolean,
    val suggestions: Boolean,
    val autoCorrect: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
    val split: Boolean,
    val splitAvailable: Boolean,
    val oneHanded: OneHandedMode,
    val oneHandedAvailable: Boolean,
    val heightScale: Float,
    val bottomPadding: Int,
    val language: KeyboardLanguage,
    val languages: List<KeyboardLanguage>
)

/**
 * The settings the keyboard itself can change, without leaving the app being typed in: the
 * switches people flip most often, plus a link into the launcher's full keyboard settings.
 */
@Composable
fun QuickSettingsPanel(
    state: QuickSettingsState,
    palette: KeyboardPalette,
    height: Dp,
    rowHeight: Dp,
    onNumberRow: (Boolean) -> Unit,
    onSuggestions: (Boolean) -> Unit,
    onAutoCorrect: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onSplit: (Boolean) -> Unit,
    onOneHanded: (OneHandedMode) -> Unit,
    onHeightScale: (Float) -> Unit,
    onBottomPadding: (Int) -> Unit,
    onLanguage: (KeyboardLanguage) -> Unit,
    onOpenAllSettings: () -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(palette.board)
            .padding(bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.keyboard_quick_title),
                color = palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.keyboard_quick_all_settings),
                color = palette.accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenAllSettings
                )
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (state.languages.size > 1) {
                QuickSegments(
                    title = stringResource(R.string.keyboard_quick_language),
                    options = state.languages.map { it.displayLabel },
                    selected = state.languages.indexOf(state.language),
                    palette = palette,
                    onSelect = { index -> state.languages.getOrNull(index)?.let(onLanguage) }
                )
            }
            QuickSwitchRow(
                title = stringResource(R.string.keyboard_number_row_title),
                checked = state.numberRow,
                palette = palette,
                onCheckedChange = onNumberRow
            )
            QuickSwitchRow(
                title = stringResource(R.string.keyboard_suggestions_title),
                checked = state.suggestions,
                palette = palette,
                onCheckedChange = onSuggestions
            )
            QuickSwitchRow(
                title = stringResource(R.string.keyboard_autocorrect_title),
                checked = state.autoCorrect,
                palette = palette,
                onCheckedChange = onAutoCorrect
            )
            QuickSwitchRow(
                title = stringResource(R.string.keyboard_sound_title),
                checked = state.sound,
                palette = palette,
                onCheckedChange = onSound
            )
            QuickSwitchRow(
                title = stringResource(R.string.keyboard_vibration_title),
                checked = state.vibration,
                palette = palette,
                onCheckedChange = onVibration
            )
            if (state.splitAvailable) {
                QuickSwitchRow(
                    title = stringResource(R.string.keyboard_split_title),
                    checked = state.split,
                    palette = palette,
                    onCheckedChange = onSplit
                )
            }
            QuickSegments(
                title = stringResource(R.string.keyboard_height_group),
                options = listOf(
                    stringResource(R.string.keyboard_height_small),
                    stringResource(R.string.keyboard_height_medium),
                    stringResource(R.string.keyboard_height_large)
                ),
                selected = KeyboardDataStore.HEIGHT_CHOICES.indexOfFirst {
                    kotlin.math.abs(it - state.heightScale) < 0.01f
                },
                palette = palette,
                onSelect = { index ->
                    KeyboardDataStore.HEIGHT_CHOICES.getOrNull(index)?.let(onHeightScale)
                }
            )
            QuickSegments(
                title = stringResource(R.string.keyboard_bottom_padding_group),
                options = listOf(
                    stringResource(R.string.keyboard_bottom_padding_none),
                    stringResource(R.string.keyboard_bottom_padding_small),
                    stringResource(R.string.keyboard_bottom_padding_medium),
                    stringResource(R.string.keyboard_bottom_padding_large)
                ),
                selected = KeyboardDataStore.BOTTOM_PADDING_CHOICES.indexOf(state.bottomPadding),
                palette = palette,
                onSelect = { index ->
                    KeyboardDataStore.BOTTOM_PADDING_CHOICES.getOrNull(index)?.let(onBottomPadding)
                }
            )
            if (state.oneHandedAvailable) {
                QuickSegments(
                    title = stringResource(R.string.keyboard_one_handed_group),
                    options = listOf(
                        stringResource(R.string.keyboard_one_handed_off),
                        stringResource(R.string.keyboard_one_handed_left),
                        stringResource(R.string.keyboard_one_handed_right)
                    ),
                    selected = OneHandedMode.entries.indexOf(state.oneHanded),
                    palette = palette,
                    onSelect = { index -> OneHandedMode.entries.getOrNull(index)?.let(onOneHanded) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
            PanelKey(palette = palette, modifier = Modifier.weight(1.5f), onClick = onClose) {
                Text(text = KeyboardLayouts.LETTERS_LABEL, color = palette.text, fontSize = 13.sp)
            }
            PanelKey(palette = palette, modifier = Modifier.weight(5f), onClick = onClose) {
                Text(
                    text = stringResource(R.string.keyboard_panel_back),
                    color = palette.muted,
                    fontSize = 13.sp
                )
            }
            PanelKey(palette = palette, modifier = Modifier.weight(1.5f), onClick = onBackspace) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = palette.text
                )
            }
        }
    }
}

@Composable
private fun QuickSwitchRow(
    title: String,
    checked: Boolean,
    palette: KeyboardPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.key)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        QuickSwitch(checked = checked, palette = palette)
    }
}

/** The flat Windows Phone toggle: an empty rectangle that fills with the accent when it is on. */
@Composable
private fun QuickSwitch(checked: Boolean, palette: KeyboardPalette) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(22.dp)
            .border(2.dp, if (checked) palette.accent else palette.muted)
            .background(if (checked) palette.accent else Color.Transparent),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(width = 12.dp, height = 14.dp)
                .background(if (checked) Color.White else palette.muted)
        )
    }
}

@Composable
private fun QuickSegments(
    title: String,
    options: List<String>,
    selected: Int,
    palette: KeyboardPalette,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(palette.key).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text = title, color = palette.text, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .background(if (isSelected) palette.accent else Color.Transparent)
                        .border(1.dp, if (isSelected) palette.accent else palette.muted.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else palette.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
