package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubBackgroundMode
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.model.TileInk
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.TileIconStyle
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * The look tab, split up.
 *
 * Everything here used to be one scroll: theme, two wallpapers, accent colour, layout, icons,
 * columns, tile look and tile animation, one after another. That is a lot to hunt through when you
 * came to change one thing, so each heading is now a page of its own and the tab itself is a list
 * of them — which is how Windows Phone arranged its own settings.
 */
internal enum class LookPage(@StringRes val titleRes: Int) {
    THEME(R.string.theme_setting),
    WALLPAPER(R.string.settings_wallpaper),
    HUB_BACKGROUND(R.string.settings_hub_background),
    START_SCREEN(R.string.settings_look_start),
    TILES(R.string.settings_look_tiles),
    ICONS(R.string.settings_tile_icons),
    TEXT_AND_MOTION(R.string.settings_look_text_motion)
}

/**
 * One of the look pages, opened out of the tab on the same door hinge the other standalone
 * settings screens use.
 */
@Composable
internal fun LookSubSettingsScreen(
    page: LookPage,
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current

    BackHandler { onClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        top = 24.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                // The breadcrumb is the way back, as on the other standalone settings screens.
                Text(
                    text = stringResource(R.string.settings_breadcrumb_look),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f)
                    else Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(bottom = 4.dp)
                )

                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 42.sp,
                        letterSpacing = (-1).sp,
                        lineHeight = 46.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                    .navigationBarsPadding()
            ) {
                when (page) {
                    LookPage.THEME -> ThemeGroups(viewModel)
                    LookPage.WALLPAPER -> WallpaperGroups(viewModel)
                    LookPage.HUB_BACKGROUND -> HubBackgroundGroups(viewModel)
                    LookPage.START_SCREEN -> StartScreenGroups(viewModel)
                    LookPage.TILES -> TileGroups(viewModel)
                    LookPage.ICONS -> IconGroups(viewModel)
                    LookPage.TEXT_AND_MOTION -> TextAndMotionGroups(viewModel)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// THEME
// ════════════════════════════════════════════════════════════

@Composable
private fun ThemeGroups(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val customThemeColor by viewModel.customThemeColor.collectAsState()

    SettingGroup(title = stringResource(R.string.theme_setting)) {
        ThemeChoiceRow(selectedMode = themeMode, onSelected = viewModel::setThemeMode)
    }

    Spacer(modifier = Modifier.height(24.dp))

    SettingGroup(title = stringResource(R.string.settings_accent_color)) {
        AccentColorChoiceRow(
            selectedColor = accentColor,
            customThemeColor = customThemeColor,
            onSelected = viewModel::setAccentColor,
            onCustomColorSelected = { colorInt ->
                viewModel.setCustomThemeColor(colorInt)
                viewModel.setAccentColor(AccentColor.CUSTOM)
            }
        )
    }
}

// ════════════════════════════════════════════════════════════
// WALLPAPER
// ════════════════════════════════════════════════════════════

@Composable
private fun WallpaperGroups(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val customWallpaperPath by viewModel.customWallpaperPath.collectAsState()
    val solidBackgroundEnabled by viewModel.solidBackgroundEnabled.collectAsState()

    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) cropUri = uri }
    )

    fun pickWallpaper() {
        if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
            wallpaperPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.settings_wallpaper_pro_only),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    CropDialog(uri = cropUri, onApply = { viewModel.saveCroppedWallpaper(it) }) { cropUri = null }

    SettingGroup(title = stringResource(R.string.settings_wallpaper)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.theme_system),
                subtitle = stringResource(R.string.settings_wallpaper_system_sub),
                selected = !solidBackgroundEnabled && customWallpaperPath == null,
                onClick = {
                    viewModel.setSolidBackgroundEnabled(false)
                    viewModel.clearCustomWallpaper()
                }
            )

            SettingChoiceRow(
                title = stringResource(R.string.settings_wallpaper_custom),
                subtitle = if (customWallpaperPath != null) {
                    stringResource(R.string.settings_wallpaper_custom_active)
                } else {
                    stringResource(R.string.settings_wallpaper_none)
                },
                selected = !solidBackgroundEnabled && customWallpaperPath != null,
                onClick = {
                    viewModel.setSolidBackgroundEnabled(false)
                    if (customWallpaperPath == null) pickWallpaper()
                }
            )

            if (!solidBackgroundEnabled && customWallpaperPath != null) {
                ChangeOrClearRow(
                    onChange = { pickWallpaper() },
                    onClear = { viewModel.clearCustomWallpaper() }
                )
            }

            SettingChoiceRow(
                title = stringResource(R.string.settings_solid_background),
                subtitle = stringResource(R.string.settings_solid_background_sub),
                selected = solidBackgroundEnabled,
                onClick = { viewModel.setSolidBackgroundEnabled(true) }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// HUB BACKGROUND
// ════════════════════════════════════════════════════════════

@Composable
private fun HubBackgroundGroups(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val customHubWallpaperPath by viewModel.customHubWallpaperPath.collectAsState()
    val hubBackgroundMode by viewModel.hubBackgroundMode.collectAsState()
    val hubBackgroundOpacity by viewModel.hubBackgroundOpacity.collectAsState()
    val animatedSky by viewModel.weatherAnimatedSky.collectAsState()

    var hubCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val hubWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) hubCropUri = uri }
    )

    fun pickHubWallpaper() {
        if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
            hubWallpaperPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.settings_wallpaper_pro_only),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    CropDialog(uri = hubCropUri, onApply = { viewModel.saveCroppedHubWallpaper(it) }) {
        hubCropUri = null
    }

    SettingGroup(title = stringResource(R.string.settings_hub_background)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.settings_hub_background_same),
                subtitle = stringResource(R.string.settings_hub_background_same_sub),
                selected = hubBackgroundMode == HubBackgroundMode.MATCH_LAUNCHER,
                onClick = { viewModel.setHubBackgroundMode(HubBackgroundMode.MATCH_LAUNCHER) }
            )

            SettingChoiceRow(
                title = stringResource(R.string.settings_wallpaper_custom),
                subtitle = if (customHubWallpaperPath != null) {
                    stringResource(R.string.settings_hub_wallpaper_active)
                } else {
                    stringResource(R.string.settings_wallpaper_none)
                },
                selected = hubBackgroundMode == HubBackgroundMode.CUSTOM,
                onClick = {
                    if (customHubWallpaperPath == null) {
                        pickHubWallpaper()
                    } else {
                        viewModel.setHubBackgroundMode(HubBackgroundMode.CUSTOM)
                    }
                }
            )

            if (hubBackgroundMode == HubBackgroundMode.CUSTOM && customHubWallpaperPath != null) {
                ChangeOrClearRow(
                    onChange = { pickHubWallpaper() },
                    onClear = {
                        viewModel.clearCustomHubWallpaper()
                        viewModel.setHubBackgroundMode(HubBackgroundMode.MATCH_LAUNCHER)
                    }
                )
            }

            SettingChoiceRow(
                title = stringResource(R.string.theme_system),
                subtitle = stringResource(R.string.settings_wallpaper_system_sub),
                selected = hubBackgroundMode == HubBackgroundMode.SYSTEM,
                onClick = { viewModel.setHubBackgroundMode(HubBackgroundMode.SYSTEM) }
            )

            SettingChoiceRow(
                title = stringResource(R.string.settings_solid_background),
                subtitle = stringResource(R.string.settings_solid_background_sub),
                selected = hubBackgroundMode == HubBackgroundMode.SOLID,
                onClick = { viewModel.setHubBackgroundMode(HubBackgroundMode.SOLID) }
            )

            // Nothing to dim when the background is a flat colour.
            if (hubBackgroundMode != HubBackgroundMode.SOLID) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingSlider(
                    label = stringResource(R.string.settings_dim_opacity),
                    value = "%${(hubBackgroundOpacity * 100).toInt()}",
                    sliderValue = hubBackgroundOpacity,
                    valueRange = 0f..1f,
                    onValueChange = viewModel::setHubBackgroundOpacity
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    SettingGroup(title = stringResource(R.string.hub_weather)) {
        SettingSwitchRow(
            title = stringResource(R.string.weather_animated_sky),
            subtitle = stringResource(R.string.weather_animated_sky_sub),
            checked = animatedSky,
            onCheckedChange = viewModel::setWeatherAnimatedSky
        )
    }
}

