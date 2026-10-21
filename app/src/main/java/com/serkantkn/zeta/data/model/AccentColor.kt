package com.serkantkn.zeta.data.model

/**
 * The accent. [LAYOUT], the default, is no colour of its own: it follows the design in use - Mozaik
 * blue, Aura orange to go with its glow, Panorama (a tablet) red, Konsol green - and is resolved by
 * [com.serkantkn.zeta.data.datastore.SettingsDataStore.accentColor]; nothing downstream sees it.
 */
enum class AccentColor {
    LAYOUT,
    DYNAMIC,
    MAGENTA,
    PINK,
    ORANGE,
    PURPLE,
    BLUE,
    GREEN,
    TEAL,
    RED,
    YELLOW,
    LIME,
    CRIMSON,
    COBALT,
    AMBER,
    BROWN,
    CUSTOM
}
