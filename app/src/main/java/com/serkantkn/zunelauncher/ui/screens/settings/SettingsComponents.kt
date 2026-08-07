package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.toColor

@Composable
internal fun SettingsLazyColumn(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        content()
    }
}

@Composable
internal fun SettingGroup(
    title: String,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                ),
                color = LocalZuneColors.current.textMuted,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
internal fun ThemeChoiceRow(
    selectedMode: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.LIGHT -> "açık"
                ThemeMode.DARK -> "koyu"
                ThemeMode.SYSTEM -> "sistem"
            }
            SettingPill(
                label = label,
                selected = selectedMode == mode,
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun AccentColorChoiceRow(
    selectedColor: AccentColor,
    customThemeColor: Int?,
    onSelected: (AccentColor) -> Unit,
    onCustomColorSelected: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        CustomColorPickerDialog(
            initialColor = customThemeColor ?: 0xFFD81B60.toInt(),
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                onCustomColorSelected(it)
                showColorPicker = false
            }
        )
    }

    val columns = 5
    val colorEntries = AccentColor.entries.toTypedArray()
    val rows = colorEntries.toList().chunked(columns)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFadingEdge(fadeWidth = 48.dp)
            ) {
                rowItems.forEach { color ->
                    val isSelected = selectedColor == color
                    val isAllowed = com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM ||
                        color in listOf(AccentColor.RED, AccentColor.BLUE, AccentColor.GREEN)

                    val baseModifier = Modifier
                        .size(if (isSelected) 48.dp else 40.dp)
                        .clickable {
                            if (isAllowed) {
                                if (color == AccentColor.CUSTOM) {
                                    showColorPicker = true
                                } else {
                                    onSelected(color)
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Bu özellik sadece Z Launcher Pro'da geçerlidir.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    val coloredModifier = when (color) {
                        AccentColor.DYNAMIC -> {
                            baseModifier.background(
                                brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                ),
                                shape = CircleShape
                            )
                        }
                        AccentColor.CUSTOM -> {
                            baseModifier.background(
                                brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                ),
                                shape = CircleShape
                            )
                        }
                        else -> {
                            baseModifier.background(color = color.toColor(), shape = CircleShape)
                        }
                    }

                    Box(
                        modifier = coloredModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        if (color == AccentColor.CUSTOM) {
                            val customColor = if (customThemeColor != null) Color(customThemeColor) else Color.Gray
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 28.dp else 22.dp)
                                    .background(customColor, CircleShape)
                            )
                        } else if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.background, CircleShape)
                            )
                        }
                    }
                }

                if (rowItems.size < columns) {
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val hsv = remember {
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsvArr)
        hsvArr
    }

    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }

    val selectedColor = remember(hue, saturation, value) {
        Color.hsv(hue, saturation, value)
    }

    com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = "renk seçici",
        confirmButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = "Tamam",
                onClick = {
                    dismissWithAnim {
                        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                        onColorSelected(argb)
                    }
                },
                borderColor = selectedColor
            )
        },
        dismissButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = "İptal",
                onClick = { dismissWithAnim() },
                borderColor = LocalZuneColors.current.textMuted
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(selectedColor, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "renk özü (hue): ${hue.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    )
                )

                Text(
                    text = "doygunluk (saturation): ${(saturation * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    )
                )

                Text(
                    text = "parlaklık (brightness): ${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor
                    )
                )
            }
        }
    }
}

@Composable
internal fun SettingPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val height by animateDpAsState(
        targetValue = if (selected) 58.dp else 52.dp,
        label = "setting_pill_height"
    )

    GlassPanel(
        modifier = modifier
            .height(height)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        selected = selected
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Light
                ),
                color = if (selected) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
internal fun SettingChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassPanel(
        selected = selected,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                SelectionDot(selected = selected)
            }
        )
    }
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassPanel(selected = checked) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalZuneColors.current.accentColor,
                        checkedTrackColor = LocalZuneColors.current.accentColor.copy(alpha = 0.42f)
                    )
                )
            }
        )
    }
}

@Composable
internal fun SystemSettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LocalZuneColors.current.textMuted
                )
            }
        )
    }
}

@Composable
internal fun SettingRowContent(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        trailing()
    }
}

@Composable
internal fun SelectionDot(selected: Boolean) {
    val size by animateDpAsState(
        targetValue = if (selected) 14.dp else 8.dp,
        label = "selection_dot_size"
    )
    val color = if (selected) {
        LocalZuneColors.current.accentColor
    } else {
        LocalZuneColors.current.textDim.copy(alpha = 0.45f)
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(color = color, shape = CircleShape)
    )
}

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val tint = if (selected) {
        zuneColors.accentColor.copy(alpha = 0.18f)
    } else if (zuneColors.isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tint)
    ) {
        content()
    }
}

private fun Modifier.horizontalFadingEdge(fadeWidth: androidx.compose.ui.unit.Dp = 48.dp) = this
    .graphicsLayer { alpha = 0.99F }
    .drawWithContent {
        drawContent()
        val width = size.width
        val fadeWidthPx = fadeWidth.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = width - fadeWidthPx,
                endX = width
            ),
            blendMode = BlendMode.DstIn
        )
    }