// ════════════════════════════════════════════════════════════
// START SCREEN
// ════════════════════════════════════════════════════════════

@Composable
private fun StartScreenGroups(viewModel: SettingsViewModel) {
    val homeScreenLayout by viewModel.homeScreenLayout.collectAsState()
    val isTablet = LocalIsWideScreen.current

    SettingGroup(title = stringResource(R.string.settings_start_layout)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.settings_layout_zune),
                subtitle = stringResource(R.string.settings_layout_zune_sub),
                selected = homeScreenLayout == HomeScreenLayout.ZUNE,
                onClick = { viewModel.setHomeScreenLayout(HomeScreenLayout.ZUNE) }
            )

            // Tablets run the Windows 8 full-screen Start instead of the phone tile grid.
            SettingChoiceRow(
                title = stringResource(
                    if (isTablet) R.string.settings_layout_win8 else R.string.settings_layout_wp
                ),
                subtitle = stringResource(
                    if (isTablet) R.string.settings_layout_win8_sub else R.string.settings_layout_wp_sub
                ),
                selected = homeScreenLayout == HomeScreenLayout.WINDOWS_PHONE,
                onClick = { viewModel.setHomeScreenLayout(HomeScreenLayout.WINDOWS_PHONE) }
            )
        }
    }

}

