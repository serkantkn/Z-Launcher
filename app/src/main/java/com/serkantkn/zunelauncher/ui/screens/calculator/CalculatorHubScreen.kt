package com.serkantkn.zunelauncher.ui.screens.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalcHistoryEntry
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.notes.NotesEmptyState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.CalcEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_STANDARD = 0
private const val PAGE_SCIENTIFIC = 1
private const val PAGE_CONVERTER = 2
private const val PAGE_HISTORY = 3

/**
 * Calculator Hub. Canonical hub skeleton: ZuneHubEntranceLayout -> 18sp header -> ZunePivotTabs
 * (standart / bilimsel / dönüştürücü / geçmiş) -> ZuneLoopingPager -> WindowsPhoneBottomBar.
 * Keys are flat Metro rectangles with the Windows Phone tilt-on-touch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()

    val display by viewModel.display.collectAsState()
    val history by viewModel.history.collectAsState()
    val degrees by viewModel.degrees.collectAsState()
    val memory by viewModel.memory.collectAsState()
    val converter by viewModel.converter.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val tabs = listOf(
        stringResource(R.string.calc_tab_standard),
        stringResource(R.string.calc_tab_scientific),
        stringResource(R.string.calc_tab_converter),
        stringResource(R.string.calc_tab_history)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentPage = pager.currentPage
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) { delay(2200); viewModel.clearStatus() }
    }
    BackHandler(enabled = true) { onBack() }

    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    // The keypad fills its page, so it must stop above the app bar (56dp) plus the navigation bar inset.
    val bottomBarClearance = 56.dp + 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val bottomBarActions: List<WpBarAction> = when (currentPage) {
        PAGE_HISTORY -> listOf(
            WpBarAction(Icons.Default.DeleteSweep, stringResource(R.string.common_clear)) { if (history.isNotEmpty()) showClearHistoryDialog = true }
        )
        PAGE_CONVERTER -> listOf(
            WpBarAction(Icons.Default.ContentCopy, stringResource(R.string.common_copy)) { viewModel.copyText(converter.result) }
        )
        else -> listOf(
            WpBarAction(Icons.Default.ContentCopy, stringResource(R.string.common_copy)) { viewModel.copyResult() },
            WpBarAction(Icons.Default.History, stringResource(R.string.calc_tab_history)) { coroutineScope.launch { pager.animateScrollToPage(PAGE_HISTORY) } }
        )
    }
    val bottomBarMenuItems: List<WpBarMenuItem> = buildList {
        if (currentPage == PAGE_STANDARD || currentPage == PAGE_SCIENTIFIC) {
            add(WpBarMenuItem(stringResource(if (degrees) R.string.calc_use_radians else R.string.calc_use_degrees)) { viewModel.toggleDegrees() })
            add(WpBarMenuItem(stringResource(R.string.calc_clear_history)) { if (history.isNotEmpty()) showClearHistoryDialog = true })
        }
    }

    @Composable
    fun StandardPage(compact: Boolean) {
        ZunePageTransition {
            Column(modifier = Modifier.fillMaxSize()) {
                CalcDisplayPanel(display = display, memory = memory, modifier = Modifier.weight(0.3f))
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.weight(0.7f).fillMaxWidth().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                    CalcKeypad(rows = CalcKeys.standardRows(viewModel), compact = compact)
                }
            }
        }
    }

    @Composable
    fun ScientificPage(compact: Boolean) {
        ZunePageTransition {
            Column(modifier = Modifier.fillMaxSize()) {
                CalcDisplayPanel(display = display, memory = memory, degrees = degrees, modifier = Modifier.weight(0.24f))
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.weight(0.76f).fillMaxWidth().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                    CalcKeypad(rows = CalcKeys.scientificRows(viewModel, degrees, memory != null), compact = true, gap = 3.dp)
                }
            }
        }
    }

    @Composable
    fun HistoryPage() {
        ZunePageTransition {
            if (history.isEmpty()) {
                NotesEmptyState(icon = Icons.Default.Calculate, title = stringResource(R.string.calc_history_empty), subtitle = stringResource(R.string.calc_history_empty_hint))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(history, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onClick = {
                                viewModel.useHistoryEntry(entry)
                                coroutineScope.launch { pager.animateScrollToPage(PAGE_STANDARD) }
                            },
                            onLongClick = { viewModel.removeHistoryEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun PageFor(index: Int, compact: Boolean) {
        when (index) {
            PAGE_STANDARD -> StandardPage(compact)
            PAGE_SCIENTIFIC -> ScientificPage(compact)
            PAGE_CONVERTER -> ZunePageTransition { ConverterPage(state = converter, inputText = viewModel.converterInputText(), viewModel = viewModel, compact = compact) }
            else -> HistoryPage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ZuneHubEntranceLayout { bottomBarModifier ->
            if (isWideScreen) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ZuneWideHubTitle(text = stringResource(R.string.hub_calculator))
                    ZuneWidePanorama(tabs = tabs, modifier = Modifier.weight(1f).fillMaxWidth(), fillPageHeight = true) { index -> PageFor(index, compact = false) }
                    Spacer(modifier = Modifier.height(bottomBarClearance))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.hub_calculator),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                            color = headerColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(top = 28.dp, bottom = 4.dp, start = ZuneDimens.ScreenPaddingHorizontal)
                        )
                        ZunePivotTabs(tabs = tabs, state = pager, modifier = Modifier.padding(top = 4.dp))
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize()) { page -> PageFor(page, compact = false) }
                    }
                    Spacer(modifier = Modifier.height(bottomBarClearance))
                }
            }
            WindowsPhoneBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                actions = bottomBarActions,
                menuItems = bottomBarMenuItems
            )
        }

        AnimatedVisibility(
            visible = statusMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(zuneColors.accentColor)
                    .statusBarsPadding()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp)
            ) {
                Text(text = statusMessage ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Color.White)
            }
        }
    }

    if (showClearHistoryDialog) {
        com.serkantkn.zunelauncher.ui.screens.email.EmailConfirmDialog(
            title = stringResource(R.string.calc_clear_history_cap),
            message = stringResource(R.string.calc_clear_history_message, history.size),
            confirmText = stringResource(R.string.common_clear_cap),
            onConfirm = { showClearHistoryDialog = false; viewModel.clearHistory() },
            onDismiss = { showClearHistoryDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(entry: CalcHistoryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    val result = remember(entry.result) { entry.result.toBigDecimalOrNull()?.let { CalcEngine.format(it) } ?: entry.result }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(text = entry.expression + " =", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light), color = zuneColors.textMuted, maxLines = 1)
        Text(text = result, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp), color = fg, maxLines = 1)
        Text(text = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(entry.timestamp)).lowercase(), style = MaterialTheme.typography.labelSmall, color = zuneColors.textDim)
    }
}
