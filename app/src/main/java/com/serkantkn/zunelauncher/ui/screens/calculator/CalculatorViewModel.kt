package com.serkantkn.zunelauncher.ui.screens.calculator

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalcHistoryEntry
import com.serkantkn.zunelauncher.data.model.UnitCategories
import com.serkantkn.zunelauncher.data.model.UnitCategory
import com.serkantkn.zunelauncher.data.model.UnitDef
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.CalcEngine
import com.serkantkn.zunelauncher.util.CalcEngine.Tokens
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

/** What the display shows: the expression line above, the big current value below. */
data class CalcDisplay(
    val expression: String,
    val value: String,
    val isError: Boolean = false,
    val isResult: Boolean = false
)

/** Converter pivot state. */
data class ConverterState(
    val category: UnitCategory = UnitCategories.LENGTH,
    val from: UnitDef = UnitCategories.LENGTH.units[2],
    val to: UnitDef = UnitCategories.LENGTH.units[3],
    /** Raw digits typed ("12.5"). */
    val input: String = "1",
    val result: String = ""
)

/**
 * Calculator Hub state. House ViewModel template. The expression is a token list; digits
 * extend the last number token, unary functions act immediately on the current value (like
 * the Windows Phone calculator), binary operators are evaluated with precedence on "=".
 */