// ════════════════════════════════════════════════════════════
// TILES
// ════════════════════════════════════════════════════════════

@Composable
private fun TileGroups(viewModel: SettingsViewModel) {
    val tileCornerStyle by viewModel.tileCornerStyle.collectAsState()
    val tileSpacing by viewModel.tileSpacing.collectAsState()
    val tileOpacity by viewModel.tileOpacity.collectAsState()
    val tileAnimation by viewModel.tileAnimation.collectAsState()
    val tileInk by viewModel.tileInk.collectAsState()

    SettingGroup(title = stringResource(R.string.settings_favorite_tiles)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.settings_corners_square),
                subtitle = stringResource(R.string.settings_corners_square_sub),
                selected = tileCornerStyle == TileCornerStyle.SHARP,
                onClick = { viewModel.setTileCornerStyle(TileCornerStyle.SHARP) }
            )
            SettingChoiceRow(
                title = stringResource(R.string.settings_corners_rounded),
                subtitle = stringResource(R.string.settings_corners_rounded_sub),
                selected = tileCornerStyle == TileCornerStyle.ROUNDED,
                onClick = { viewModel.setTileCornerStyle(TileCornerStyle.ROUNDED) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            SettingSlider(
                label = stringResource(R.string.settings_tile_spacing),
                value = "$tileSpacing dp",
                sliderValue = tileSpacing.toFloat(),
                valueRange = 0f..16f,
                steps = 15,
                onValueChange = { viewModel.setTileSpacing(it.toInt()) }
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    SettingGroup(title = stringResource(R.string.settings_tile_look)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The slider runs the way the label reads: to the right is more see-through.
            SettingSlider(
                label = stringResource(R.string.settings_tile_transparency),
                sublabel = stringResource(R.string.settings_tile_transparency_sub),
                value = "%${100 - tileOpacity}",
                sliderValue = (100 - tileOpacity).toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                onValueChange = { viewModel.setTileOpacity(100 - it.toInt()) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_anim_slide),
                subtitle = stringResource(R.string.settings_tile_anim_slide_sub),
                selected = tileAnimation == TileAnimation.SLIDE,
                onClick = { viewModel.setTileAnimation(TileAnimation.SLIDE) }
            )
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_anim_flip),
                subtitle = stringResource(R.string.settings_tile_anim_flip_sub),
                selected = tileAnimation == TileAnimation.FLIP,
                onClick = { viewModel.setTileAnimation(TileAnimation.FLIP) }
            )
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_anim_off),
                subtitle = stringResource(R.string.settings_tile_anim_off_sub),
                selected = tileAnimation == TileAnimation.NONE,
                onClick = { viewModel.setTileAnimation(TileAnimation.NONE) }
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Follows the transparency slider for a reason: turning a tile down until the wallpaper
    // shows through is what raises the question of whether its writing can still be read.
    SettingGroup(title = stringResource(R.string.settings_tile_ink)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_ink_auto),
                subtitle = stringResource(R.string.settings_tile_ink_auto_sub),
                selected = tileInk == TileInk.AUTO,
                onClick = { viewModel.setTileInk(TileInk.AUTO) }
            )
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_ink_dark),
                subtitle = stringResource(R.string.settings_tile_ink_dark_sub),
                selected = tileInk == TileInk.DARK,
                onClick = { viewModel.setTileInk(TileInk.DARK) }
            )
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_ink_light),
                subtitle = stringResource(R.string.settings_tile_ink_light_sub),
                selected = tileInk == TileInk.LIGHT,
                onClick = { viewModel.setTileInk(TileInk.LIGHT) }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// ICONS
// ════════════════════════════════════════════════════════════

