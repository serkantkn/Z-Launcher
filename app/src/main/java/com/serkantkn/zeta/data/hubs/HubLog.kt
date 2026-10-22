package com.serkantkn.zeta.data.hubs

import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubSpecValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What Zeta and other apps' hubs say to each other, for whoever is writing one (developer mode,
 * PLATFORM.md): connections, descriptions with what is wrong in them, touches, commands, failures.
 * Kept in memory only and only while developer mode is on - the keys in it are the hub's own and
 * may name the person's things - and never written to logcat.
 */
object HubLog {
    data class Entry(
        val at: Long,
        val hub: String,
        val what: String,
        val detail: String,
        val problem: Boolean = false
    )

    private const val MAX = 400

    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) _entries.value = emptyList()
        }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(hub: String, what: String, detail: String = "", problem: Boolean = false) {
        if (!enabled) return
        val entry = Entry(System.currentTimeMillis(), hub, what, detail, problem)
        _entries.update { (it + entry).takeLast(MAX) }
    }

    /** A description arrived: how big, and what breaks the rules in it. */
    fun spec(hub: String, spec: HubNode, nodes: Int) {
        if (!enabled) return
        add(hub, "spec", "$nodes parts")
        HubSpecValidator.check(spec).forEach { p ->
            add(hub, if (p.error) "error" else "warning", "${p.key}: ${p.message}", problem = true)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
