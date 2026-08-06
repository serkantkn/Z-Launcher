package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Dialog(
        onDismissRequest = { scope.dismissWithAnim() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f * flipAnim.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { scope.dismissWithAnim() }
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            // Flip Container Card attached flush to Top, Left, Right
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, start = 0.dp, end = 0.dp, bottom = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevents closing when tapping inside dialog card
                    )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    scope.content()

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
    }
}

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
