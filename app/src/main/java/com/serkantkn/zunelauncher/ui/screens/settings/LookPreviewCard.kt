package com.serkantkn.zunelauncher.ui.screens.settings

import com.serkantkn.zunelauncher.R
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
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubBackgroundMode
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.BackgroundMode
import com.serkantkn.zunelauncher.ui.components.ZuneBackground
import com.serkantkn.zunelauncher.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zunelauncher.ui.theme.toColor

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
    tileColumns: Int = 4,
    hubBackgroundOpacity: Float,
    hubBackgroundMode: HubBackgroundMode,
    homeScreenLayout: HomeScreenLayout = HomeScreenLayout.ZUNE,
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

    val effectiveBgMode = when {
        solidBackgroundEnabled -> BackgroundMode.SOLID
        customWallpaperPath != null -> BackgroundMode.WALLPAPER
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
                text = if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE) "Windows Phone" else "Zune",
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
                forceModeOverride = effectiveBgMode
            ) {
                if (!solidBackgroundEnabled) {
                    ZuneWallpaperOverlay(alpha = 1f, isHubOverlay = false)
                }

                if (homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE) {
                    // Windows Phone Start Screen Grid Preview
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (tileColumns == 8) {
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
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    )
                                    MiniTile(
                                        icon = Icons.Default.Email,
                                        label = null,
                                        badge = "2",
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    )
                                    MiniTile(
                                        icon = Icons.Default.People,
                                        label = null,
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    )
                                    MiniTile(
                                        icon = Icons.Default.Language,
                                        label = null,
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
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
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier.weight(2f).height(36.dp)
                                    )
                                    MiniTile(
                                        icon = Icons.Default.Folder,
                                        label = null,
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    )
                                    MiniTile(
                                        icon = Icons.Default.Settings,
                                        label = null,
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    )
                                }

                                // Row 3: 1 Full Wide Tile
                                MiniTile(
                                    icon = Icons.Default.MusicNote,
                                    label = stringResource(R.string.music_hub),
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = textColor,
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                )
                            }
                        } else {
                            // 4-Column Mode Preview
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(animatedTileSpacing)
                            ) {
                                // Row 1: 2 Medium Hub Tiles (Phone & Messaging)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(animatedTileSpacing)
                                ) {
                                    MiniTile(
                                        icon = Icons.Default.Call,
                                        label = stringResource(R.string.hub_phone),
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    )

                                    MiniTile(
                                        icon = Icons.Default.Email,
                                        label = stringResource(R.string.hub_messaging),
                                        badge = "2",
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    )
                                }

                                // Row 2: 1 Wide Tile (Photos)
                                MiniTile(
                                    icon = Icons.Default.Image,
                                    label = stringResource(R.string.pictures_hub),
                                    badge = null,
                                    accentColor = animatedAccentColor,
                                    tileShape = tileShape,
                                    strokeColor = tileStrokeColor,
                                    textColor = textColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                )

                                // Row 3: 1 Medium Tile (Music) + 2 Small Tiles (Camera & Settings)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(animatedTileSpacing)
                                ) {
                                    MiniTile(
                                        icon = Icons.Default.MusicNote,
                                        label = stringResource(R.string.music_hub),
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier
                                            .weight(2f)
                                            .height(44.dp)
                                    )

                                    MiniTile(
                                        icon = Icons.Default.CameraAlt,
                                        label = null,
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    )

                                    MiniTile(
                                        icon = Icons.Default.Settings,
                                        label = null,
                                        badge = null,
                                        accentColor = animatedAccentColor,
                                        tileShape = tileShape,
                                        strokeColor = tileStrokeColor,
                                        textColor = textColor,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    )
                                }
                            }
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
                                Text(text = stringResource(R.string.music_hub), fontSize = 16.sp, fontWeight = FontWeight.Light, color = textColor)
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
                                tileShape = tileShape,
                                strokeColor = tileStrokeColor,
                                textColor = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            )
                            MiniTile(
                                icon = Icons.Default.Email,
                                label = null,
                                badge = "2",
                                accentColor = animatedAccentColor,
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(tileShape)
            .background(accentColor.copy(alpha = 0.45f))
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
