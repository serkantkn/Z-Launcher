package com.serkantkn.zunelauncher.ui.screens.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.LockScreenMode
import com.serkantkn.zunelauncher.ui.components.ZuneClock
import com.serkantkn.zunelauncher.ui.components.ZuneDate
import com.serkantkn.zunelauncher.ui.components.ZuneWallpaperOverlay
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.components.ZuneBackground
import com.serkantkn.zunelauncher.ui.components.BackgroundMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LockScreenContent(
    mode: LockScreenMode,
    onUnlockRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    val customPin by settingsDataStore.customPin.collectAsState(initial = null)

    var biometricTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        if (mode == LockScreenMode.SAFE_MODE && !biometricTriggered) {
            biometricTriggered = true
            val activity = context as? FragmentActivity
            activity?.let { act ->
                val executor = ContextCompat.getMainExecutor(act)
                val biometricPrompt = BiometricPrompt(act, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            // If user cancels or it errors, they can swipe up to use system fallback
                        }
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onUnlockRequested() // Proceed to unlock and dismiss keyguard
                        }
                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Kilit Ekranı")
                    .setSubtitle("Kilidi açmak için parmak izinizi kullanın")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    ZuneBackground(mode = BackgroundMode.WALLPAPER) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
            val screenHeight = constraints.maxHeight.toFloat()
        val halfScreen = screenHeight / 2f
        
        val dragOffsetY = remember { Animatable(0f) }
        val pinPadOffsetY = remember { Animatable(halfScreen) } // Pin pad starts hidden below

        var isPinVisible by remember { mutableStateOf(false) }
        var enteredPin by remember { mutableStateOf("") }
        var isUnlocking by remember { mutableStateOf(false) }

        // PIN Pad Layer (Bottom Half)
        val requiresPin = mode == LockScreenMode.PIN_MODE && !customPin.isNullOrEmpty()
        if ((isPinVisible && requiresPin) || (isUnlocking && requiresPin)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeight / 2)
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, pinPadOffsetY.value.roundToInt()) }
                    .background(Color.Black.copy(alpha = 0.85f))
            ) {
                PinPad(
                    enteredPin = enteredPin,
                    onNumberClick = { num ->
                        if (enteredPin.length < 4 && !isUnlocking) {
                            enteredPin += num
                            if (enteredPin.length == 4) {
                                if (enteredPin == customPin) {
                                    isUnlocking = true
                                    scope.launch {
                                        // Animate unlock
                                        launch { dragOffsetY.animateTo(-screenHeight, tween(500)) }
                                        launch { pinPadOffsetY.animateTo(halfScreen, tween(500)) }
                                        onUnlockRequested()
                                    }
                                } else {
                                    // Wrong PIN, shake and reset
                                    scope.launch {
                                        enteredPin = ""
                                        // Add a small shake animation if desired
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (enteredPin.isNotEmpty() && !isUnlocking) {
                            enteredPin = enteredPin.dropLast(1)
                        }
                    }
                )
            }
        }

        // Main Lock Screen Cover Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (!isUnlocking) {
                            val newValue = dragOffsetY.value + delta
                            // Restrict dragging between -halfScreen and 0
                            val clampedValue = newValue.coerceIn(-halfScreen, 0f)
                            scope.launch { dragOffsetY.snapTo(clampedValue) }
                            
                            // Parallax effect for PIN pad (slides up as cover slides up)
                            val pinPadNewY = halfScreen + clampedValue
                            scope.launch { pinPadOffsetY.snapTo(pinPadNewY) }
                            
                            isPinVisible = clampedValue < -10f
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        if (!isUnlocking) {
                            if (dragOffsetY.value < -halfScreen / 3) {
                                // Snap to half open
                                scope.launch {
                                    launch { dragOffsetY.animateTo(-halfScreen, tween(300)) }
                                    launch { pinPadOffsetY.animateTo(0f, tween(300)) }
                                }
                                isPinVisible = true
                                
                                if (mode == LockScreenMode.SAFE_MODE) {
                                    isUnlocking = true
                                    scope.launch {
                                        launch { dragOffsetY.animateTo(-screenHeight, tween(300)) }
                                        onUnlockRequested() // This triggers system fallback
                                    }
                                } else if (mode == LockScreenMode.PIN_MODE && customPin.isNullOrEmpty()) {
                                    isUnlocking = true
                                    scope.launch {
                                        launch { dragOffsetY.animateTo(-screenHeight, tween(300)) }
                                        onUnlockRequested() // Directly unlock if no PIN is set
                                    }
                                }
                            } else {
                                // Snap back to fully closed
                                scope.launch {
                                    launch { dragOffsetY.animateTo(0f, tween(300)) }
                                    launch { pinPadOffsetY.animateTo(halfScreen, tween(300)) }
                                }
                            }
                        }
                    }
                )
        ) {
            // Background Wallpaper
            ZuneWallpaperOverlay()

            // Time and Date
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                ZuneClock()
                ZuneDate(
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // Hint text
                Text(
                    text = "Açmak için yukarı kaydırın",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        }
    }
}
}

@Composable
fun PinPad(
    enteredPin: String,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            if (isFilled) LocalZuneColors.current.accentColor else Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                )
            }
        }

        // Numpad Grid
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = key.isNotEmpty()
                            ) {
                                if (key == "DEL") onDeleteClick() else onNumberClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Text(
                                text = "sil",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        } else if (key.isNotEmpty()) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
