package com.serkantkn.zunelauncher.ui.screens.settings

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import com.serkantkn.zunelauncher.di.appContainer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * Windows Phone / Metro style full-screen app notification permission selector.
 *
 * Design references:
 *  - WP 8.1 Settings → Notifications+Actions → Choose apps
 *  - Large lowercase page title, small muted breadcrumb header
 *  - Each app row uses WP-style rectangular toggle switch (açık/kapalı)
 *  - Sharp-cornered stringResource(R.string.common_done) (done) button at the bottom bar
 *  - Staggered entrance animation per row
 */
@Composable
fun AppNotificationFilterScreen(
    disabledApps: Set<String>,
    installedApps: List<AppInfo>,
    onClose: () -> Unit,
    onSave: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val appRepository = remember(context) { context.appContainer.appRepository }

    // Local mutable copy — checked = allowed (NOT in disabled set)
    val selectedDisabledSet = remember(disabledApps) {
        mutableStateOf(disabledApps.toMutableSet())
    }

    BackHandler { onClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── Top Header Area (WP style) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 32.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                // Small breadcrumb: stringResource(R.string.common_settings)
                Text(
                    text = stringResource(R.string.common_settings),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f)
                    else Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Giant page title (WP oversized lowercase)
                Text(
                    text = stringResource(R.string.settings_notification_permissions),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 46.sp,
                        letterSpacing = (-1).sp,
                        lineHeight = 50.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Subtitle instruction
                Text(
                    text = stringResource(R.string.settings_notification_permissions_help),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    ),
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // ─── App List ───
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal,
                    bottom = 16.dp
                )
            ) {
                itemsIndexed(
                    items = installedApps,
                    key = { _, app -> app.packageName }
                ) { index, app ->
                    val isAllowed = !selectedDisabledSet.value.contains(app.packageName)
                    val iconDrawable = remember(app.packageName) {
                        appRepository.getAppIcon(app.packageName)
                    }

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val currentSet =
                                        selectedDisabledSet.value.toMutableSet()
                                    if (isAllowed) currentSet.add(app.packageName)
                                    else currentSet.remove(app.packageName)
                                    selectedDisabledSet.value = currentSet
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // App icon (square with very slight rounding — WP tile style)
                            if (iconDrawable != null) {
                                val bitmap =
                                    remember(iconDrawable) { iconDrawable.toBitmap() }
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(zuneColors.accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // App name
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // WP-style rectangular toggle switch
                            WpNotificationToggle(
                                checked = isAllowed,
                                onCheckedChange = { checked ->
                                    val currentSet =
                                        selectedDisabledSet.value.toMutableSet()
                                    if (checked) currentSet.remove(app.packageName)
                                    else currentSet.add(app.packageName)
                                    selectedDisabledSet.value = currentSet
                                }
                            )
                        }

                        // Thin separator
                        HorizontalDivider(
                            color = if (zuneColors.isDark) Color(0xFF1A1A1A)
                            else Color(0xFFE8E8E8),
                            thickness = 0.5.dp
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
                    // bitti (done) — WP sharp rectangular button filled with accent
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(2.dp, zuneColors.accentColor, RoundedCornerShape(0.dp))
                            .background(zuneColors.accentColor, RoundedCornerShape(0.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSave(selectedDisabledSet.value)
                                onClose()
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.common_done),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                    }

                    // iptal (cancel) — WP sharp rectangular button outlined
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
                                indication = null
                            ) { onClose() }
                    ) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

/**
 * Authentic Windows Phone rectangular toggle switch for notification rows.
 * Same visual style as [WindowsPhoneSwitch] in SettingsComponents.
 */
@Composable
private fun WpNotificationToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wp_notif_toggle"
    )

    val borderColor =
        if (checked) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.6f)
    val bgColor = if (checked) zuneColors.accentColor else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onCheckedChange(!checked) }
        )
    ) {
        Text(
            text = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
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
