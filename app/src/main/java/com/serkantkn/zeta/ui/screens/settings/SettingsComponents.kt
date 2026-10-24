package com.serkantkn.zeta.ui.screens.settings

import com.serkantkn.zeta.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.data.model.AccentColor
import com.serkantkn.zeta.data.model.ThemeMode
import com.serkantkn.zeta.ui.components.WpSlider
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.toColor

@Composable
internal fun SettingsLazyColumn(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
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
            // Vista headed its settings' pages in small capitals, white.
            val vista = com.serkantkn.zeta.ui.tv.LocalVista.current
            Text(
                text = if (vista) title.uppercase() else title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (vista) FontWeight.SemiBold else FontWeight.Light,
                    fontSize = if (vista) 17.sp else 18.sp,
                    letterSpacing = if (vista) 0.sp else 1.sp
                ),
                color = if (vista) Color.White else LocalZuneColors.current.textMuted,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
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
    onCustomColorSelected: (Int) -> Unit,
    /** The colour taken from the wallpaper, shown in the middle of the automatic choice. */
    dynamicThemeColor: Int? = null,
    /** The colour "by design" stands for now: the design's own. */
    layoutColor: Color? = null
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
                    // "By design" is the default, so it stays everyone's to go back to.
                    val isAllowed = com.serkantkn.zeta.util.Pro.unlocked || color.isFree

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
                                    context.getString(R.string.pro_only_feature),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    val coloredModifier = when (color) {
                        // The design's colour, ringed: a choice that is not one colour for good.
                        AccentColor.LAYOUT -> baseModifier
                            .background(layoutColor ?: Color.Gray, CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), CircleShape)
                        AccentColor.DYNAMIC -> {
                            baseModifier.background(
                                brush = Brush.sweepGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                ),
                                shape = CircleShape
                            )
                        }
                        AccentColor.CUSTOM -> {
                            baseModifier.background(
                                brush = Brush.sweepGradient(
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
                        if (color == AccentColor.DYNAMIC && dynamicThemeColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 28.dp else 22.dp)
                                    .background(Color(dynamicThemeColor), CircleShape)
                            )
                        } else if (color == AccentColor.CUSTOM) {
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

    com.serkantkn.zeta.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_color_picker),
        confirmButton = {
            com.serkantkn.zeta.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_ok_cap),
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
            com.serkantkn.zeta.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
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
                    text = stringResource(R.string.settings_hue, hue.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                WpSlider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    accent = selectedColor
                )

                Text(
                    text = stringResource(R.string.settings_saturation, (saturation * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                WpSlider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    accent = selectedColor
                )

                Text(
                    text = stringResource(R.string.settings_brightness_pct, (value * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                WpSlider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    accent = selectedColor
                )
            }
        }
    }
}

// Authentic Windows Phone sharp rectangular selection button
@Composable
internal fun SettingPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val borderColor = if (selected) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.5f)
    val bgColor = if (selected) zuneColors.accentColor else Color.Transparent
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onBackground

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .border(2.dp, borderColor, RoundedCornerShape(0.dp))
            .background(bgColor, RoundedCornerShape(0.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                fontSize = 15.sp
            ),
            color = textColor
        )
    }
}

// Authentic Windows Phone rectangular sharp toggle switch (0.dp corners with slider thumb & text label)
@Composable
internal fun WindowsPhoneSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wp_switch_thumb"
    )

    val borderColor = if (checked) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.6f)
    val bgColor = if (checked) zuneColors.accentColor else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onCheckedChange(!checked) }
        )
    ) {
        Text(
            // "on", not "light": the Turkish for both is açık, which is how this went unnoticed.
            text = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            ),
            color = if (checked) zuneColors.accentColor else zuneColors.textMuted
        )

        Box(
            modifier = Modifier
                .width(52.dp)
                .height(26.dp)
                .border(2.dp, borderColor, RoundedCornerShape(0.dp))
                .background(bgColor, RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(width = 16.dp, height = 18.dp)
                    .background(Color.White, RoundedCornerShape(0.dp))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Vista's radio stands before its words.
        if (com.serkantkn.zeta.ui.tv.LocalVista.current) {
            SettingRowContent(
                title = title,
                subtitle = subtitle,
                leading = { com.serkantkn.zeta.ui.tv.VistaRadio(selected) },
                trailing = {}
            )
        } else SettingRowContent(
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
    ) {
        // Vista's check box stands before its words.
        if (com.serkantkn.zeta.ui.tv.LocalVista.current) {
            SettingRowContent(
                title = title,
                subtitle = subtitle,
                leading = { com.serkantkn.zeta.ui.tv.VistaCheck(checked) },
                trailing = {}
            )
        } else SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                WindowsPhoneSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        )
    }
}

@Composable
internal fun SystemSettingRow(
    title: String,
    subtitle: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        SettingRowContent(
            title = title,
            subtitle = subtitle,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF27AE60), CircleShape)
                        )
                    }
                    // Vista's menus had no arrows: a line is a line, and the glass says which.
                    if (!com.serkantkn.zeta.ui.tv.LocalVista.current) Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = LocalZuneColors.current.textMuted
                    )
                }
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
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    color = if (com.serkantkn.zeta.ui.tv.LocalVista.current) com.serkantkn.zeta.ui.tv.Vista.Category
                    else LocalZuneColors.current.textMuted,
                    maxLines = 4
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        trailing()
    }
}

@Composable
internal fun SelectionDot(selected: Boolean) {
    val zuneColors = LocalZuneColors.current
    val color = if (selected) zuneColors.accentColor else Color.Transparent
    val borderColor = if (selected) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .size(18.dp)
            .border(2.dp, borderColor, CircleShape)
            .background(color = color, shape = CircleShape)
    )
}

// Background-less container
@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth()
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

/**
 * What the accessibility service does, said before the user is sent to turn it on, with a choice
 * to accept or not. Google Play asks this of any app whose service is not an aid for a disability,
 * and it is only fair besides: the system's own warning about the service says far less.
 */
@Composable
internal fun AccessibilityDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    body: String = stringResource(R.string.a11y_disclosure_body)
) {
    com.serkantkn.zeta.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.a11y_disclosure_title),
        confirmButton = {
            com.serkantkn.zeta.ui.components.ZuneDialogButton(
                text = stringResource(R.string.a11y_disclosure_accept),
                onClick = { dismissWithAnim(onAccept) },
                borderColor = LocalZuneColors.current.accentColor
            )
        },
        dismissButton = {
            com.serkantkn.zeta.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim() },
                borderColor = LocalZuneColors.current.textMuted
            )
        }
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            // The dialog is dark in either theme, and scrolls its content itself.
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

