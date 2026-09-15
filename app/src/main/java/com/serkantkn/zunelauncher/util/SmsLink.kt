package com.serkantkn.zunelauncher.util

/** Who an `sms:` link is for, and what it already says. */
data class SmsTarget(val address: String, val body: String)

/**
 * Reading an `sms:` or `smsto:` link.
 *
 * These are opaque addresses, not paths: everything after the scheme is one run of text, so the
 * number, the "?", and the message all have to be picked out by hand rather than asked for. The
 * shapes in the wild are `sms:+905551112233`, `smsto:05551112233?body=hello%20there` and, from
 * older apps, a comma-separated list of numbers.
 */
fun parseSmsLink(schemeSpecificPart: String): SmsTarget {
    val raw = schemeSpecificPart.trim()
    if (raw.isEmpty()) return SmsTarget("", "")

    // "+" is a country code here, not a space: only the query after the "?" uses that rule.
    val address = percentDecode(raw.substringBefore('?'), plusIsSpace = false)
        // A link to several people is still one conversation at a time here.
        .substringBefore(',')
        .trim()

    val query = raw.substringAfter('?', "")
    val body = query.split('&')
        .firstOrNull { it.startsWith("$BODY_KEY=") }
        ?.substringAfter('=')
        ?.let { percentDecode(it) }
        .orEmpty()

    return SmsTarget(address, body)
}

/**
 * Undoes percent-escaping, and — in a query, where the rule applies — "+" for a space.
 *
 * A malformed escape is left as it was written rather than dropped: a message that arrives with a
 * stray "%" in it is better than one that arrives a character short.
 */
fun percentDecode(text: String, plusIsSpace: Boolean = true): String {
    if ('%' !in text && !(plusIsSpace && '+' in text)) return text
    val out = StringBuilder(text.length)
    val bytes = ArrayList<Byte>()

    fun flush() {
        if (bytes.isEmpty()) return
        out.append(String(bytes.toByteArray(), Charsets.UTF_8))
        bytes.clear()
    }

    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char == '%' && index + 2 < text.length -> {
                val hex = text.substring(index + 1, index + 3)
                val value = hex.toIntOrNull(16)
                if (value == null) {
                    flush()
                    out.append(char)
                    index++
                } else {
                    bytes.add(value.toByte())
                    index += 3
                }
            }

            char == '+' && plusIsSpace -> {
                flush()
                out.append(' ')
                index++
            }

            else -> {
                flush()
                out.append(char)
                index++
            }
        }
    }
    flush()
    return out.toString()
}

private const val BODY_KEY = "body"
