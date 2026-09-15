package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.OnboardingStep

/**
 * The tour somebody is shown the first time the launcher opens.
 *
 * Windows Phone's own first run was one idea to a page, set in enormous light type against the
 * accent, with a word at the bottom to go on. That is what this is. It sits over the launcher
 * rather than beside it, so there is nothing to swipe past and nothing to get lost in, and it can
 * always be left - a person who wants to see their phone should be allowed to.
 */
@Composable
fun OnboardingScreen(
    tour: FirstRunScreen.Tour,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val zuneColors = LocalZuneColors.current

    // Back walks the tour backwards rather than dropping out of it; only the first page lets go.
    BackHandler { if (tour.isFirst) onSkip() else onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (zuneColors.isDark) Color(0xFF0A0A0A) else Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // -- The step, and the way out --
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 16.dp)
            ) {
                StepDots(count = tour.steps.size, current = tour.index, accent = zuneColors.accentColor)
                Spacer(modifier = Modifier.weight(1f))
                if (!tour.isLast) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted,
                        modifier = Modifier
                            .clickable(onClick = onSkip)
                            .padding(8.dp)
                    )
                }
            }

            // -- The page itself --
            AnimatedContent(
                targetState = tour.step,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                },
                label = "onboarding_step",
                modifier = Modifier.weight(1f)
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                ) {
                    OnboardingStepContent(step)
                }
            }

            // -- On, or back --
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 24.dp)
            ) {
                if (!tour.isFirst) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(
                        if (tour.isLast) R.string.onboarding_finish else R.string.onboarding_next
                    ),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 26.sp
                    ),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .clickable(onClick = onNext)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * The body of one page.
 *
 * Every page is a heading and a line underneath, and most of them then have something to do. The
 * doing parts arrive with their own rounds; for now each page says what it is for.
 */
@Composable
private fun OnboardingStepContent(step: OnboardingStep) {
    when (step) {
        OnboardingStep.WELCOME -> OnboardingPage(
            title = stringResource(R.string.onboarding_welcome_title),
            line = stringResource(R.string.onboarding_welcome_line)
        )

        OnboardingStep.THEME -> OnboardingPage(
            title = stringResource(R.string.onboarding_theme_title),
            line = stringResource(R.string.onboarding_theme_line)
        )

        OnboardingStep.DEFAULT_LAUNCHER -> OnboardingPage(
            title = stringResource(R.string.onboarding_default_title),
            line = stringResource(R.string.onboarding_default_line)
        )

        OnboardingStep.PERMISSIONS -> OnboardingPage(
            title = stringResource(R.string.onboarding_permissions_title),
            line = stringResource(R.string.onboarding_permissions_line)
        )

        OnboardingStep.GESTURES -> OnboardingPage(
            title = stringResource(R.string.onboarding_gestures_title),
            line = ""
        )

        OnboardingStep.DONE -> OnboardingPage(
            title = stringResource(R.string.onboarding_done_title),
            line = stringResource(R.string.onboarding_done_line)
        )
    }
}

/** A heading, a line, and room underneath for whatever the page asks somebody to do. */
@Composable
fun OnboardingPage(
    title: String,
    line: String,
    content: @Composable () -> Unit = {}
) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 44.sp,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (line.isNotBlank()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        content()
    }
}

/** Where in the tour somebody is: one square a page, the one they are on in the accent. */
@Composable
private fun StepDots(count: Int, current: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 10.dp else 6.dp)
                    .background(if (index == current) accent else LocalZuneColors.current.textDim)
            )
        }
    }
}
