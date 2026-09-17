package com.serkantkn.zunelauncher.data.model

/**
 * What colour a tile writes in: its icon, its name, and the numbers on its face.
 *
 * Windows Phone never had to ask. Its tiles were solid accent colour on black, so white always
 * read. This launcher lets a tile be turned down until the wallpaper shows through it, and then
 * the right answer depends on the wallpaper — which is somebody's photograph, not something the
 * launcher can reason about. On a light theme the guess is black, and on a pale photograph that
 * is right; on a dark photograph seen through a pale theme it is not.
 *
 * So it is asked. [AUTO] keeps the guess, which is what it has always done; the other two settle
 * it.
 */
enum class TileInk {
    /** Work it out from the theme and the tile's own colour. */
    AUTO,

    /** Always black. */
    DARK,

    /** Always white. */
    LIGHT
}

/**
 * Below this, enough of the wallpaper shows through that the tile's own colour stops deciding
 * what can be read on it.
 */
const val TILE_SEE_THROUGH_POINT = 0.6f

/**
 * Whether a tile should write in black. [opacity] is 0f..1f, [fillLuminance] the brightness of
 * the tile's colour (0f..1f) — both as the tile itself sees them.
 *
 * Kept away from Compose so the rule can be read and tested in one place: it is the same
 * decision for the board, for the settings preview, and for anything that draws a tile later.
 */
fun tileInkIsDark(
    ink: TileInk,
    isDarkTheme: Boolean,
    opacity: Float,
    fillLuminance: Float
): Boolean = when (ink) {
    TileInk.DARK -> true
    TileInk.LIGHT -> false
    // See-through: the tile's colour is barely there, so the theme behind it decides.
    // Solid: the tile's own colour decides, whatever the theme is doing.
    TileInk.AUTO -> if (opacity < TILE_SEE_THROUGH_POINT) !isDarkTheme else fillLuminance > 0.5f
}
