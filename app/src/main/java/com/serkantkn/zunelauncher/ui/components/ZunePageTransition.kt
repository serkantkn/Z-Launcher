package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled

/**
 * Applies the standard Zune/W10M slide-in and fade-in enter animation 
 * to its content when the composable enters the composition.
 */
@Composable
fun ZunePageTransition(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    if (!animationsEnabled) {
        androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
        return
    }

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
        modifier = modifier
    ) {
        content()
    }
}
