package com.serkantkn.zunelauncher.data.model

/**
 * How a live tile swaps its front face for its back face.
 *
 * Windows Phone used more than one motion: most tiles slid their content upwards and let the new
 * content come in from below, the People and Photos tiles turned over, and a tile whose live data
 * was switched off simply stood still. [CYCLE] is the Photos tile's cross-fade and is chosen by
 * that tile itself, never by the user.
 */
enum class TileAnimation {
    SLIDE,
    FLIP,
    NONE,
    CYCLE
}
