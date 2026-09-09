package com.serkantkn.zunelauncher.data.service

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.KeyAction
import com.serkantkn.zunelauncher.data.model.KeyboardKey
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.KeyboardPage
import com.serkantkn.zunelauncher.data.model.KeyboardPanel
import com.serkantkn.zunelauncher.data.model.KeyboardVariant
import com.serkantkn.zunelauncher.data.model.OneHandedMode
import com.serkantkn.zunelauncher.data.model.ShiftState
import com.serkantkn.zunelauncher.data.model.Suggestion
import com.serkantkn.zunelauncher.data.model.TextShortcut
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.keyboard.ClipboardPanel
import com.serkantkn.zunelauncher.ui.keyboard.EmojiPanel
import com.serkantkn.zunelauncher.ui.keyboard.KeyboardMicPermissionActivity
import com.serkantkn.zunelauncher.ui.keyboard.MAX_SUGGESTIONS
import com.serkantkn.zunelauncher.ui.keyboard.OneHandedFrame
import com.serkantkn.zunelauncher.ui.keyboard.QuickSettingsPanel
import com.serkantkn.zunelauncher.ui.keyboard.QuickSettingsState
import com.serkantkn.zunelauncher.ui.keyboard.SuggestionStrip
import com.serkantkn.zunelauncher.ui.keyboard.VoicePanel
import com.serkantkn.zunelauncher.ui.keyboard.VoiceState
import com.serkantkn.zunelauncher.ui.keyboard.ZuneKeyboardView
import com.serkantkn.zunelauncher.ui.keyboard.rememberKeyboardPalette
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.KeyboardLayouts
import com.serkantkn.zunelauncher.util.TextPredictor
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The Z keyboard: a system-wide input method that can be used in every app.
 *
 * The input view is Compose, hosted in the IME window rather than an Activity, so the service
 * supplies the lifecycle / saved-state / ViewModel owners itself ([ImeOwners]) — the same trick
 * [WpToastOverlay] uses for its overlay window.
 *
 * All typing state lives here: the current board, shift, the word being composed (which is what
 * makes suggestions and auto-correct possible), the panels, dictation and the clipboard history.
 * Everything under ui/keyboard is presentational and reports back through callbacks.
 *
 * The service is declared with android:enabled="@bool/zune_keyboard_enabled", which is false in
 * the free flavour, so the keyboard is offered only in the premium build.
 */
class ZuneKeyboardService : InputMethodService() {

    private val owners = ImeOwners()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inputView: ComposeView? = null

    /** Launcher language the current input view was built for; a change rebuilds the view. */
    private var inputViewLocale: String? = null

    private val preferences by lazy { applicationContext.appContainer.keyboardDataStore }
    private val predictor = TextPredictor { language -> loadDictionary(language) }

    // --- UI state -----------------------------------------------------------------------------

    private var page by mutableStateOf(KeyboardPage.LETTERS)
    private var panel by mutableStateOf(KeyboardPanel.KEYS)
    private var shiftState by mutableStateOf(ShiftState.OFF)
    private var variant by mutableStateOf(KeyboardVariant.NORMAL)
    private var language by mutableStateOf(KeyboardLanguage.ENGLISH)
    private var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
    private var voiceState by mutableStateOf(VoiceState.READY)
    private var voicePartial by mutableStateOf("")
    private var enterLabelRes by mutableIntStateOf(R.string.ime_action_enter)
    private var enterLabelOverride by mutableStateOf<String?>(null)

    /**
     * Height of the system navigation bar *inside the keyboard window*, in pixels. Since Android
     * 15 the IME window is laid out edge to edge, so the bottom row would sit under the navigation
     * bar unless the keyboard pads itself by this much. Windows that the system already places
     * above the bar report 0 here, so no gap appears in three-button navigation either.
     */
    private var navigationBarInset by mutableIntStateOf(0)

    // --- Settings, mirrored from the data store ------------------------------------------------

    private var numberRow by mutableStateOf(false)
    private var suggestionsEnabled by mutableStateOf(true)
    private var autoCorrectEnabled by mutableStateOf(true)
    private var splitEnabled by mutableStateOf(false)
    private var oneHandedMode by mutableStateOf(OneHandedMode.OFF)
    private var extraBottomPadding by mutableStateOf(0)
    private var enabledLanguages by mutableStateOf<List<KeyboardLanguage>>(emptyList())
    private var storedActiveLanguage: KeyboardLanguage? = null
    private var learnedWords: Map<String, Int> = emptyMap()
    private var shortcuts: List<TextShortcut> = emptyList()

