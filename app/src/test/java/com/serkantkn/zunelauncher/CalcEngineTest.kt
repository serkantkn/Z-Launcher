package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.CalcEngine
import com.serkantkn.zunelauncher.util.CalcEngine.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class CalcEngineTest {

    private fun eval(vararg tokens: String, degrees: Boolean = true): BigDecimal? = CalcEngine.evaluate(tokens.toList(), degrees)
    private fun plain(vararg tokens: String) = eval(*tokens)?.stripTrailingZeros()?.toPlainString()

    @Test fun precedence() = assertEquals("14", plain("2", Tokens.ADD, "3", Tokens.MUL, "4"))
    @Test fun parentheses() = assertEquals("20", plain("(", "2", Tokens.ADD, "3", ")", Tokens.MUL, "4"))
    @Test fun decimalsAreExact() = assertEquals("0.3", plain("0.1", Tokens.ADD, "0.2"))
    @Test fun unaryMinus() = assertEquals("-6", plain(Tokens.SUB, "2", Tokens.MUL, "3"))
    @Test fun powerIsRightAssociative() = assertEquals("512", plain("2", Tokens.POW, "3", Tokens.POW, "2"))
    @Test fun percentPostfix() = assertEquals("0.5", plain("50", Tokens.PERCENT))
    @Test fun factorial() = assertEquals("120", plain("5", Tokens.FACT))
    @Test fun squareRoot() = assertEquals("4", plain("√", "(", "16", ")"))

    // The root is worked out by hand rather than by BigDecimal.sqrt, which only exists on
    // Android 13 and later. These are the cases where an approximation would show.
    // Twenty significant figures, every one of them right: 1.41421356237309504880...
    @Test fun squareRootOfTwo() = assertEquals("1.4142135623730950488", plain("√", "(", "2", ")"))
    @Test fun squareRootOfZero() = assertEquals("0", plain("√", "(", "0", ")"))
    @Test fun squareRootOfAFraction() = assertEquals("0.5", plain("√", "(", "0.25", ")"))
    @Test fun squareRootOfSomethingLarge() = assertEquals("1000000", plain("√", "(", "1000000000000", ")"))
    @Test fun squareRootSquaresBack() =
        assertEquals("7", plain("√", "(", "49", ")"))
    @Test fun sinInDegrees() = assertEquals("1", plain("sin", "(", "90", ")"))
    @Test fun sinOf180IsZero() = assertEquals("0", plain("sin", "(", "180", ")"))
    @Test fun implicitClosingParenthesis() = assertEquals("6", plain("2", Tokens.MUL, "(", "1", Tokens.ADD, "2"))
    @Test fun trailingOperatorIsIgnored() = assertEquals("7", plain("7", Tokens.ADD))
    @Test fun emptyIsNull() = assertNull(eval())

    @Test fun divisionByZero() {
        val e = assertThrows(CalcEngine.CalcException::class.java) { eval("1", Tokens.DIV, "0") }
        assertEquals("divzero", e.reasonKey)
    }

    @Test fun logDomain() {
        val e = assertThrows(CalcEngine.CalcException::class.java) { eval("ln", "(", "0", ")") }
        assertEquals("domain", e.reasonKey)
    }

    @Test fun turkishFormatting() {
        assertEquals("1.234,5", CalcEngine.format(BigDecimal("1234.5"), Locale("tr")))
        assertEquals("1,234.5", CalcEngine.format(BigDecimal("1234.5"), Locale.US))
        assertEquals("12,", CalcEngine.formatTyping("12.", Locale("tr")))
    }

    @Test fun renderExpression() {
        assertEquals("2 × (3 + 4)", CalcEngine.render(listOf("2", Tokens.MUL, "(", "3", Tokens.ADD, "4", ")"), Locale.US))
    }
}
