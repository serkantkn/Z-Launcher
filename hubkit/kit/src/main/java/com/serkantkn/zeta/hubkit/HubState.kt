package com.serkantkn.zeta.hubkit

/**
 * A value the hub is drawn from: setting it describes the hub again. Made with
 * [ZetaHubService.mutableHubStateOf].
 */
class HubState<T> internal constructor(initial: T, private val changed: () -> Unit) {
    var value: T = initial
        set(new) {
            if (field == new) return
            field = new
            changed()
        }

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, new: T) {
        value = new
    }
}
