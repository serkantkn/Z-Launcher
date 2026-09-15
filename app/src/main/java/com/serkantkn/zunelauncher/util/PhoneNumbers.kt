package com.serkantkn.zunelauncher.util

/**
 * The small amount of phone-number handling the launcher does for itself.
 *
 * Nothing here tries to be a number parser: a launcher never has to know what a number means, only
 * whether two of them are the same person and what to hand to the dialler.
 */
object PhoneNumbers {

    /** What may go in a `tel:` address. Everything else in a written number is decoration. */
    private val DIALABLE = Regex("[^0-9+*#,;]")

    /** Strips a written number down to what can actually be dialled. */
    fun toDialable(raw: String): String = DIALABLE.replace(raw, "")

    /** Just the digits, for comparing one number with another. */
    fun digitsOf(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Whether two numbers are the same line.
     *
     * The same person's number is written a dozen ways — with the country code, without it, with
     * spaces, in brackets. Comparing the last [SIGNIFICANT_DIGITS] digits is what every dialler
     * does: it is enough to tell two people apart and forgiving enough to match +90 555 111 22 33
     * against 0555 111 22 33.
     */
    fun sameNumber(a: String, b: String): Boolean {
        val left = digitsOf(a)
        val right = digitsOf(b)
        if (left.isEmpty() || right.isEmpty()) return a.trim() == b.trim()
        val tail = minOf(SIGNIFICANT_DIGITS, left.length, right.length)
        return left.takeLast(tail) == right.takeLast(tail)
    }

    /** The key two numbers share when [sameNumber] says they are the same line. */
    fun matchKey(raw: String): String {
        val digits = digitsOf(raw)
        return if (digits.isEmpty()) raw.trim() else digits.takeLast(SIGNIFICANT_DIGITS)
    }

    /**
     * How many digits from the end decide it. Seven covers a full subscriber number in every
     * plan the launcher is likely to meet, without letting two short numbers collide.
     */
    const val SIGNIFICANT_DIGITS = 7
}
