package com.serkantkn.zunelauncher.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.E

/**
 * Expression evaluator for the calculator hub. Tokens are kept as a list (numbers, operators,
 * parentheses, functions, constants); [evaluate] runs a shunting-yard pass and computes with
 * BigDecimal so 0.1 + 0.2 shows 0.3. Trigonometry is done in Double and rounded back.
 */
object CalcEngine {

    private val MC = MathContext(20, RoundingMode.HALF_EVEN)
    private val TWO = BigDecimal(2)
    private const val NEWTON_STEPS = 5
    private const val MAX_DIGITS = 12

    class CalcException(val reasonKey: String) : Exception(reasonKey)

    /** Canonical token text as stored in the expression list. */
    object Tokens {
        const val ADD = "+"; const val SUB = "−"; const val MUL = "×"; const val DIV = "÷"; const val POW = "^"
        const val LPAREN = "("; const val RPAREN = ")"; const val PERCENT = "%"; const val FACT = "!"
        const val PI = "π"; const val EULER = "e"
        val FUNCTIONS = setOf("sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "√", "sq", "inv", "neg")
        val BINARY = setOf(ADD, SUB, MUL, DIV, POW)
        val POSTFIX = setOf(PERCENT, FACT)
    }

    fun isNumber(token: String): Boolean = token.isNotEmpty() && (token[0].isDigit() || token[0] == '.' || (token[0] == '-' && token.length > 1))
    fun isFunction(token: String): Boolean = token in Tokens.FUNCTIONS
    fun isBinary(token: String): Boolean = token in Tokens.BINARY
    fun isPostfix(token: String): Boolean = token in Tokens.POSTFIX
    fun isConstant(token: String): Boolean = token == Tokens.PI || token == Tokens.EULER
    fun isOperand(token: String): Boolean = isNumber(token) || isConstant(token) || token == Tokens.RPAREN || isPostfix(token)

    private fun precedence(op: String): Int = when (op) {
        Tokens.ADD, Tokens.SUB -> 1
        Tokens.MUL, Tokens.DIV -> 2
        Tokens.POW -> 3
        else -> 0
    }

    private fun rightAssociative(op: String) = op == Tokens.POW

    /**
     * Evaluates [tokens]. Unbalanced trailing operators/parentheses are tolerated (closing
     * parentheses are implied, a dangling operator is ignored) so the live preview works while typing.
     */
    fun evaluate(tokens: List<String>, degrees: Boolean): BigDecimal? {
        if (tokens.isEmpty()) return null
        val cleaned = tokens.toMutableList()
        while (cleaned.isNotEmpty() && (isBinary(cleaned.last()) || isFunction(cleaned.last()) || cleaned.last() == Tokens.LPAREN)) cleaned.removeAt(cleaned.lastIndex)
        if (cleaned.isEmpty() || cleaned.none { isNumber(it) || isConstant(it) }) return null

        // Shunting-yard → RPN
        val output = mutableListOf<String>()
        val ops = ArrayDeque<String>()
        var previous: String? = null
        for (raw in cleaned) {
            val token = raw
            when {
                isNumber(token) || isConstant(token) -> output += token
                isFunction(token) -> ops.addLast(token)
                isPostfix(token) -> output += token
                token == Tokens.LPAREN -> ops.addLast(token)
                token == Tokens.RPAREN -> {
                    while (ops.isNotEmpty() && ops.last() != Tokens.LPAREN) output += ops.removeLast()
                    if (ops.isNotEmpty()) ops.removeLast()
                    if (ops.isNotEmpty() && isFunction(ops.last())) output += ops.removeLast()
                }
                isBinary(token) -> {
                    // Unary minus after another operator / at the start
                    if (token == Tokens.SUB && (previous == null || isBinary(previous) || previous == Tokens.LPAREN)) {
                        ops.addLast("neg")
                    } else {
                        while (ops.isNotEmpty()) {
                            val top = ops.last()
                            val takes = isFunction(top) || (isBinary(top) && (precedence(top) > precedence(token) || (precedence(top) == precedence(token) && !rightAssociative(token))))
                            if (!takes) break
                            output += ops.removeLast()
                        }
                        ops.addLast(token)
                    }
                }
            }
            previous = token
        }
        while (ops.isNotEmpty()) {
            val op = ops.removeLast()
            if (op != Tokens.LPAREN) output += op
        }

        // RPN evaluation
        val stack = ArrayDeque<BigDecimal>()
        for (token in output) {
            when {
                isNumber(token) -> stack.addLast(token.toBigDecimalOrNull() ?: throw CalcException("format"))
                token == Tokens.PI -> stack.addLast(BigDecimal(PI, MC))
                token == Tokens.EULER -> stack.addLast(BigDecimal(E, MC))
                isBinary(token) -> {
                    if (stack.size < 2) throw CalcException("format")
                    val b = stack.removeLast(); val a = stack.removeLast()
                    stack.addLast(applyBinary(token, a, b))
                }
                isFunction(token) -> {
                    if (stack.isEmpty()) throw CalcException("format")
                    stack.addLast(applyFunction(token, stack.removeLast(), degrees))
                }
                token == Tokens.PERCENT -> {
                    if (stack.isEmpty()) throw CalcException("format")
                    stack.addLast(stack.removeLast().divide(BigDecimal(100), MC))
                }
                token == Tokens.FACT -> {
                    if (stack.isEmpty()) throw CalcException("format")
                    stack.addLast(factorial(stack.removeLast()))
                }
            }
        }
        if (stack.size != 1) throw CalcException("format")
        val result = stack.last()
        if (result.abs() > BigDecimal("1E+300")) throw CalcException("overflow")
        return result
    }

