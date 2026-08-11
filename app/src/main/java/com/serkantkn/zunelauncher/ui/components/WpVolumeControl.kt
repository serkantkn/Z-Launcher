package com.serkantkn.zunelauncher.ui.components

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.service.VolumeController
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Windows Phone 8.1 / Windows 10 Mobile style Volume Bar.
 * - Standardized 3D Flip entrance & exit animation around X-axis.
 * - Metro typography (e.g. "12/15  MEDYA + UYGULAMALAR").
 * - Authentic Metro sharp/square (0dp corner) slider control.
 * - Expandable dropdown for dual Media & Ringer volume sliders.
 * - Silent / Vibrate / Ringing mode toggle.
 * - Auto-hides after 3 seconds of inactivity.
 */
@Composable
fun WpVolumeControl(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()

    val volumeState by VolumeController.volumeState.collectAsState()

    val flipAnim = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 3D Flip entrance & exit logic
    LaunchedEffect(volumeState.isVisible) {
        if (volumeState.isVisible) {
            isClosing = false
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        } else if (flipAnim.value > 0f && !isClosing) {
            isClosing = true
            flipAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
            isClosing = false
        }
    }

    // Auto-dismiss timer (3s) on key presses or touch
    LaunchedEffect(volumeState.eventId, volumeState.isVisible, isExpanded) {
        if (volumeState.isVisible && !isExpanded) {
            delay(3000L)
            VolumeController.hide()
        }
    }

    if (!volumeState.isVisible && flipAnim.value == 0f && !isClosing) return

    val bannerBgColor = zuneColors.accentColor

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isWideScreen) Alignment.TopEnd else Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (isWideScreen) {
                        Modifier
                            .width(420.dp)
                            .padding(top = statusBarTop + 12.dp, end = 16.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp)
                    }
                )
                .graphicsLayer {
                    // Standardized 3D Flip animation around X-axis (top hinge)
                    val rotation = (1f - flipAnim.value) * -90f
                    rotationX = rotation
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 12f * density
                    alpha = flipAnim.value.coerceIn(0f, 1f)
                },
            shape = RoundedCornerShape(if (isWideScreen) 2.dp else 0.dp),
            color = bannerBgColor,
            shadowElevation = 12.dp,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (isWideScreen) 12.dp else statusBarTop + 8.dp,
                        bottom = 12.dp
                    )
            ) {
                // Header Row: Volume Text + Category Label + Ringer Icon + Dropdown Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Level number (e.g. 12/15)
                        Text(
                            text = "${volumeState.mediaVolume.toString().padStart(2, '0')}/${volumeState.maxMediaVolume}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Category Title
                        Text(
                            text = "MEDYA + UYGULAMALAR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Ringer Mode Toggle Icon
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { VolumeController.toggleRingerMode(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            val ringerIcon = when (volumeState.ringerMode) {
                                AudioManager.RINGER_MODE_SILENT -> Icons.Default.NotificationsOff
                                AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
                                else -> Icons.Default.Notifications
                            }
                            Icon(
                                imageVector = ringerIcon,
                                contentDescription = "Zil Modu",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Expand Dropdown Toggle Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { isExpanded = !isExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Genişlet",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Media Volume Square Slider
                WpSquareSlider(
                    value = volumeState.mediaVolume,
                    maxValue = volumeState.maxMediaVolume,
                    onValueChange = { newVol ->
                        VolumeController.setMediaVolume(context, newVol)
                    }
                )

                // Secondary Slider Section (Ringtone & Notifications) when expanded
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${volumeState.ringerVolume.toString().padStart(2, '0')}/${volumeState.maxRingerVolume}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White
                            )

                            Text(
                                text = "ZİL SESİ + BİLDİRİMLER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        WpSquareSlider(
                            value = volumeState.ringerVolume,
                            maxValue = volumeState.maxRingerVolume,
                            onValueChange = { newVol ->
                                VolumeController.setRingerVolume(context, newVol)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Authentic Windows Phone Metro style Sharp/Square Volume Slider.
 * - Sharp 0dp rectangular track and fill.
 * - Sharp rectangular thumb block.
 * - Supports horizontal drag and tap interactions.
 */
@Composable
private fun WpSquareSlider(
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val fraction = if (maxValue > 0) (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) else 0f

    fun updateValueFromX(xPx: Float) {
        if (widthPx > 0f && maxValue > 0) {
            val newFraction = (xPx / widthPx).coerceIn(0f, 1f)
            val newVol = (newFraction * maxValue + 0.5f).toInt()
            onValueChange(newVol)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onGloballyPositioned { coordinates ->
                widthPx = coordinates.size.width.toFloat()
            }
            .pointerInput(maxValue) {
                detectTapGestures { offset ->
                    updateValueFromX(offset.x)
                }
            }
            .pointerInput(maxValue) {
                detectHorizontalDragGestures { change, _ ->
                    updateValueFromX(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Background (Semi-transparent white bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.35f))
        )

        // Filled Track Progress (Solid white bar)
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(Color.White)
        )

        // Sharp Rectangular Metro Thumb (0dp corner radius, solid white block)
        Box(
            modifier = Modifier
                .padding(
                    start = ((widthPx * fraction) - 6f).coerceAtLeast(0f).let {
                        with(LocalDensity.current) { it.toDp() }
                    }
                )
                .width(12.dp)
                .height(20.dp)
                .background(Color.White)
        )
    }
}