class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.appContainer.calculatorDataStore
    private val locale: Locale get() = Locale.getDefault()

    private val _tokens = MutableStateFlow<List<String>>(emptyList())
    /** True while the last token is a number the user is still typing. */
    private var editingNumber = false
    private val _lastExpression = MutableStateFlow<String?>(null)
    private val _errorRes = MutableStateFlow<Int?>(null)
    private val _isResult = MutableStateFlow(false)

    val history: StateFlow<List<CalcHistoryEntry>> = dataStore.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val degrees: StateFlow<Boolean> = dataStore.degreesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val memory: StateFlow<String?> = dataStore.memoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val display: StateFlow<CalcDisplay> = combine(_tokens, _lastExpression, _errorRes, _isResult, degrees) { tokens, lastExpr, errorRes, isResult, deg ->
        buildDisplay(tokens, lastExpr, errorRes, isResult, deg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalcDisplay("", "0"))

    private val _converter = MutableStateFlow(ConverterState())
    val converter: StateFlow<ConverterState> = _converter.asStateFlow()

    init {
        recomputeConverter()
    }

    private fun buildDisplay(tokens: List<String>, lastExpr: String?, errorRes: Int?, isResult: Boolean, degrees: Boolean): CalcDisplay {
        if (errorRes != null) return CalcDisplay(lastExpr ?: CalcEngine.render(tokens, locale), app().localizedString(errorRes), isError = true)
        if (tokens.isEmpty()) return CalcDisplay(lastExpr ?: "", "0")
        val last = tokens.last()
        val value = when {
            isResult -> formatToken(last)
            CalcEngine.isNumber(last) -> CalcEngine.formatTyping(last, locale)
            else -> runCatching { CalcEngine.evaluate(tokens, degrees)?.let { CalcEngine.format(it, locale) } }.getOrNull() ?: "0"
        }
        val expression = if (isResult) (lastExpr ?: "") else CalcEngine.render(tokens, locale)
        return CalcDisplay(expression, value, isResult = isResult)
    }

    private fun formatToken(token: String): String =
        token.toBigDecimalOrNull()?.let { CalcEngine.format(it, locale) } ?: token

    // ── Input ─────────────────────────────────────────────────────────────

    private fun update(block: (MutableList<String>) -> Unit) {
        val list = _tokens.value.toMutableList()
        block(list)
        _tokens.value = list
        _errorRes.value = null
    }

    /** After "=" the next digit starts a fresh expression; an operator continues from the result. */
    private fun beginAfterResult(keepValue: Boolean) {
        if (_isResult.value) {
            _isResult.value = false
            _lastExpression.value = null
            if (!keepValue) _tokens.value = emptyList()
        }
    }

    fun digit(d: Char) {
        beginAfterResult(keepValue = false)
        if (_errorRes.value != null) { _tokens.value = emptyList(); _errorRes.value = null }
        update { list ->
            val last = list.lastOrNull()
            if (last != null && CalcEngine.isNumber(last) && editingNumber) {
                if (last.removePrefix("-").length < 15) list[list.lastIndex] = if (last == "0") d.toString() else if (last == "-0") "-$d" else last + d
            } else {
                if (last != null && CalcEngine.isOperand(last)) list += Tokens.MUL   // implicit multiply after ")" / π
                list += d.toString()
                editingNumber = true
            }
        }
    }

    fun decimal() {
        beginAfterResult(keepValue = false)
        update { list ->
            val last = list.lastOrNull()
            if (last != null && CalcEngine.isNumber(last) && editingNumber) {
                if (!last.contains('.')) list[list.lastIndex] = "$last."
            } else {
                if (last != null && CalcEngine.isOperand(last)) list += Tokens.MUL
                list += "0."
                editingNumber = true
            }
        }
    }

    fun operator(op: String) {
        beginAfterResult(keepValue = true)
        if (_errorRes.value != null) return
        editingNumber = false
        update { list ->
            val last = list.lastOrNull()
            when {
                last == null -> if (op == Tokens.SUB) list += op
                CalcEngine.isBinary(last) -> list[list.lastIndex] = op
                last == Tokens.LPAREN -> if (op == Tokens.SUB) list += op
                else -> list += op
            }
        }
    }

    fun parenthesis(open: Boolean) {
        beginAfterResult(keepValue = !open)
        editingNumber = false
        update { list ->
            val last = list.lastOrNull()
            if (open) {
                if (last != null && CalcEngine.isOperand(last)) list += Tokens.MUL
                list += Tokens.LPAREN
            } else {
                val depth = list.count { it == Tokens.LPAREN } - list.count { it == Tokens.RPAREN }
                if (depth > 0 && last != null && !CalcEngine.isBinary(last) && last != Tokens.LPAREN) list += Tokens.RPAREN
            }
        }
    }

    fun constant(token: String) {
        beginAfterResult(keepValue = false)
        editingNumber = false
        update { list ->
            val last = list.lastOrNull()
            if (last != null && CalcEngine.isOperand(last)) list += Tokens.MUL
            list += token
        }
    }

    fun negate() {
        update { list ->
            val last = list.lastOrNull() ?: return@update
            if (CalcEngine.isNumber(last)) {
                list[list.lastIndex] = if (last.startsWith("-")) last.removePrefix("-") else "-$last"
            }
        }
    }

    fun backspace() {
        if (_errorRes.value != null) { clear(); return }
        if (_isResult.value) { clear(); return }
        update { list ->
            val last = list.lastOrNull() ?: return@update
            if (CalcEngine.isNumber(last) && editingNumber && last.removePrefix("-").length > 1) {
                list[list.lastIndex] = last.dropLast(1).let { if (it == "-" ) "0" else it }
            } else {
                list.removeAt(list.lastIndex)
                editingNumber = list.lastOrNull()?.let { CalcEngine.isNumber(it) } == true
            }
        }
    }

    fun clear() {
        _tokens.value = emptyList()
        _lastExpression.value = null
        _errorRes.value = null
        _isResult.value = false
        editingNumber = false
    }

    /**
     * Unary functions (x², √, sin, %, n!, 1/x …) act immediately on the current value: the number
     * being typed when there is one, otherwise the evaluated expression.
     */
    fun applyUnary(fn: String) {
        if (_errorRes.value != null) return
        val tokens = _tokens.value
        if (tokens.isEmpty()) return
        val last = tokens.last()
        try {
            if (CalcEngine.isNumber(last) && !_isResult.value) {
                val value = last.toBigDecimal()
                val result = CalcEngine.evaluate(listOf(fn, Tokens.LPAREN, value.toPlainString(), Tokens.RPAREN).let { if (fn == Tokens.PERCENT || fn == Tokens.FACT) listOf(value.toPlainString(), fn) else it }, degrees.value)
                    ?: return
                update { list -> list[list.lastIndex] = result.round(java.math.MathContext(12)).stripTrailingZeros().toPlainString() }
                editingNumber = false
            } else {
                val current = CalcEngine.evaluate(tokens, degrees.value) ?: return
                val rendered = CalcEngine.render(tokens, locale)
                val result = CalcEngine.evaluate(if (fn == Tokens.PERCENT || fn == Tokens.FACT) listOf(current.toPlainString(), fn) else listOf(fn, Tokens.LPAREN, current.toPlainString(), Tokens.RPAREN), degrees.value)
                    ?: return
                _lastExpression.value = unaryLabel(fn, rendered)
                _tokens.value = listOf(result.round(java.math.MathContext(12)).stripTrailingZeros().toPlainString())
                _isResult.value = true
                editingNumber = false
            }
        } catch (e: CalcEngine.CalcException) {
            _errorRes.value = errorRes(e)
        }
    }

    private fun unaryLabel(fn: String, inner: String): String = when (fn) {
        "sq" -> "($inner)²"
        "inv" -> "1/($inner)"
        Tokens.PERCENT -> "($inner)%"
        Tokens.FACT -> "($inner)!"
        "√" -> "√($inner)"
        else -> "$fn($inner)"
    }

    fun equals() {
        if (_errorRes.value != null) return
        val tokens = _tokens.value
        if (tokens.isEmpty() || _isResult.value) return
        try {
            val result = CalcEngine.evaluate(tokens, degrees.value) ?: return
            val rendered = CalcEngine.render(tokens, locale)
            val plain = result.stripTrailingZeros().toPlainString()
            _lastExpression.value = "$rendered ="
            _tokens.value = listOf(plain)
            _isResult.value = true
            editingNumber = false
            if (tokens.size > 1) viewModelScope.launch { dataStore.addEntry(CalcHistoryEntry(expression = rendered, result = plain)) }
        } catch (e: CalcEngine.CalcException) {
            _errorRes.value = errorRes(e)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "equals failed", e)
            _errorRes.value = R.string.calc_error_generic
        }
    }

    private fun errorRes(e: CalcEngine.CalcException): Int = when (e.reasonKey) {
        "divzero" -> R.string.calc_error_div_zero
        "domain" -> R.string.calc_error_domain
        "overflow" -> R.string.calc_error_overflow
        else -> R.string.calc_error_generic
    }

    fun toggleDegrees() {
        viewModelScope.launch { dataStore.setDegrees(!degrees.value) }
    }

    // ── Memory ────────────────────────────────────────────────────────────

    private fun currentValue(): BigDecimal? {
        val tokens = _tokens.value
        if (tokens.isEmpty()) return BigDecimal.ZERO
        return runCatching { CalcEngine.evaluate(tokens, degrees.value) }.getOrNull()
    }

    fun memoryStore() { currentValue()?.let { v -> viewModelScope.launch { dataStore.setMemory(v.stripTrailingZeros().toPlainString()) } } }
    fun memoryAdd() { val v = currentValue() ?: return; val m = memory.value?.toBigDecimalOrNull() ?: BigDecimal.ZERO; viewModelScope.launch { dataStore.setMemory(m.add(v).stripTrailingZeros().toPlainString()) } }
    fun memorySubtract() { val v = currentValue() ?: return; val m = memory.value?.toBigDecimalOrNull() ?: BigDecimal.ZERO; viewModelScope.launch { dataStore.setMemory(m.subtract(v).stripTrailingZeros().toPlainString()) } }
    fun memoryClear() { viewModelScope.launch { dataStore.setMemory(null) } }
    fun memoryRecall() { memory.value?.let { insertValue(it) } }

    /** Replaces the number being typed (or appends) with [plain]. Used by MR and the history list. */
    fun insertValue(plain: String) {
        beginAfterResult(keepValue = false)
        update { list ->
            val last = list.lastOrNull()
            if (last != null && CalcEngine.isNumber(last) && editingNumber) list[list.lastIndex] = plain
            else {
                if (last != null && CalcEngine.isOperand(last)) list += Tokens.MUL
                list += plain
            }
            editingNumber = false
        }
    }

    // ── History ───────────────────────────────────────────────────────────

    fun useHistoryEntry(entry: CalcHistoryEntry) {
        _tokens.value = listOf(entry.result)
        _lastExpression.value = entry.expression + " ="
        _isResult.value = true
        _errorRes.value = null
        editingNumber = false
    }

    fun removeHistoryEntry(id: String) { viewModelScope.launch { dataStore.removeEntry(id) } }
    fun clearHistory() { viewModelScope.launch { dataStore.clearHistory(); _statusMessage.value = app().localizedString(R.string.calc_history_cleared) } }

    fun copyResult() = copyText(display.value.value)

    fun copyText(text: String) {
        val clipboard = app().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("calc", text))
        _statusMessage.value = app().localizedString(R.string.notes_copied)
    }

    fun clearStatus() { _statusMessage.value = null }

    // ── Converter ─────────────────────────────────────────────────────────

    fun setCategory(category: UnitCategory) {
        _converter.value = ConverterState(category = category, from = category.units.first(), to = category.units.getOrElse(1) { category.units.first() }, input = _converter.value.input)
        recomputeConverter()
    }

    fun setFromUnit(unit: UnitDef) { _converter.value = _converter.value.copy(from = unit); recomputeConverter() }
    fun setToUnit(unit: UnitDef) { _converter.value = _converter.value.copy(to = unit); recomputeConverter() }
    fun swapUnits() { val s = _converter.value; _converter.value = s.copy(from = s.to, to = s.from); recomputeConverter() }

    fun converterDigit(d: Char) {
        val s = _converter.value
        val next = if (s.input == "0") d.toString() else if (s.input.length < 15) s.input + d else s.input
        _converter.value = s.copy(input = next); recomputeConverter()
    }

    fun converterDecimal() {
        val s = _converter.value
        if (!s.input.contains('.')) { _converter.value = s.copy(input = s.input.ifEmpty { "0" } + "."); recomputeConverter() }
    }

    fun converterBackspace() {
        val s = _converter.value
        _converter.value = s.copy(input = s.input.dropLast(1).ifEmpty { "0" }); recomputeConverter()
    }

    fun converterClear() { _converter.value = _converter.value.copy(input = "0"); recomputeConverter() }

    fun converterNegate() {
        val s = _converter.value
        _converter.value = s.copy(input = if (s.input.startsWith("-")) s.input.removePrefix("-") else "-" + s.input); recomputeConverter()
    }

    private fun recomputeConverter() {
        val s = _converter.value
        val value = s.input.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val result = runCatching { UnitCategories.convert(value, s.from, s.to) }.getOrNull()
        _converter.value = s.copy(result = result?.let { CalcEngine.format(it, locale) } ?: "")
    }

    fun converterInputText(): String = CalcEngine.formatTyping(_converter.value.input, locale)

    private fun app(): Application = getApplication()

    private companion object {
        const val TAG = "CalculatorViewModel"
    }
}
