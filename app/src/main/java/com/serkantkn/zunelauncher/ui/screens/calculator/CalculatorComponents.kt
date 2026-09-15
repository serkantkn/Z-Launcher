package com.serkantkn.zunelauncher.ui.screens.calculator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.UnitCategories
import com.serkantkn.zunelauncher.data.model.UnitDef
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.screens.notes.WpRadioRow
import androidx.compose.ui.unit.IntSize
import com.serkantkn.zunelauncher.ui.components.rememberWpTiltAngles
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.CalcEngine.Tokens

// ════════════════════════════════════════════════════════════
// KEYS
// ════════════════════════════════════════════════════════════

/** Visual family of a key: digits are neutral, operators tinted, "=" solid accent, functions quiet. */
enum class CalcKeyStyle { DIGIT, OPERATOR, ACCENT, FUNCTION, TOGGLE }

data class CalcKey(
    val label: String,
    val style: CalcKeyStyle = CalcKeyStyle.DIGIT,
    val weight: Float = 1f,
    val enabled: Boolean = true,
    val onPress: () -> Unit
)

/**
 * A Metro calculator key: flat rectangle, no ripple, the Windows Phone "tilt" on touch (the key
 * leans toward the finger and shrinks slightly) and a spring back on release.
 */
@Composable
fun RowScope.CalcKeyButton(key: CalcKey, modifier: Modifier = Modifier, compact: Boolean = false) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }

    // A key handles its own press so it fires the instant it is touched, rather than on release —
    // so it cannot use the plain tilt modifier, but it uses the same lean underneath it.
    val lean = rememberWpTiltAngles(
        pressPoint = pressOffset.takeIf { pressed },
        size = size,
        maxDegrees = KEY_TILT_DEGREES
    )
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 900f), label = "calc_scale")

    val fg = if (zuneColors.isDark) Color.White else Color.Black
    val (bg, border, textColor) = when (key.style) {
        CalcKeyStyle.DIGIT -> Triple(fg.copy(alpha = 0.08f), fg.copy(alpha = 0.10f), fg)
        CalcKeyStyle.OPERATOR -> Triple(zuneColors.accentColor.copy(alpha = 0.32f), zuneColors.accentColor.copy(alpha = 0.45f), fg)
        CalcKeyStyle.ACCENT -> Triple(zuneColors.accentColor, zuneColors.accentColor, Color.White)
        CalcKeyStyle.FUNCTION -> Triple(fg.copy(alpha = 0.04f), fg.copy(alpha = 0.10f), fg.copy(alpha = 0.9f))
        CalcKeyStyle.TOGGLE -> Triple(zuneColors.accentColor.copy(alpha = 0.18f), zuneColors.accentColor.copy(alpha = 0.6f), fg)
    }
    val fontSize = when {
        key.style == CalcKeyStyle.FUNCTION || key.style == CalcKeyStyle.TOGGLE -> if (compact) 13.sp else 15.sp
        compact -> 20.sp
        else -> 26.sp
    }

    Box(
        modifier = modifier
            .weight(key.weight)
            .fillMaxHeight()
            .graphicsLayer {
                rotationX = lean.rotationX
                rotationY = lean.rotationY
                scaleX = scale
                scaleY = scale
                cameraDistance = 12f * density.density
                transformOrigin = TransformOrigin.Center
            }
            .background(bg, RoundedCornerShape(0.dp))
            .border(1.dp, border, RoundedCornerShape(0.dp))
            .graphicsLayer { alpha = if (key.enabled) 1f else 0.35f }
            .pointerInput(key.enabled, key.onPress) {
                size = IntSize(this.size.width.coerceAtLeast(1), this.size.height.coerceAtLeast(1))
                detectTapGestures(
                    onPress = { offset ->
                        if (key.enabled) {
                            pressOffset = offset
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        }
                    },
                    onTap = { if (key.enabled) key.onPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key.label,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = fontSize, fontWeight = FontWeight.Light),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Fills the available height with equally tall rows of keys. */
@Composable
fun ColumnScope.CalcKeypad(rows: List<List<CalcKey>>, compact: Boolean = false, gap: androidx.compose.ui.unit.Dp = 4.dp) {
    rows.forEach { row ->
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            row.forEach { key -> CalcKeyButton(key = key, compact = compact) }
        }
        Spacer(modifier = Modifier.height(gap))
    }
}

// ════════════════════════════════════════════════════════════
// DISPLAY
// ════════════════════════════════════════════════════════════

/** Right-aligned Windows Phone display: expression line in muted text, the value in big light type. */
@Composable
fun CalcDisplayPanel(display: CalcDisplay, modifier: Modifier = Modifier, memory: String? = null, degrees: Boolean? = null) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    val valueSize = when {
        display.value.length > 18 -> 26.sp
        display.value.length > 12 -> 36.sp
        else -> 52.sp
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (memory != null) {
                Text(text = "M", style = MaterialTheme.typography.labelSmall, color = zuneColors.accentColor, modifier = Modifier.padding(end = 8.dp))
            }
            if (degrees != null) {
                Text(text = stringResource(if (degrees) R.string.calc_deg else R.string.calc_rad), style = MaterialTheme.typography.labelSmall, color = zuneColors.textMuted)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = display.expression,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)
            )
        }
        AnimatedContent(
            targetState = display.value to display.isResult,
            transitionSpec = {
                if (targetState.second && !initialState.second) {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "calc_value"
        ) { (value, _) ->
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = valueSize, letterSpacing = (-1).sp),
                color = if (display.isError) zuneColors.accentColor else fg,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// CONVERTER
// ════════════════════════════════════════════════════════════

@Composable
fun ConverterPage(state: ConverterState, inputText: String, viewModel: CalculatorViewModel, compact: Boolean) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    var showCategoryDialog by remember { mutableStateOf(false) }
    var pickingUnit by remember { mutableStateOf<String?>(null) } // "from" / "to"

    Column(modifier = Modifier.fillMaxSize()) {
        // Category chooser (Metro text button)
        Text(
            text = stringResource(state.category.nameRes),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
            color = zuneColors.accentColor,
            modifier = Modifier
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                .clickable { showCategoryDialog = true }
                .padding(vertical = 6.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // From
        UnitLine(
            value = inputText,
            unit = state.from,
            active = true,
            fg = fg,
            onUnitClick = { pickingUnit = "from" }
        )
        // Swap
        Text(
            text = stringResource(R.string.calc_swap_units),
            style = MaterialTheme.typography.labelLarge,
            color = zuneColors.accentColor,
            modifier = Modifier
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                .clickable { viewModel.swapUnits() }
                .padding(vertical = 4.dp)
        )
        // To
        UnitLine(
            value = state.result,
            unit = state.to,
            active = false,
            fg = fg,
            onUnitClick = { pickingUnit = "to" }
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Numeric keypad
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
            val rows = listOf(
                listOf(CalcKey("7") { viewModel.converterDigit('7') }, CalcKey("8") { viewModel.converterDigit('8') }, CalcKey("9") { viewModel.converterDigit('9') }, CalcKey("⌫", CalcKeyStyle.FUNCTION) { viewModel.converterBackspace() }),
                listOf(CalcKey("4") { viewModel.converterDigit('4') }, CalcKey("5") { viewModel.converterDigit('5') }, CalcKey("6") { viewModel.converterDigit('6') }, CalcKey("C", CalcKeyStyle.FUNCTION) { viewModel.converterClear() }),
                listOf(CalcKey("1") { viewModel.converterDigit('1') }, CalcKey("2") { viewModel.converterDigit('2') }, CalcKey("3") { viewModel.converterDigit('3') }, CalcKey("±", CalcKeyStyle.FUNCTION) { viewModel.converterNegate() }),
                listOf(CalcKey("0", weight = 2f) { viewModel.converterDigit('0') }, CalcKey(stringResource(R.string.calc_decimal_separator)) { viewModel.converterDecimal() }, CalcKey("", CalcKeyStyle.FUNCTION, enabled = false) {})
            )
            CalcKeypad(rows = rows, compact = compact)
        }
    }

    if (showCategoryDialog) {
        ZuneFlipDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = stringResource(R.string.calc_category_cap),
            dismissButton = { ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { showCategoryDialog = false } }, borderColor = zuneColors.textMuted) }
        ) {
            Column {
                UnitCategories.all.forEach { category ->
                    WpRadioRow(text = stringResource(category.nameRes), selected = category.id == state.category.id, onClick = { dismissWithAnim { viewModel.setCategory(category); showCategoryDialog = false } })
                }
            }
        }
    }
    pickingUnit?.let { which ->
        val selected = if (which == "from") state.from else state.to
        ZuneFlipDialog(
            onDismissRequest = { pickingUnit = null },
            title = stringResource(R.string.calc_unit_cap),
            dismissButton = { ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { pickingUnit = null } }, borderColor = zuneColors.textMuted) }
        ) {
            Column {
                state.category.units.forEach { unit ->
                    WpRadioRow(
                        text = "${stringResource(unit.nameRes)} (${unit.symbol})",
                        selected = unit.id == selected.id,
                        onClick = { dismissWithAnim { if (which == "from") viewModel.setFromUnit(unit) else viewModel.setToUnit(unit); pickingUnit = null } }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitLine(value: String, unit: UnitDef, active: Boolean, fg: Color, onUnitClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = value.ifBlank { "0" },
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = if (value.length > 14) 24.sp else 36.sp),
            color = if (active) fg else fg.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState(), reverseScrolling = true)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable { onUnitClick() }.padding(vertical = 4.dp)) {
            Text(text = unit.symbol, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 20.sp), color = zuneColors.accentColor)
            Text(text = stringResource(unit.nameRes), style = MaterialTheme.typography.labelSmall, color = zuneColors.textMuted)
        }
    }
}