@Composable
private fun IconGroups(viewModel: SettingsViewModel) {
    val tileIconStyle by viewModel.tileIconStyle.collectAsState()
    val iconPack by viewModel.iconPackPackage.collectAsState()
    val packs = remember { viewModel.installedIconPacks() }

    SettingGroup(title = stringResource(R.string.settings_tile_icons)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_icon_wp),
                subtitle = stringResource(R.string.settings_tile_icon_wp_sub),
                selected = tileIconStyle == TileIconStyle.WINDOWS_PHONE,
                onClick = { viewModel.setTileIconStyle(TileIconStyle.WINDOWS_PHONE) }
            )
            SettingChoiceRow(
                title = stringResource(R.string.settings_tile_icon_original),
                subtitle = stringResource(R.string.settings_tile_icon_original_sub),
                selected = tileIconStyle == TileIconStyle.ORIGINAL,
                onClick = { viewModel.setTileIconStyle(TileIconStyle.ORIGINAL) }
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    SettingGroup(title = stringResource(R.string.settings_icon_pack)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChoiceRow(
                title = stringResource(R.string.settings_icon_pack_none),
                subtitle = stringResource(R.string.settings_icon_pack_none_sub),
                selected = iconPack == null,
                onClick = { viewModel.setIconPack(null) }
            )
            packs.forEach { pack ->
                SettingChoiceRow(
                    title = pack.label,
                    subtitle = pack.packageName,
                    selected = iconPack == pack.packageName,
                    onClick = { viewModel.setIconPack(pack.packageName) }
                )
            }
            if (packs.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_icon_pack_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZuneColors.current.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// SHARED PIECES
// ════════════════════════════════════════════════════════════

/** The cropper both wallpaper pages open a picked image in. */
@Composable
private fun CropDialog(
    uri: android.net.Uri?,
    onApply: (android.graphics.Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val picked = uri ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        WallpaperCropScreen(
            uri = picked,
            onApply = {
                onApply(it)
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

/** What a chosen image offers: another image, or none at all. */
@Composable
private fun ChangeOrClearRow(onChange: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = {
                if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) onChange()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalZuneColors.current.accentColor,
                contentColor = Color.White
            )
        ) {
            Text(stringResource(R.string.settings_wallpaper_change))
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onClear,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LocalZuneColors.current.textMuted
            )
        ) {
            Text(stringResource(R.string.common_clear))
        }
    }
}

/** A labelled slider with its value beside the label, as every one of these pages wants. */
@Composable
private fun SettingSlider(
    label: String,
    value: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    sublabel: String? = null,
    steps: Int = 0
) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = zuneColors.accentColor
            )
        }
        if (sublabel != null) {
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = zuneColors.accentColor,
                activeTrackColor = zuneColors.accentColor,
                inactiveTrackColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f)
            )
        )
    }
}

// ════════════════════════════════════════════════════════════
// WHAT EACH PAGE CURRENTLY SAYS
// ════════════════════════════════════════════════════════════

/**
 * How big the writing is and whether the launcher moves.
 *
 * Both of these were stored settings that nothing on screen could change and nothing in the
 * launcher read — the type scale had a setter no caller ever called, and the animation switch was
 * remembered by a launcher whose animations never asked about it.
 */
