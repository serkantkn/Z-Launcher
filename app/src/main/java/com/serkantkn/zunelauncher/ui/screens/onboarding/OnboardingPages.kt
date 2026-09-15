package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.toColor
import com.serkantkn.zunelauncher.util.AppLanguage

/**
 * The pages somebody actually does something on.
 *
 * Language and the look come first, and both take effect the moment they are touched: a setting
 * that only applies later gives nobody any way to tell whether they picked the right one.
 */

// -- Language ---------------------------------------------------------------

/**
 * Which language the launcher speaks.
 *
 * "The phone's language" is the first choice and the one already selected, because it is right
 * far more often than not. Picking a different one on Android 13 and later hands the choice to
 * the system, which restarts the launcher to apply it - which is exactly why this page is first:
 * there is no progress to lose.
 */
@Composable
fun LanguageStepPage(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_language_title),
        line = stringResource(R.string.onboarding_language_line)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLanguage.entries.forEach { language ->
                ChoiceRow(
                    title = stringResource(language.titleRes),
                    selected = language == current,
                    onClick = { onSelect(language) }
                )
            }
        }
    }
}

// -- The look ---------------------------------------------------------------

/**
 * Light or dark, and the accent.
 *
 * "The phone's setting" is again first and again preselected, so a phone in dark mode opens a dark
 * launcher without anybody being asked to state the obvious.
 */
@Composable
fun ThemeStepPage(
    themeMode: ThemeMode,
    accent: AccentColor,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit
) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_theme_title),
        line = stringResource(R.string.onboarding_theme_line)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT).forEach { mode ->
                ChoiceRow(
                    title = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.DARK -> R.string.theme_dark
                            ThemeMode.LIGHT -> R.string.theme_light
                        }
                    ),
                    selected = mode == themeMode,
                    onClick = { onThemeMode(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.onboarding_accent_line),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZuneColors.current.textMuted,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        AccentSquares(selected = accent, onSelect = onAccent)
    }
}

/**
 * The colours, as squares.
 *
 * The free build is limited to three of them, the same three the settings screen allows; offering
 * the rest here and refusing them later would be a worse introduction than showing what there is.
 */
@Composable
private fun AccentSquares(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val offered = remember {
        val all = AccentColor.entries.filter { it != AccentColor.DYNAMIC && it != AccentColor.CUSTOM }
        if (BuildConfig.IS_PREMIUM) {
            all
        } else {
            all.filter { it == AccentColor.RED || it == AccentColor.BLUE || it == AccentColor.GREEN }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        offered.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { colour ->
                    val isSelected = colour == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colour.toColor())
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(colour) }
                    )
                }
            }
        }
    }
}

// -- The greeting -----------------------------------------------------------

/**
 * The first word, in the language and the colour that were just chosen.
 *
 * It arrives on the Windows Phone turnstile - the letters pivoting in from the right edge - which
 * is the launcher's own entrance animation and the quickest way to say what this is going to be
 * like. It holds still when animations are switched off, like everything else.
 */
@Composable
fun WelcomeStepPage() {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(durationMillis = 700)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 76.sp,
                letterSpacing = (-3).sp
            ),
            color = LocalZuneColors.current.accentColor,
            modifier = Modifier.w10mStaggeredAnimation(progress = entrance.value, index = 0)
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_line),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = LocalZuneColors.current.textMuted,
            modifier = Modifier
                .padding(top = 18.dp)
                .w10mStaggeredAnimation(progress = entrance.value, index = 1)
        )
    }
}

// -- Shared -----------------------------------------------------------------

/** One choice in a list: a square that fills with the accent when it is the one picked. */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) zuneColors.accentColor else zuneColors.textDim)
                .padding(4.dp)
                .background(if (selected) zuneColors.accentColor else Color.Transparent)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}
