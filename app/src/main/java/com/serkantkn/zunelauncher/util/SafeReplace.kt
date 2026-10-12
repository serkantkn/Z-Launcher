package com.serkantkn.zunelauncher.util

import java.io.File

/**
 * Replacing something in a folder without ever being left with neither.
 *
 * The Files hub used to answer "replace" by deleting what was there and then copying the new one
 * in. A copy that was stopped, failed, or ran out of room half way left the folder with neither:
 * the old one already gone, the new one never whole. Now the new one is written beside it under a
 * hidden name first, and only once it is all there do the two change places - two renames inside
 * one folder, which take no time and cannot be caught half done. The old one is deleted last.
 *
 * Should the launcher be killed in the middle anyway, what it leaves behind is named so that the
 * next paste into that folder can tell what happened and put it right: see [recover].
 */
object SafeReplace {

    /** Where the new one is written while the old one still stands. */
    private const val INCOMING = ".zune-incoming."

    /** Where the old one steps aside for the moment the two change places. */
    private const val ASIDE = ".zune-replaced."

    fun incomingName(name: String): String = INCOMING + name

    fun asideName(name: String): String = ASIDE + name

    /** What a leftover is, and the name it stood in for; null for anything that is not one. */
    fun leftover(name: String): Pair<Leftover, String>? = when {
        name.startsWith(INCOMING) && name.length > INCOMING.length -> Leftover.INCOMING to name.removePrefix(INCOMING)
        name.startsWith(ASIDE) && name.length > ASIDE.length -> Leftover.ASIDE to name.removePrefix(ASIDE)
        else -> null
    }

    enum class Leftover { INCOMING, ASIDE }

    /**
     * Puts [incoming] where [existing] is, under [target]'s name.
     *
     * [existing] is renamed aside, [incoming] takes [target]'s name, and only then is the old one
     * deleted. If the second rename fails the old one is put straight back and false comes back,
     * with [incoming] left where it was for the caller to deal with. A failure to delete the old
     * one afterwards is not a failure of the replace: it is left hidden, and [recover] clears it.
     */
    fun swapIn(incoming: File, existing: File, target: File): Boolean {
        val aside = File(existing.parentFile, asideName(existing.name))
        if (aside.exists() && !aside.deleteRecursively()) return false
        if (!existing.renameTo(aside)) return false
        if (!incoming.renameTo(target)) {
            aside.renameTo(existing)
            return false
        }
        aside.deleteRecursively()
        return true
    }

    /**
     * Tidies up after a replace that was interrupted by something worse than a cancel.
     *
     * A half-written new one is removed. An old one that had stepped aside goes back to its name
     * if nothing has taken that name since - the new one never arrived - and is deleted if the new
     * one did. Returns the paths that changed, for the media store.
     */
    fun recover(folder: File): List<String> {
        val changed = mutableListOf<String>()
        val children = folder.listFiles() ?: return changed
        // The new ones first: a stale one must not be mistaken for the arrival that makes an old
        // one redundant.
        children.forEach { child ->
            val (kind, _) = leftover(child.name) ?: return@forEach
            if (kind == Leftover.INCOMING && child.deleteRecursively()) changed += child.absolutePath
        }
        children.forEach { child ->
            val (kind, original) = leftover(child.name) ?: return@forEach
            if (kind != Leftover.ASIDE || !child.exists()) return@forEach
            val home = File(folder, original)
            val done = if (home.exists()) child.deleteRecursively() else child.renameTo(home)
            if (done) changed += home.absolutePath
        }
        return changed
    }
}
