package com.serkantkn.zeta.util

/**
 * Request codes for pending intents, derived so that two different things never share one.
 *
 * A PendingIntent is told apart from another by its request code and its intent's filter
 * equality — not by its extras. Two notifications that differ only in an extra, such as which
 * message to open, therefore need different request codes or the later one overwrites the
 * earlier one's target. `uid.toInt()` and `threadId % 1000` both did that eventually.
 *
 * Each [Kind] owns the top four bits, so a mail message and a text conversation can never land
 * on the same code however their ids happen to hash; the remaining 28 bits are a stable hash of
 * the key, stable across process restarts because a notification posted yesterday must still be
 * cancellable today.
 */
object RequestCodes {

    enum class Kind { SMS_OPEN, SMS_REPLY, SMS_MARK_READ, EMAIL_OPEN, CALL }

    private const val KEY_BITS = 28
    private const val KEY_MASK = (1 shl KEY_BITS) - 1

    fun of(kind: Kind, key: String): Int = (kind.ordinal shl KEY_BITS) or (stableHash(key) and KEY_MASK)

    fun of(kind: Kind, vararg parts: Any): Int = of(kind, parts.joinToString("|"))

    /** FNV-1a over the UTF-16 code units: the same answer on every run and every device. */
    internal fun stableHash(text: String): Int {
        var hash = 0x811C9DC5.toInt()
        for (ch in text) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return hash
    }
}