    // --- Editing state -------------------------------------------------------------------------

    private val composing = StringBuilder()
    private var fieldAllowsSuggestions = true
    private var isPasswordField = false
    private var lastSpaceAt = 0L
    private var lastShiftAt = 0L
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        owners.onCreate()
        attachOwners(window?.window?.decorView)
        collectPreferences()
        language = resolveLanguage()
        scope.launch { withContext(Dispatchers.IO) { predictor.prepare(language) } }
    }

    override fun onCreateInputView(): View {
        inputView?.disposeComposition()
        // Compose resolves its recomposer from the window's root view, so the owners have to sit
        // on the IME window decor as well as on the ComposeView itself.
        attachOwners(window?.window?.decorView)
        val view = ComposeView(keyboardContext()).apply {
            attachOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owners))
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                insets
            }
            setContent { KeyboardContent() }
        }
        inputView = view
        inputViewLocale = AppLocale.current(this).tag
        return view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        language = resolveLanguage()
        // The IME window keeps the system locale, so the view is rebuilt when the launcher's own
        // language (and with it the enter-key caption) changes.
        if (inputViewLocale != null && inputViewLocale != AppLocale.current(this).tag) {
            setInputView(onCreateInputView())
        }
        page = KeyboardLayouts.pageFor(info.inputType)
        panel = KeyboardPanel.KEYS
        variant = KeyboardLayouts.variantFor(info.inputType)
        isPasswordField = KeyboardLayouts.isPasswordField(info.inputType)
        fieldAllowsSuggestions = !KeyboardLayouts.suppressesSuggestions(info.inputType)
        enterLabelOverride = info.actionLabel?.toString()?.takeIf { it.isNotBlank() }
        enterLabelRes = enterLabelRes(info)
        shiftState = ShiftState.OFF
        clearComposing()
        captureClipboard()
        updateNavigationBarInset()
        refreshAutoCaps()
        scope.launch { withContext(Dispatchers.IO) { predictor.prepare(language) } }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopListening()
        clearComposing()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The editor dropped the composing region (the caret was moved or another app edited the
        // text), so the word the keyboard thought it was writing no longer exists.
        if (composing.isNotEmpty() && candidatesStart < 0) clearComposing()
        refreshAutoCaps()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        language = resolveLanguage()
    }

    /** The keyboard always draws its own board; the fullscreen extract editor is never used. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotating or switching between gesture and three-button navigation changes the inset.
        updateNavigationBarInset()
    }

    /** Runs on every layout of the keyboard window, which is when the insets can change. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        updateNavigationBarInset()
    }

    override fun onDestroy() {
        stopListening()
        inputView?.disposeComposition()
        inputView = null
        owners.onDestroy()
        scope.cancel()
        super.onDestroy()
    }

    private fun collectPreferences() {
        fun <T> collect(flow: Flow<T>, apply: (T) -> Unit) {
            scope.launch { flow.collect { apply(it) } }
        }
        collect(preferences.numberRowEnabled) { numberRow = it }
        collect(preferences.suggestionsEnabled) { suggestionsEnabled = it }
        collect(preferences.autoCorrectEnabled) { autoCorrectEnabled = it }
        collect(preferences.splitEnabled) { splitEnabled = it }
        collect(preferences.oneHandedMode) { oneHandedMode = it }
        collect(preferences.bottomPaddingDp) { extraBottomPadding = it }
        collect(preferences.learnedWords) { learnedWords = it }
        collect(preferences.textShortcuts) { shortcuts = it }
        collect(preferences.languages) {
            enabledLanguages = it
            language = resolveLanguage()
        }
        collect(preferences.activeLanguage) {
            storedActiveLanguage = it
            language = resolveLanguage()
        }
    }

    /**
     * Resources for the input view. An IME window runs with the system locale, so the launcher's
     * in-app language is applied explicitly instead of relying on the per-app locale.
     */
    private fun keyboardContext(): Context {
        val tag = AppLocale.current(this).tag ?: return localized()
        return try {
            val config = Configuration(resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            createConfigurationContext(config)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "keyboardContext failed", e)
            localized()
        }
    }

    /**
     * Measures how much of the keyboard window the system covers at the bottom.
     *
     * Both the input view and the window decor are asked, because an IME window hands its children
     * different insets on different manufacturers' builds, and the tappable-element inset is taken
     * into account as well: that is the one that reports the row with the "hide keyboard" and
     * "switch keyboard" buttons on devices that draw it inside the keyboard window.
     */
    private fun updateNavigationBarInset() {
        val views = listOfNotNull(inputView, window?.window?.decorView)
        var bottom = 0
        var insetDetail = ""
        for (view in views) {
            val insets = ViewCompat.getRootWindowInsets(view) ?: continue
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            insetDetail = "nav=$navigation tappable=$tappable bars=$bars"
            bottom = maxOf(bottom, navigation, tappable, bars)
        }
        if (bottom != navigationBarInset) {
            ZuneLog.d(TAG, "bottom inset: $bottom px ($insetDetail)")
            navigationBarInset = bottom
        }
    }

    private fun attachOwners(view: View?) {
        view ?: return
        view.setViewTreeLifecycleOwner(owners)
        view.setViewTreeViewModelStoreOwner(owners)
        view.setViewTreeSavedStateRegistryOwner(owners)
    }

    private fun loadDictionary(language: KeyboardLanguage): List<String> = try {
        assets.open("dict_${language.tag}.txt").bufferedReader().use { it.readLines() }
    } catch (e: Exception) {
        ZuneLog.w(TAG, "dictionary for ${language.tag} could not be read", e)
        emptyList()
    }

    // --- UI ---------------------------------------------------------------------------------

    @Composable
    private fun KeyboardContent() {
        val container = applicationContext.appContainer
        val settings = container.settingsDataStore

        val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
        val accentColor by settings.accentColor.collectAsState(initial = AccentColor.MAGENTA)
        val dynamicThemeColor by settings.dynamicThemeColor.collectAsState(initial = null)
        val customThemeColor by settings.customThemeColor.collectAsState(initial = null)
        val soundEnabled by preferences.soundEnabled.collectAsState(initial = false)
        val vibrationEnabled by preferences.vibrationEnabled.collectAsState(initial = true)
        val keyPreviewEnabled by preferences.keyPreviewEnabled.collectAsState(initial = true)
        val heightScale by preferences.heightScale.collectAsState(initial = 1.0f)
        val clipboard by preferences.clipboardHistory.collectAsState(initial = emptyList())
        val recentEmoji by preferences.recentEmoji.collectAsState(initial = emptyList())

        ZuneLauncherTheme(
            themeMode = themeMode,
            accentColor = accentColor,
            dynamicThemeColor = dynamicThemeColor,
            customThemeColor = customThemeColor
        ) {
            val configuration = LocalConfiguration.current
            val isWideScreen = LocalIsWideScreen.current
            val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val palette = rememberKeyboardPalette()
            val baseHeight = when {
                landscape -> 40.dp
                isWideScreen -> 58.dp
                else -> 52.dp
            }
            val keyHeight = baseHeight * heightScale
            // Compose's own inset does not always reach an IME window, so the value measured on
            // the input view wins whenever it is larger.
            val measuredInset = with(LocalDensity.current) { navigationBarInset.toDp() }
            val bottomInset = maxOf(
                measuredInset,
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ) + extraBottomPadding.dp
            val showLanguageKey = enabledLanguages.size > 1
            val layout = KeyboardLayouts.forPage(page, language, variant, numberRow, showLanguageKey)
            val stripHeight = 38.dp * heightScale
            // The strip is always there while the keys are shown: even with suggestions switched
            // off it carries the clipboard and dictation shortcuts.
            val showStrip = panel == KeyboardPanel.KEYS
            val panelHeight = keyHeight * layout.rows.size + stripHeight + bottomInset
            val enterLabel = enterLabelOverride ?: stringResource(enterLabelRes)

            when (panel) {
                KeyboardPanel.EMOJI -> EmojiPanel(
                    recent = recentEmoji,
                    palette = palette,
                    height = panelHeight,
                    rowHeight = keyHeight,
                    onEmoji = ::commitEmoji,
                    onBackspace = ::backspace,
                    onClose = { panel = KeyboardPanel.KEYS },
                    bottomPadding = bottomInset
                )

                KeyboardPanel.CLIPBOARD -> ClipboardPanel(
                    entries = clipboard,
                    palette = palette,
                    height = panelHeight,
                    rowHeight = keyHeight,
                    onPaste = { entry ->
                        currentInputConnection?.commitText(entry.text, 1)
                        panel = KeyboardPanel.KEYS
                    },
                    onPin = { entry -> scope.launch { preferences.setClipPinned(entry.id, !entry.pinned) } },
                    onDelete = { entry -> scope.launch { preferences.removeClip(entry.id) } },
                    onClear = { scope.launch { preferences.clearClipboard() } },
                    onBackspace = ::backspace,
                    onClose = { panel = KeyboardPanel.KEYS },
                    bottomPadding = bottomInset
                )

                KeyboardPanel.VOICE -> VoicePanel(
                    state = voiceState,
                    partialText = voicePartial,
                    palette = palette,
                    height = panelHeight,
                    rowHeight = keyHeight,
                    onStart = ::startListening,
                    onStop = ::stopListening,
                    onRequestPermission = ::requestMicrophonePermission,
                    onClose = {
                        stopListening()
                        panel = KeyboardPanel.KEYS
                    },
                    bottomPadding = bottomInset
                )

                KeyboardPanel.QUICK_SETTINGS -> QuickSettingsPanel(
                    state = QuickSettingsState(
                        numberRow = numberRow,
                        suggestions = suggestionsEnabled,
                        autoCorrect = autoCorrectEnabled,
                        sound = soundEnabled,
                        vibration = vibrationEnabled,
                        split = splitEnabled,
                        splitAvailable = isWideScreen || landscape,
                        oneHanded = oneHandedMode,
                        oneHandedAvailable = !isWideScreen && !landscape,
                        heightScale = heightScale,
                        bottomPadding = extraBottomPadding,
                        language = language,
                        languages = enabledLanguages
                    ),
                    palette = palette,
                    height = panelHeight,
                    rowHeight = keyHeight,
                    onNumberRow = { value -> scope.launch { preferences.setNumberRowEnabled(value) } },
                    onSuggestions = { value -> scope.launch { preferences.setSuggestionsEnabled(value) } },
                    onAutoCorrect = { value -> scope.launch { preferences.setAutoCorrectEnabled(value) } },
                    onSound = { value -> scope.launch { preferences.setSoundEnabled(value) } },
                    onVibration = { value -> scope.launch { preferences.setVibrationEnabled(value) } },
                    onSplit = { value -> scope.launch { preferences.setSplitEnabled(value) } },
                    onOneHanded = { mode -> scope.launch { preferences.setOneHandedMode(mode) } },
                    onHeightScale = { scale -> scope.launch { preferences.setHeightScale(scale) } },
                    onBottomPadding = { dp -> scope.launch { preferences.setBottomPaddingDp(dp) } },
                    onLanguage = ::selectLanguage,
                    onOpenAllSettings = ::openKeyboardSettings,
                    onBackspace = ::backspace,
                    onClose = { panel = KeyboardPanel.KEYS },
                    bottomPadding = bottomInset
                )

                KeyboardPanel.KEYS -> OneHandedFrame(
                    mode = if (landscape || isWideScreen) OneHandedMode.OFF else oneHandedMode,
                    palette = palette,
                    onSwapSide = {
                        val next = if (oneHandedMode == OneHandedMode.LEFT) OneHandedMode.RIGHT else OneHandedMode.LEFT
                        scope.launch { preferences.setOneHandedMode(next) }
                    },
                    onExit = { scope.launch { preferences.setOneHandedMode(OneHandedMode.OFF) } }
                ) {
                    androidx.compose.foundation.layout.Column {
                        if (showStrip) {
                            SuggestionStrip(
                                suggestions = suggestions,
                                palette = palette,
                                height = stripHeight,
                                onPick = ::pickSuggestion,
                                onForget = { suggestion ->
                                    scope.launch { preferences.forgetWord(suggestion.word.lowercase(localeOf(language))) }
                                },
                                onOpenClipboard = {
                                    captureClipboard()
                                    panel = KeyboardPanel.CLIPBOARD
                                },
                                onOpenVoice = {
                                    voiceState = voiceStateForPermission()
                                    panel = KeyboardPanel.VOICE
                                    if (voiceState == VoiceState.READY) startListening()
                                },
                                onOpenQuickSettings = { panel = KeyboardPanel.QUICK_SETTINGS }
                            )
                        }
                        ZuneKeyboardView(
                            layout = layout,
                            shiftState = shiftState,
                            enterLabel = enterLabel,
                            spaceLabel = language.displayLabel,
                            languageLabel = language.tag.uppercase(Locale.ROOT),
                            palette = palette,
                            keyHeight = keyHeight,
                            keyPreviewEnabled = keyPreviewEnabled,
                            soundEnabled = soundEnabled,
                            vibrationEnabled = vibrationEnabled,
                            split = splitEnabled && (isWideScreen || landscape),
                            onKey = ::onKey,
                            onAlternate = ::replaceLastCharacter,
                            onCursorStep = ::moveCursor,
                            onDeleteWord = ::deleteWord,
                            bottomPadding = bottomInset
                        )
                    }
                }
            }
        }
    }

    // --- Typing -----------------------------------------------------------------------------

    private fun onKey(key: KeyboardKey) {
        try {
            when (key.action) {
                KeyAction.CHARACTER -> commitCharacter(key)
                KeyAction.SPACE -> commitSpace()
                KeyAction.BACKSPACE -> backspace()
                KeyAction.ENTER -> commitEnter()
                KeyAction.SHIFT -> toggleShift()
                KeyAction.PAGE -> key.targetPage?.let {
                    finishWord(null)
                    page = it
                }
                KeyAction.PANEL -> key.targetPanel?.let {
                    finishWord(null)
                    if (it == KeyboardPanel.CLIPBOARD) captureClipboard()
                    if (it == KeyboardPanel.VOICE) voiceState = voiceStateForPermission()
                    panel = it
                }
                KeyAction.LANGUAGE -> cycleLanguage()
            }
        } catch (e: Exception) {
            // A crash here would break typing in every app, so failures are logged, never thrown.
            ZuneLog.e(TAG, "onKey ${key.action} failed", e)
        }
    }

    private fun commitCharacter(key: KeyboardKey) {
        val ic = currentInputConnection ?: return
        val text = if (shiftState != ShiftState.OFF && page == KeyboardPage.LETTERS && key.output.length == 1) {
            KeyboardLayouts.shifted(key.output, language)
        } else {
            key.output
        }
        if (composesWords() && text.length == 1 && isWordCharacter(text[0])) {
            composing.append(text)
            ic.setComposingText(composing, 1)
            updateSuggestions()
        } else {
            finishWord(text)
        }
        if (shiftState == ShiftState.SHIFTED) shiftState = ShiftState.OFF
    }

    /** Space, plus the Windows Phone "two spaces end a sentence" shortcut. */
    private fun commitSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val hadWord = composing.isNotEmpty()
        finishWord(null)

        val before = ic.getTextBeforeCursor(2, 0)
        val doubleSpace = !hadWord &&
            !isPasswordField &&
            variant == KeyboardVariant.NORMAL &&
            now - lastSpaceAt < DOUBLE_SPACE_MS &&
            before != null && before.length == 2 &&
            before[1] == ' ' && before[0].isLetterOrDigit()

        if (doubleSpace) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceAt = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpaceAt = now
        }
        refreshAutoCaps()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } else {
                ic.setComposingText(composing, 1)
            }
            updateSuggestions()
            return
        }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(2, 0)
        val length = if (before != null && before.length == 2 &&
            Character.isSurrogatePair(before[0], before[1])
        ) 2 else 1
        ic.deleteSurroundingText(length, 0)
    }

    /** Backspace swipe: removes the word (and the spaces) before the caret in one go. */
    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            val length = composing.length
            clearComposing()
            ic.deleteSurroundingText(length, 0)
            return
        }
        val before = ic.getTextBeforeCursor(WORD_LOOKBEHIND, 0) ?: return
        if (before.isEmpty()) return
        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        while (end > 0 && !before[end - 1].isWhitespace()) end--
        ic.deleteSurroundingText(before.length - end, 0)
    }

    /** Space-bar slide: one step per notch, in whichever direction the finger moved. */
    private fun moveCursor(steps: Int) {
        if (steps == 0) return
        finishWord(null)
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    /** Hold-to-type alternate: swaps the character the key just produced. */
    private fun replaceLastCharacter(text: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            composing.append(text)
            ic.setComposingText(composing, 1)
            updateSuggestions()
        } else {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(text, 1)
        }
    }

    private fun commitEnter() {
        val ic = currentInputConnection ?: return
        finishWord(null)
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val enterSuppressed = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!enterSuppressed && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        refreshAutoCaps()
    }

    /** Tap shifts the next letter; a second tap right after locks caps, as on Windows Phone. */
    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.SHIFTED
            ShiftState.SHIFTED -> if (now - lastShiftAt < DOUBLE_TAP_MS) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftAt = now
    }

    /** Follows the editor's capitalisation rules (start of a field, after a full stop, ...). */
    private fun refreshAutoCaps() {
        if (shiftState == ShiftState.LOCKED || composing.isNotEmpty()) return
        val ic = currentInputConnection ?: return
        val inputType = currentInputEditorInfo?.inputType ?: return
        val caps = try {
            ic.getCursorCapsMode(inputType)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "getCursorCapsMode failed", e)
            0
        }
        shiftState = if (caps != 0) ShiftState.SHIFTED else ShiftState.OFF
    }

    // --- Words, suggestions and learning -------------------------------------------------------

    private fun composesWords(): Boolean =
        suggestionsEnabled && fieldAllowsSuggestions && !isPasswordField && page == KeyboardPage.LETTERS

    private fun isWordCharacter(char: Char): Boolean = char.isLetter() || char == '\''

    /**
     * Ends the word being composed: expands a text shortcut, applies auto-correction, learns the
     * result and finally commits [terminator] (the punctuation or space that ended the word).
     */
    private fun finishWord(terminator: String?) {
        val ic = currentInputConnection
        val word = composing.toString()
        composing.setLength(0)
        suggestions = emptyList()

        if (ic == null) return
        if (word.isNotEmpty()) {
            val shortcut = shortcuts.firstOrNull { it.trigger.equals(word, ignoreCase = true) }?.expansion
            val corrected = if (shortcut == null && autoCorrectEnabled) {
                predictor.correction(word, language, learnedWords)
            } else {
                null
            }
            val finalWord = shortcut ?: corrected ?: word
            ic.setComposingText(finalWord, 1)
            ic.finishComposingText()
            if (shortcut == null) learn(finalWord)
        }
        if (terminator != null) ic.commitText(terminator, 1)
    }

    /** Drops the composing word without touching the text, e.g. when the caret moved away. */
    private fun clearComposing() {
        composing.setLength(0)
        suggestions = emptyList()
        currentInputConnection?.finishComposingText()
    }

    private fun updateSuggestions() {
        suggestions = if (composesWords() && composing.isNotEmpty()) {
            predictor.suggest(
                typed = composing.toString(),
                language = language,
                learned = learnedWords,
                limit = MAX_SUGGESTIONS,
                allowCorrection = autoCorrectEnabled
            )
        } else {
            emptyList()
        }
    }

    private fun pickSuggestion(suggestion: Suggestion) {
        val ic = currentInputConnection ?: return
        composing.setLength(0)
        suggestions = emptyList()
        ic.setComposingText(suggestion.word, 1)
        ic.finishComposingText()
        ic.commitText(" ", 1)
        // Picking a word by hand is a strong signal: it counts double towards "this is a real word".
        learn(suggestion.word, weight = 2)
        refreshAutoCaps()
    }

    private fun learn(word: String, weight: Int = 1) {
        if (isPasswordField || !fieldAllowsSuggestions) return
        val normalized = word.lowercase(localeOf(language))
        if (normalized.length < 3 || !normalized.all { isWordCharacter(it) }) return
        scope.launch { repeat(weight) { preferences.learnWord(normalized) } }
    }

    // --- Languages, clipboard and dictation ----------------------------------------------------

    /**
     * The language key cycles through the languages switched on in settings; when none are, the
     * layout simply follows the launcher.
     */
    private fun cycleLanguage() {
        val available = enabledLanguages.ifEmpty { return }
        selectLanguage(available[(available.indexOf(language) + 1) % available.size])
    }

    /** Switches to [next] straight away (quick settings) and remembers the choice. */
    private fun selectLanguage(next: KeyboardLanguage) {
        if (next == language) return
        finishWord(null)
        language = next
        scope.launch {
            preferences.setActiveLanguage(next)
            withContext(Dispatchers.IO) { predictor.prepare(next) }
        }
    }

    /** Opens the launcher's full keyboard settings page. */
    private fun openKeyboardSettings() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS_TAB, SETTINGS_TAB_KEYBOARD)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            requestHideSelf(0)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "could not open the keyboard settings", e)
        }
    }

    /**
     * The launcher's own language decides the layout, unless the user switched keyboard languages
     * on in settings; the active IME subtype and the device locale are the fallbacks.
     */
    private fun resolveLanguage(): KeyboardLanguage {
        storedActiveLanguage?.let { active ->
            if (enabledLanguages.isEmpty() || enabledLanguages.contains(active)) return active
        }
        enabledLanguages.firstOrNull()?.let { return it }
        val subtypeTag = try {
            getSystemService(InputMethodManager::class.java)
                ?.currentInputMethodSubtype
                ?.languageTag
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "subtype lookup failed", e)
            null
        }
        val tag = AppLocale.current(this).tag
            ?: subtypeTag
            ?: resources.configuration.locales[0].language
        return KeyboardLanguage.fromTag(tag)
    }

    /** An input method may read the clipboard while it is on screen; that is when history grows. */
    private fun captureClipboard() {
        val text = try {
            val manager = getSystemService(ClipboardManager::class.java) ?: return
            manager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
        } catch (e: Exception) {
            ZuneLog.w(TAG, "clipboard read failed", e)
            null
        }
        if (!text.isNullOrBlank()) scope.launch { preferences.rememberClip(text) }
    }

    private fun commitEmoji(emoji: String) {
        finishWord(null)
        currentInputConnection?.commitText(emoji, 1)
        if (emoji.isNotBlank()) scope.launch { preferences.rememberEmoji(emoji) }
    }

    private fun voiceStateForPermission(): VoiceState =
        if (hasMicrophonePermission()) VoiceState.READY else VoiceState.NEEDS_PERMISSION

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** An IME cannot ask for a runtime permission itself, so a tiny activity does it. */
    private fun requestMicrophonePermission() {
        try {
            startActivity(
                Intent(this, KeyboardMicPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            ZuneLog.e(TAG, "microphone permission request failed", e)
            voiceState = VoiceState.ERROR
        }
    }

    private fun startListening() {
        if (!hasMicrophonePermission()) {
            voiceState = VoiceState.NEEDS_PERMISSION
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceState = VoiceState.ERROR
            return
        }
        stopListening()
        finishWord(null)
        voicePartial = ""
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech.setRecognitionListener(voiceListener)
            speech.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeOf(language).toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
            )
            recognizer = speech
            voiceState = VoiceState.LISTENING
        } catch (e: Exception) {
            ZuneLog.e(TAG, "speech recognition failed to start", e)
            voiceState = VoiceState.ERROR
        }
    }

    private fun stopListening() {
        val speech = recognizer ?: return
        recognizer = null
        try {
            speech.stopListening()
            speech.destroy()
        } catch (e: Exception) {
            ZuneLog.w(TAG, "speech recognizer teardown failed", e)
        }
        if (voiceState == VoiceState.LISTENING) voiceState = VoiceState.READY
    }

    private val voiceListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            voiceState = VoiceState.LISTENING
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            voicePartial = ""
            voiceState = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                VoiceState.READY
            } else {
                ZuneLog.w(TAG, "speech recognition error $error")
                VoiceState.ERROR
            }
            recognizer = null
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) currentInputConnection?.commitText("$text ", 1)
            voicePartial = ""
            voiceState = VoiceState.READY
            recognizer = null
            refreshAutoCaps()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            voicePartial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun localeOf(language: KeyboardLanguage): Locale = Locale.forLanguageTag(language.tag)

    @StringRes
    private fun enterLabelRes(info: EditorInfo): Int =
        when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> R.string.ime_action_go
            EditorInfo.IME_ACTION_SEARCH -> R.string.ime_action_search
            EditorInfo.IME_ACTION_SEND -> R.string.ime_action_send
            EditorInfo.IME_ACTION_NEXT -> R.string.ime_action_next
            EditorInfo.IME_ACTION_DONE -> R.string.ime_action_done
            EditorInfo.IME_ACTION_PREVIOUS -> R.string.ime_action_previous
            else -> R.string.ime_action_enter
        }

    /** Lifecycle / saved-state / ViewModel owners so ComposeView can live in the IME window. */
    private class ImeOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    private companion object {
        const val TAG = "ZuneKeyboard"
        const val SETTINGS_TAB_KEYBOARD = "KEYBOARD"
        const val DOUBLE_SPACE_MS = 700L
        const val DOUBLE_TAP_MS = 500L
        const val WORD_LOOKBEHIND = 64
    }
}