@Composable
private fun TextAndMotionGroups(viewModel: SettingsViewModel) {
    val fontScale by viewModel.fontScale.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()

    SettingGroup(title = stringResource(R.string.settings_text_size)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontScaleChoice.entries.forEach { choice ->
                SettingChoiceRow(
                    title = stringResource(choice.titleRes),
                    subtitle = stringResource(R.string.settings_text_size_sample),
                    selected = choice.matches(fontScale),
                    onClick = { viewModel.setFontScale(choice.scale) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingGroup(title = stringResource(R.string.settings_motion)) {
        SettingSwitchRow(
            title = stringResource(R.string.settings_animations),
            subtitle = stringResource(
                if (animationsEnabled) R.string.settings_animations_on else R.string.settings_animations_off
            ),
            checked = animationsEnabled,
            onCheckedChange = { viewModel.setAnimationsEnabled(it) }
        )
    }
}

/**
 * The type sizes offered.
 *
 * Steps rather than a slider: Metro asks you to choose between named things, and a launcher whose
 * every screen is set in one typeface has no use for the half point between two of them.
 */
internal enum class FontScaleChoice(@StringRes val titleRes: Int, val scale: Float) {
    SMALL(R.string.settings_text_small, 0.85f),
    NORMAL(R.string.settings_text_normal, 1.0f),
    LARGE(R.string.settings_text_large, 1.15f),
    LARGER(R.string.settings_text_larger, 1.3f);

    /** Whichever step the stored number is nearest to, so an old odd value still shows a choice. */
    fun matches(stored: Float): Boolean =
        entries.minByOrNull { kotlin.math.abs(it.scale - stored) } == this
}

/**
 * The line under each entry in the list, so the tab still answers "what is it set to?" without
 * having to be opened. A page that has been split off is only an improvement if you can still see
 * what is inside it.
 */
@Composable
internal fun lookPageSummary(page: LookPage, viewModel: SettingsViewModel): String = when (page) {
    LookPage.THEME -> {
        val themeMode by viewModel.themeMode.collectAsState()
        val accentColor by viewModel.accentColor.collectAsState()
        val theme = stringResource(
            when (themeMode) {
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
                ThemeMode.SYSTEM -> R.string.theme_system
            }
        )
        "$theme • ${accentName(accentColor)}"
    }

    LookPage.TEXT_AND_MOTION -> {
        val fontScale by viewModel.fontScale.collectAsState()
        val animationsEnabled by viewModel.animationsEnabled.collectAsState()
        val size = stringResource(
            FontScaleChoice.entries.first { it.matches(fontScale) }.titleRes
        )
        val motion = stringResource(
            if (animationsEnabled) R.string.settings_animations_on_short else R.string.settings_animations_off_short
        )
        "$size • $motion"
    }

    LookPage.WALLPAPER -> {
        val solid by viewModel.solidBackgroundEnabled.collectAsState()
        val path by viewModel.customWallpaperPath.collectAsState()
        when {
            solid -> stringResource(R.string.settings_solid_background)
            path != null -> stringResource(R.string.settings_wallpaper_custom)
            else -> stringResource(R.string.theme_system)
        }
    }

    LookPage.HUB_BACKGROUND -> {
        val mode by viewModel.hubBackgroundMode.collectAsState()
        stringResource(
            when (mode) {
                HubBackgroundMode.MATCH_LAUNCHER -> R.string.settings_hub_background_same
                HubBackgroundMode.CUSTOM -> R.string.settings_wallpaper_custom
                HubBackgroundMode.SYSTEM -> R.string.theme_system
                HubBackgroundMode.SOLID -> R.string.settings_solid_background
            }
        )
    }

    LookPage.START_SCREEN -> {
        val layout by viewModel.homeScreenLayout.collectAsState()
        val isTablet = LocalIsWideScreen.current
        stringResource(
            when (layout) {
                HomeScreenLayout.ZUNE -> R.string.settings_layout_zune
                HomeScreenLayout.WINDOWS_PHONE ->
                    if (isTablet) R.string.settings_layout_win8 else R.string.settings_layout_wp
            }
        )
    }

    LookPage.TILES -> {
        val corners by viewModel.tileCornerStyle.collectAsState()
        val opacity by viewModel.tileOpacity.collectAsState()
        val corner = stringResource(
            if (corners == TileCornerStyle.ROUNDED) {
                R.string.settings_corners_rounded
            } else {
                R.string.settings_corners_square
            }
        )
        "$corner • ${stringResource(R.string.settings_tile_transparency)} %${100 - opacity}"
    }

    LookPage.ICONS -> {
        val style by viewModel.tileIconStyle.collectAsState()
        val pack by viewModel.iconPackPackage.collectAsState()
        val styleName = stringResource(
            if (style == TileIconStyle.ORIGINAL) {
                R.string.settings_tile_icon_original
            } else {
                R.string.settings_tile_icon_wp
            }
        )
        val packName = pack?.substringAfterLast('.') ?: stringResource(R.string.settings_icon_pack_none)
        "$styleName • $packName"
    }
}

/** Accent swatches carry no labels of their own, so the two that are not colours get names. */
@Composable
private fun accentName(accentColor: AccentColor): String = when (accentColor) {
    AccentColor.CUSTOM -> stringResource(R.string.settings_accent_custom)
    AccentColor.DYNAMIC -> stringResource(R.string.settings_accent_dynamic)
    else -> accentColor.name.lowercase()
}

/** The line of explanation under each entry, saying what the page is for. */
internal val LookPage.summaryRes: Int
    get() = when (this) {
        LookPage.THEME -> R.string.settings_look_theme_sub
        LookPage.WALLPAPER -> R.string.settings_look_wallpaper_sub
        LookPage.HUB_BACKGROUND -> R.string.settings_look_hub_sub
        LookPage.START_SCREEN -> R.string.settings_look_start_sub
        LookPage.TILES -> R.string.settings_look_tiles_sub
        LookPage.ICONS -> R.string.settings_look_icons_sub
        LookPage.TEXT_AND_MOTION -> R.string.settings_look_text_motion_sub
    }
