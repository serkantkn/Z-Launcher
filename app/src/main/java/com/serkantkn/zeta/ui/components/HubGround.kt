package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Draws a hub's own ground - its wallpaper and the shade over it, whatever the hub background
 * setting says - behind something laid over the hub, and the content on top.
 */
typealias HubGroundPainter = @Composable (modifier: Modifier, content: @Composable BoxScope.() -> Unit) -> Unit

/** The ground of the hub this is drawn in; null outside a hub. */
val LocalHubGround = staticCompositionLocalOf<HubGroundPainter?> { null }

/**
 * The ground for a page that covers its hub: a sub-page, the browser's start page, a story.
 *
 * Such a page has to hide what is under it, and they all did it with the theme's plain colour -
 * so the hub's wallpaper vanished the moment one opened, and a hub that opens on one (the
 * browser) never showed its wallpaper at all. Inside a hub this draws the hub's own wallpaper
 * and shade instead, opaque all the same; outside one it is the plain colour as before.
 */
@Composable
fun HubGround(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ground = LocalHubGround.current
    if (ground != null) {
        ground(modifier.fillMaxSize(), content)
    } else {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
    }
}