    private fun applyBinary(op: String, a: BigDecimal, b: BigDecimal): BigDecimal = when (op) {
        Tokens.ADD -> a.add(b, MC)
        Tokens.SUB -> a.subtract(b, MC)
        Tokens.MUL -> a.multiply(b, MC)
        Tokens.DIV -> if (b.signum() == 0) throw CalcException("divzero") else a.divide(b, MC)
        Tokens.POW -> pow(a, b)
        else -> throw CalcException("format")
    }

    private fun pow(a: BigDecimal, b: BigDecimal): BigDecimal {
        val exp = b.stripTrailingZeros()
        return if (exp.scale() <= 0 && exp.abs() <= BigDecimal(999)) {
            val n = exp.toInt()
            if (n >= 0) a.pow(n, MC) else BigDecimal.ONE.divide(a.pow(-n, MC), MC)
        } else {
            val d = Math.pow(a.toDouble(), b.toDouble())
            if (d.isNaN() || d.isInfinite()) throw CalcException("domain")
            BigDecimal(d, MC)
        }
    }

    private fun applyFunction(fn: String, x: BigDecimal, degrees: Boolean): BigDecimal {
        fun rad(v: Double) = if (degrees) Math.toRadians(v) else v
        fun deg(v: Double) = if (degrees) Math.toDegrees(v) else v
        val d = x.toDouble()
        val r: Double = when (fn) {
            "sin" -> Math.sin(rad(d))
            "cos" -> Math.cos(rad(d))
            "tan" -> Math.tan(rad(d))
            "asin" -> deg(Math.asin(d))
            "acos" -> deg(Math.acos(d))
            "atan" -> deg(Math.atan(d))
            "ln" -> if (d <= 0) throw CalcException("domain") else Math.log(d)
            "log" -> if (d <= 0) throw CalcException("domain") else Math.log10(d)
            "√" -> if (d < 0) throw CalcException("domain") else return squareRoot(x)
            "sq" -> return x.multiply(x, MC)
            "inv" -> return if (x.signum() == 0) throw CalcException("divzero") else BigDecimal.ONE.divide(x, MC)
            "neg" -> return x.negate()
            else -> throw CalcException("format")
        }
        if (r.isNaN() || r.isInfinite()) throw CalcException("domain")
        // Trig results like sin(180°) come back as 1.2E-16: snap to 12 significant digits.
        return BigDecimal(r, MC).round(MathContext(12, RoundingMode.HALF_EVEN)).let { if (it.abs() < BigDecimal("1E-11")) BigDecimal.ZERO else it }
    }

    /**
     * Square root to the calculator's own precision.
     *
     * BigDecimal grew a sqrt of its own, but only on Android 13 and later, and calling it on
     * anything older is not an exception that can be caught — it is a NoSuchMethodError that
     * ends the process, on a phone whose only sin was being three years old. So the root is
     * worked out here: a double gives the first fifteen digits, and Newton's method doubles the
     * number of correct digits each time round.
     */
    private fun squareRoot(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val approximate = Math.sqrt(x.toDouble())
        var guess = if (approximate.isNaN() || approximate.isInfinite() || approximate == 0.0) {
            BigDecimal.ONE
        } else {
            BigDecimal(approximate, MC)
        }
        repeat(NEWTON_STEPS) {
            guess = guess.add(x.divide(guess, MC), MC).divide(TWO, MC)
        }
        return guess.round(MC).stripTrailingZeros()
    }

    private fun factorial(x: BigDecimal): BigDecimal {
        val n = x.stripTrailingZeros()
        if (n.scale() > 0 || n.signum() < 0 || n > BigDecimal(170)) throw CalcException("domain")
        var acc = BigDecimal.ONE
        for (i in 2..n.toInt()) acc = acc.multiply(BigDecimal(i), MC)
        return acc
    }

    // ── Formatting ────────────────────────────────────────────────────────

    /** Locale-aware display text: grouping, decimal comma for tr, at most 12 significant digits. */
    fun format(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val rounded = value.round(MathContext(MAX_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val abs = rounded.abs()
        return if ((abs >= BigDecimal("1E+12") || (abs < BigDecimal("1E-6") && abs.signum() != 0))) {
            DecimalFormat("0.######E0", symbols).format(rounded)
        } else {
            val plain = rounded.toPlainString()
            val scale = maxOf(0, rounded.scale())
            val pattern = "#,##0" + if (scale > 0) "." + "0".repeat(scale.coerceAtMost(12)) else ""
            DecimalFormat(pattern, symbols).format(BigDecimal(plain))
        }
    }

    /** Display form of a number token still being typed ("12.5" → "12,5" for tr, keeps a trailing separator). */
    fun formatTyping(token: String, locale: Locale = Locale.getDefault()): String {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val negative = token.startsWith("-")
        val body = token.removePrefix("-")
        val intPart = body.substringBefore('.')
        val hasDot = body.contains('.')
        val fracPart = body.substringAfter('.', "")
        val grouped = intPart.toBigIntegerOrNull()?.let { DecimalFormat("#,##0", symbols).format(it) } ?: intPart
        return (if (negative) "-" else "") + grouped + (if (hasDot) symbols.decimalSeparator + fracPart else "")
    }

    /** Readable expression text for the display and the history. */
    fun render(tokens: List<String>, locale: Locale = Locale.getDefault()): String = tokens.joinToString(" ") { token ->
        when {
            isNumber(token) -> formatTyping(token, locale)
            token == "sq" -> "²"
            token == "inv" -> "1/"
            token == "neg" -> "−"
            isFunction(token) -> "$token("
            else -> token
        }
    }.replace(" ( ", " (").replace("( ", "(").replace(" )", ")").replace(" ²", "²").replace(" !", "!").replace(" %", "%")
}
