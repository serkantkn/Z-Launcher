package com.serkantkn.zunelauncher.util

/**
 * What letting go of dragged files over a folder does.
 *
 * Windows' rule, which people have long since learnt without being told: a drop on the same
 * drive moves, a drop on another drive copies. Moving a holiday's photos to the SD card by
 * dragging them should not leave the phone without them if the card is then taken out.
 */
enum class DropAction { MOVE, COPY, REFUSED }

/**
 * The volume [path] lives on: the longest of [volumeRoots] it sits under, or null when it is on
 * none of them. The longest, because a card can be mounted under a folder of another volume.
 */
fun volumeRootOf(path: String, volumeRoots: List<String>): String? {
    val trimmed = path.trimEnd('/')
    return volumeRoots
        .map { it.trimEnd('/') }
        .filter { root -> trimmed == root || trimmed.startsWith("$root/") }
        .maxByOrNull { it.length }
}

/**
 * Whether dropping [sources] on [destination] moves them, copies them, or does nothing.
 *
 * Nothing when a folder would go into itself or one of its own folders — the copy would chase
 * its own tail until the disk filled — and nothing when every one of them already sits in the
 * destination, which would only rename them to "(2)". A mixed drop, some of which are already
 * there, goes ahead: the paste skips those and says so.
 *
 * A move needs every source on the destination's volume; one that is not, or a place nobody
 * knows the volume of, makes the whole drop a copy, which can never lose anything.
 */
fun dropActionFor(sources: List<String>, destination: String, volumeRoots: List<String>): DropAction {
    if (sources.isEmpty()) return DropAction.REFUSED
    if (sources.any { isInsideItself(it, destination) }) return DropAction.REFUSED
    if (sources.all { isAlreadyThere(it, destination) }) return DropAction.REFUSED
    val destinationRoot = volumeRootOf(destination, volumeRoots) ?: return DropAction.COPY
    val sameVolume = sources.all { volumeRootOf(it, volumeRoots) == destinationRoot }
    return if (sameVolume) DropAction.MOVE else DropAction.COPY
}
