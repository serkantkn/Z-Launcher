package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import com.serkantkn.zunelauncher.ui.tv.tvTake
import com.serkantkn.zunelauncher.ui.tv.vistaGlass
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch

class ZuneFlipDialogScope(
    private val dismissWithAnimInternal: (onComplete: (() -> Unit)?) -> Unit
) {
    fun dismissWithAnim(onComplete: (() -> Unit)? = null) {
        dismissWithAnimInternal(onComplete)
    }
}

/**
 * Windows Phone / Zune style Dialog attached flush to the top, left, and right edges of the screen.
 * Opens and closes with a 3D flip animation around its horizontal center axis.
 */
@Composable
fun ZuneFlipDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    confirmButton: (@Composable ZuneFlipDialogScope.() -> Unit)? = null,
    dismissButton: (@Composable ZuneFlipDialogScope.() -> Unit)? = null,
    content: @Composable ZuneFlipDialogScope.() -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val flipAnim = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        flipAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    val dismissWithAnimLambda: (onComplete: (() -> Unit)?) -> Unit = remember(onDismissRequest) {
        { onComplete ->
            if (!isClosing) {
                isClosing = true
                coroutineScope.launch {
                    flipAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                    onComplete?.invoke()
                    onDismissRequest()
                }
            }
        }
    }

    val scope = remember(dismissWithAnimLambda) {
        ZuneFlipDialogScope(dismissWithAnimLambda)
    }

    val isTv = com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current
    val dialogFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (isTv) {
        // A group asked for the focus hands it to its first stop.
        LaunchedEffect(Unit) { dialogFocus.tvTake() }
    }

    if (com.serkantkn.zunelauncher.ui.tv.LocalVista.current) {
        VistaDialog(
            progress = flipAnim.value,
            title = title,
            focus = dialogFocus,
            onDismiss = { scope.dismissWithAnim() },
            buttons = if (confirmButton != null || dismissButton != null) {
                {
                    if (confirmButton != null) scope.confirmButton()
                    if (dismissButton != null) scope.dismissButton()
                }
            } else null
        ) { scope.content() }
        return
    }

    Dialog(
        onDismissRequest = { scope.dismissWithAnim() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f * flipAnim.value))
                    // A tap outside closes it. Not a clickable: that would make the whole scrim
                    // somewhere a TV's remote could stop.
                    .pointerInput(Unit) {
                        detectTapGestures { scope.dismissWithAnim() }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                // Flip Container Card attached flush to Top, Left, Right
                Surface(
                    modifier = Modifier
                        // On a television the remote is put inside the dialog as it opens, on its
                        // first stop - a list's first row, or the button that leaves it be.
                        .then(if (isTv) Modifier.focusRequester(dialogFocus).focusGroup() else Modifier)
                        .fillMaxWidth()
                        .padding(top = 0.dp, start = 0.dp, end = 0.dp, bottom = 24.dp)
                        .swallowTaps()
                        .graphicsLayer {
                            // 3D Flip around horizontal axis (X-axis)
                            val rotation = (1f - flipAnim.value) * -90f
                            rotationX = rotation
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            cameraDistance = 12f * density.density
                            alpha = flipAnim.value.coerceIn(0f, 1f)
                        },
                    shape = RoundedCornerShape(0.dp), // Rectangular attached edges
                    color = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFF2B2B2B),
                    shadowElevation = 16.dp,
                    tonalElevation = 8.dp
                ) {
                    // Never taller than most of the screen: a long list — every hub that can be
                    // added back — used to push the buttons off the bottom. The content scrolls
                    // inside; the title and the buttons stay put.
                    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * DIALOG_MAX_HEIGHT_FRACTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDialogHeight)
                            .padding(top = 28.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
                    ) {
                        if (!title.isNullOrEmpty()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
    
                        // Dialog Content
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            scope.content()
                        }
    
                        // Action Buttons Row
                        if (confirmButton != null || dismissButton != null) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (dismissButton != null) {
                                    scope.dismissButton()
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                if (confirmButton != null) {
                                    scope.confirmButton()
                                }
                            }
                        }
                    }
                }
            }
            // The dialog is a window of its own: a TV's focus frame has to be drawn in it too.
            if (com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current) {
                com.serkantkn.zunelauncher.ui.tv.TvFocusFrame(modifier = Modifier.matchParentSize())
            }
        }
    }
}

/**
 * A dialog as Vista Media Center put one up: a pane of blue glass in the bottom right corner over
 * the page, dimmed behind it; the glass comes first, empty, and the words come into it. Its name in
 * small white capitals, the question under it, the buttons side by side along its foot - the
 * answer that goes ahead first.
 */
@Composable
private fun VistaDialog(
    progress: Float,
    title: String?,
    focus: androidx.compose.ui.focus.FocusRequester,
    onDismiss: () -> Unit,
    buttons: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * progress))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            val words = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 44.dp, bottom = 40.dp)
                    .width(minOf(maxWidth * 0.5f, 560.dp))
                    .heightIn(max = maxHeight * 0.8f)
                    .focusRequester(focus)
                    .focusGroup()
                    .swallowTaps()
                    .graphicsLayer {
                        alpha = progress
                        val grow = 0.92f + 0.08f * progress
                        scaleX = grow
                        scaleY = grow
                        transformOrigin = TransformOrigin(1f, 1f)
                    }
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                com.serkantkn.zunelauncher.ui.tv.Vista.PanelTop.copy(alpha = 0.95f),
                                com.serkantkn.zunelauncher.ui.tv.Vista.PanelBottom.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .vistaGlass(strength = 0.45f)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.graphicsLayer { alpha = words }) {
                    if (!title.isNullOrEmpty()) {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                    if (buttons != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = buttons
                        )
                    }
                }
            }
            com.serkantkn.zunelauncher.ui.tv.TvFocusFrame(modifier = Modifier.matchParentSize())
        }
    }
}

/** How much of the screen a dialog may take before its content scrolls. */
private const val DIALOG_MAX_HEIGHT_FRACTION = 0.85f

/**
 * Windows Phone Metro rectangular stroked button (no solid fill).
 */
@Composable
fun ZuneDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White
) {
    // Vista's button: a wide pane of glass with its word in the middle.
    if (com.serkantkn.zunelauncher.ui.tv.LocalVista.current) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .widthIn(min = 150.dp)
                .height(42.dp)
                .vistaGlass(strength = 0.55f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 18.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = Color.White, maxLines = 1)
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(0.dp), // Rectangular box
        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.85f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = Color.White
        )
    }
}
