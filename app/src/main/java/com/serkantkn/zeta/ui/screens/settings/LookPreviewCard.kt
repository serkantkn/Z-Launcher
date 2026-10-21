package com.serkantkn.zeta.ui.screens.settings

import com.serkantkn.zeta.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.data.model.AccentColor
import com.serkantkn.zeta.data.model.HomeScreenLayout
import com.serkantkn.zeta.data.model.HubBackgroundMode
import com.serkantkn.zeta.data.model.ThemeMode
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.data.model.TileInk
import com.serkantkn.zeta.data.model.tileInkIsDark
import com.serkantkn.zeta.ui.components.BackgroundMode
import com.serkantkn.zeta.ui.components.ZuneBackground
import com.serkantkn.zeta.ui.components.frostedBackdrop
import com.serkantkn.zeta.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.toColor

@Composable
fun LookPreviewCard(
    themeMode: ThemeMode,
    accentColor: AccentColor,
    customThemeColor: Int?,
    dynamicThemeColor: Int?,
    solidBackgroundEnabled: Boolean,
    customWallpaperPath: String?,
    tileCornerStyle: TileCornerStyle,
    tileSpacing: Int,
    /** 0-100, the same scale the transparency slider works on. */
    tileOpacity: Int = com.serkantkn.zeta.data.datastore.SettingsDataStore.DEFAULT_TILE_OPACITY,
    /** 0-100, how frosted the wallpaper behind the tiles is; 0 leaves it sharp. */
    tileBlur: Int = 0,
    tileInk: TileInk = TileInk.AUTO,
    hubBackgroundOpacity: Float,
    hubBackgroundMode: HubBackgroundMode,
    homeScreenLayout: HomeScreenLayout = HomeScreenLayout.ZUNE,
    zuneGlow: com.serkantkn.zeta.data.model.ZuneGlow = com.serkantkn.zeta.data.model.ZuneGlow.OFF,
    modifier: Modifier = Modifier
) {
    val isDark = themeMode == ThemeMode.DARK
    val textColor = if (isDark) Color.White else Color.Black
    val textMuted = textColor.copy(alpha = 0.6f)

    val effectiveAccentColor = remember(accentColor, customThemeColor, dynamicThemeColor) {
        if (accentColor == AccentColor.CUSTOM && customThemeColor != null) {
            Color(customThemeColor)
        } else if (accentColor == AccentColor.DYNAMIC && dynamicThemeColor != null) {
            Color(dynamicThemeColor)
        } else {
            accentColor.toColor()
        }
    }

    val animatedAccentColor by animateColorAsState(
        targetValue = effectiveAccentColor,
        label = "preview_accent"
    )

    // The tiles' own ink, worked out exactly the way the Start board works it out, so the
    // preview shows what the setting actually does rather than an approximation of it.
    val tileTextColor = if (
        tileInkIsDark(
            ink = tileInk,
            isDarkTheme = isDark,
            opacity = tileOpacity / 100f,
            fillLuminance = effectiveAccentColor.luminance()
        )
    ) Color.Black else Color.White

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (tileCornerStyle == TileCornerStyle.SHARP) 0.dp else 8.dp,
        label = "preview_corner_radius"
    )

    val animatedTileSpacing by animateDpAsState(
        targetValue = (tileSpacing * 0.75f).dp,
        label = "preview_tile_spacing"
    )

    val tileShape = RoundedCornerShape(animatedCornerRadius)
    val tileStrokeColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.55f)
    val tileFillAlpha = (tileOpacity / 100f).coerceIn(0f, 1f)

    val glowing = homeScreenLayout == HomeScreenLayout.ZUNE && zuneGlow != com.serkantkn.zeta.data.model.ZuneGlow.OFF
    val effectiveBgMode = when {
        solidBackgroundEnabled -> BackgroundMode.SOLID
        glowing -> BackgroundMode.GLOW
        else -> BackgroundMode.WALLPAPER
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_preview),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when {
                    homeScreenLayout != HomeScreenLayout.WINDOWS_PHONE -> stringResource(R.string.settings_layout_zune)
                    LocalIsWideScreen.current -> stringResource(R.string.settings_layout_win8)
                    else -> stringResource(R.string.settings_layout_wp)
                },
                style = MaterialTheme.typography.labelSmall,
                color = animatedAccentColor
            )
        }

        // Preview Device Frame / Screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(if (isDark) Color(0xFF101010) else Color(0xFFF2F2F2))
        ) {
            // Background Layer inside Preview
            ZuneBackground(
                mode = effectiveBgMode,
                accentColor = animatedAccentColor,
                customWallpaperPathOverride = customWallpaperPath,
                forceModeOverride = effectiveBgMode,
                tileBlur = tileBlur,
                glow = zuneGlow
            ) {
                // Start with frosted tiles shows its wallpaper bare, and so does its preview; the
                // glow is never shaded.
                if (!solidBackgroundEnabled && !glowing && tileBlur == 0) {
                    ZuneWallpaperOverlay(alpha = 1f, isHubOverlay = false)
                }

                if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE) {
                    // Windows Phone Start Screen Grid Preview
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // 8-Column Mode Preview
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(animatedTileSpacing)
                        ) {
                            // Row 1: 4 Mini Tiles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(animatedTileSpacing)
                            ) {
                                MiniTile(
                                    icon = Icons.Default.Call,
                                    label = null,
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                                MiniTile(
                                    icon = com.serkantkn.zeta.ui.components.WpMessagingGlyph,
                                    label = null,
                                    badge = "2",
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                                MiniTile(
                                    icon = Icons.Default.People,
                                    label = null,
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                                MiniTile(
                                    icon = Icons.Default.Language,
                                    label = null,
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                            }

                            // Row 2: 1 Medium Tile + 2 Mini Tiles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(animatedTileSpacing)
                            ) {
                                MiniTile(
                                    icon = Icons.Default.Image,
                                    label = stringResource(R.string.pictures_hub),
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(2f).height(36.dp)
                                )
                                MiniTile(
                                    icon = Icons.Default.Folder,
                                    label = null,
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                                MiniTile(
                                    icon = Icons.Default.Settings,
                                    label = null,
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileOpacity = tileFillAlpha,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = tileTextColor,
                                    modifier = Modifier.weight(1f).height(36.dp)
                                )
                            }

                            // Row 3: 1 Full Wide Tile
                            MiniTile(
                                icon = Icons.Default.MusicNote,
                                label = stringResource(R.string.music_hub),
                                badge = null,
                                accentColor = animatedAccentColor,
                                tileOpacity = tileFillAlpha,
                                tileShape = tileShape,
                                strokeColor = tileStrokeColor,
                                textColor = textColor,
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            )
                        }
                    }
                } else {
                    // Classic Zune Layout Preview (Left Hub List + Right Side Favorites)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Clock & Hub Titles List
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "12:45",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Light,
                                    color = textColor,
                                    lineHeight = 24.sp,
                                    letterSpacing = (-1).sp
                                )
                                Text(
                                    text = stringResource(R.string.settings_preview_date),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = textMuted
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = stringResource(R.string.zune_row_music), fontSize = 16.sp, fontWeight = FontWeight.Light, color = textColor)
                                Text(text = stringResource(R.string.people_hub), fontSize = 16.sp, fontWeight = FontWeight.Light, color = textColor)
                                Text(text = stringResource(R.string.pictures_hub), fontSize = 16.sp, fontWeight = FontWeight.Light, color = textColor)
                                Text(text = stringResource(R.string.hub_messaging), fontSize = 16.sp, fontWeight = FontWeight.Light, color = textColor)
                            }
                        }

                        // Right: Side Favorite Tiles
                        Column(
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(animatedTileSpacing, Alignment.CenterVertically)
                        ) {
                            MiniTile(
                                icon = Icons.Default.Call,
                                label = null,
                                badge = null,
                                accentColor = animatedAccentColor,
                                tileOpacity = tileFillAlpha,
                                tileShape = tileShape,
                                strokeColor = tileStrokeColor,
                                textColor = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            )
                            MiniTile(
                                icon = com.serkantkn.zeta.ui.components.WpMessagingGlyph,
                                label = null,
                                badge = "2",
                                accentColor = animatedAccentColor,
                                tileOpacity = tileFillAlpha,
                                tileShape = tileShape,
                                strokeColor = tileStrokeColor,
                                textColor = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            )
                            MiniTile(
                                icon = Icons.Default.CameraAlt,
                                label = null,
                                badge = null,
                                accentColor = animatedAccentColor,
                                tileOpacity = tileFillAlpha,
                                tileShape = tileShape,
                                strokeColor = tileStrokeColor,
                                textColor = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            )
                            MiniTile(
                                icon = Icons.Default.Settings,
                                label = null,
                                badge = null,
                                accentColor = animatedAccentColor,
                                tileOpacity = tileFillAlpha,
                                tileShape = tileShape,
                                strokeColor = tileStrokeColor,
                                textColor = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTile(
    icon: ImageVector,
    label: String?,
    badge: String?,
    accentColor: Color,
    tileShape: RoundedCornerShape,
    strokeColor: Color,
    textColor: Color,
    tileOpacity: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(tileShape)
            .then(if (tileOpacity < 1f) Modifier.frostedBackdrop() else Modifier)
            .background(accentColor.copy(alpha = tileOpacity))
            .border(0.5.dp, strokeColor, tileShape)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.Center)
        )

        badge?.let { count ->
            Text(
                text = count,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        label?.let { text ->
            Text(
                text = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