/** Key sets shared by the standard and scientific pages. */
object CalcKeys {
    @Composable
    fun standardRows(viewModel: CalculatorViewModel): List<List<CalcKey>> {
        val sep = stringResource(R.string.calc_decimal_separator)
        return listOf(
            listOf(CalcKey("C", CalcKeyStyle.FUNCTION) { viewModel.clear() }, CalcKey("⌫", CalcKeyStyle.FUNCTION) { viewModel.backspace() }, CalcKey("%", CalcKeyStyle.FUNCTION) { viewModel.applyUnary(Tokens.PERCENT) }, CalcKey(Tokens.DIV, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.DIV) }),
            listOf(CalcKey("7") { viewModel.digit('7') }, CalcKey("8") { viewModel.digit('8') }, CalcKey("9") { viewModel.digit('9') }, CalcKey(Tokens.MUL, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.MUL) }),
            listOf(CalcKey("4") { viewModel.digit('4') }, CalcKey("5") { viewModel.digit('5') }, CalcKey("6") { viewModel.digit('6') }, CalcKey(Tokens.SUB, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.SUB) }),
            listOf(CalcKey("1") { viewModel.digit('1') }, CalcKey("2") { viewModel.digit('2') }, CalcKey("3") { viewModel.digit('3') }, CalcKey(Tokens.ADD, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.ADD) }),
            listOf(CalcKey("±", CalcKeyStyle.FUNCTION) { viewModel.negate() }, CalcKey("0") { viewModel.digit('0') }, CalcKey(sep) { viewModel.decimal() }, CalcKey("=", CalcKeyStyle.ACCENT) { viewModel.equals() })
        )
    }

    @Composable
    fun scientificRows(viewModel: CalculatorViewModel, degrees: Boolean, hasMemory: Boolean): List<List<CalcKey>> {
        val sep = stringResource(R.string.calc_decimal_separator)
        val f = CalcKeyStyle.FUNCTION
        return listOf(
            listOf(CalcKey("MC", f, enabled = hasMemory) { viewModel.memoryClear() }, CalcKey("MR", f, enabled = hasMemory) { viewModel.memoryRecall() }, CalcKey("M+", f) { viewModel.memoryAdd() }, CalcKey("M−", f) { viewModel.memorySubtract() }, CalcKey("MS", f) { viewModel.memoryStore() }),
            listOf(CalcKey(stringResource(if (degrees) R.string.calc_deg else R.string.calc_rad), CalcKeyStyle.TOGGLE) { viewModel.toggleDegrees() }, CalcKey("(", f) { viewModel.parenthesis(true) }, CalcKey(")", f) { viewModel.parenthesis(false) }, CalcKey("C", f) { viewModel.clear() }, CalcKey("⌫", f) { viewModel.backspace() }),
            listOf(CalcKey("sin", f) { viewModel.applyUnary("sin") }, CalcKey("cos", f) { viewModel.applyUnary("cos") }, CalcKey("tan", f) { viewModel.applyUnary("tan") }, CalcKey("x²", f) { viewModel.applyUnary("sq") }, CalcKey("√", f) { viewModel.applyUnary("√") }),
            listOf(CalcKey("ln", f) { viewModel.applyUnary("ln") }, CalcKey("log", f) { viewModel.applyUnary("log") }, CalcKey("xʸ", f) { viewModel.operator(Tokens.POW) }, CalcKey("1/x", f) { viewModel.applyUnary("inv") }, CalcKey("n!", f) { viewModel.applyUnary(Tokens.FACT) }),
            listOf(CalcKey("7") { viewModel.digit('7') }, CalcKey("8") { viewModel.digit('8') }, CalcKey("9") { viewModel.digit('9') }, CalcKey(Tokens.MUL, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.MUL) }, CalcKey(Tokens.DIV, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.DIV) }),
            listOf(CalcKey("4") { viewModel.digit('4') }, CalcKey("5") { viewModel.digit('5') }, CalcKey("6") { viewModel.digit('6') }, CalcKey(Tokens.SUB, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.SUB) }, CalcKey("π", f) { viewModel.constant(Tokens.PI) }),
            listOf(CalcKey("1") { viewModel.digit('1') }, CalcKey("2") { viewModel.digit('2') }, CalcKey("3") { viewModel.digit('3') }, CalcKey(Tokens.ADD, CalcKeyStyle.OPERATOR) { viewModel.operator(Tokens.ADD) }, CalcKey("e", f) { viewModel.constant(Tokens.EULER) }),
            listOf(CalcKey("±", f) { viewModel.negate() }, CalcKey("0") { viewModel.digit('0') }, CalcKey(sep) { viewModel.decimal() }, CalcKey("%", f) { viewModel.applyUnary(Tokens.PERCENT) }, CalcKey("=", CalcKeyStyle.ACCENT) { viewModel.equals() })
        )
    }
}

/** A key leans a touch further than a list row: seven degrees at its corner. */
private const val KEY_TILT_DEGREES = 14f
