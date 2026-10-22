package com.serkantkn.zeta.data.model

/**
 * What a hub is, wherever hubs are listed, opened, kept or ordered: one of Zeta's own
 * ([HubType]) or one another app supplies through ZetaHubKit ([ExternalHub], see PLATFORM.md).
 *
 * [key] is how it is stored - in the hub order, the hubs taken off home, tile ids ("hub:<key>")
 * and the navigation state - so it never changes for a hub. A Zeta hub's key is its enum name, as
 * it always was, so everything stored before external hubs existed still reads.
 */
sealed interface HubId {
    val key: String

    /** Whether the hub may be left running when the user goes Home. */
    val runsInBackground: Boolean

    /** Whether the hub is offered on a TV. */
    val onTelevision: Boolean

    companion object {
        /** The hub a stored [key] names, or null for one that no longer exists. */
        fun fromKey(key: String): HubId? = when {
            key.startsWith(ExternalHub.PREFIX) -> ExternalHub.fromKey(key)
            else -> HubType.entries.firstOrNull { it.name == key }
        }
    }
}

/**
 * A hub supplied by another app: the service [className] in [packageName] that answers
 * `com.serkantkn.zeta.action.HUB`. Not offered on a TV in the first version.
 */
data class ExternalHub(val packageName: String, val className: String) : HubId {
    override val key: String get() = "$PREFIX$packageName/$className"
    override val runsInBackground: Boolean get() = true
    override val onTelevision: Boolean get() = false

    companion object {
        const val PREFIX = "ext:"

        fun fromKey(key: String): ExternalHub? {
            val rest = key.removePrefix(PREFIX)
            val slash = rest.indexOf('/')
            if (slash <= 0 || slash == rest.lastIndex) return null
            return ExternalHub(rest.substring(0, slash), rest.substring(slash + 1))
        }
    }
}
