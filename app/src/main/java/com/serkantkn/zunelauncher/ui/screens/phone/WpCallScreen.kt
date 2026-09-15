package com.serkantkn.zunelauncher.ui.screens.phone

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.service.CallAudioRoute
import com.serkantkn.zunelauncher.data.service.CallManager
import com.serkantkn.zunelauncher.data.service.CallStatus
import com.serkantkn.zunelauncher.util.QuickReply
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Authentic Windows Phone 8 Call Screen.
 * - Incoming Call: Full screen contact photo backdrop with intuitive swipe up (answer) and swipe down (decline) gestures.
 * - Active / Outgoing Call: Top photo backdrop + bottom 3x3 sharp WP8 Metro tile grid.
 * - Supported by Social Hub Notification 3D Flip transition animation.
 */
@Composable
fun WpCallScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current

    val callStatus by CallManager.callStatus.collectAsState()
    val contactName by CallManager.contactName.collectAsState()
    val phoneNumber by CallManager.phoneNumber.collectAsState()
    val photoUri by CallManager.photoUri.collectAsState()
    val callDurationSeconds by CallManager.callDurationSeconds.collectAsState()

    val isMuted by CallManager.isMuted.collectAsState()
    val isSpeakerOn by CallManager.isSpeakerOn.collectAsState()
    val isOnHold by CallManager.isOnHold.collectAsState()
    val isKeypadOpen by CallManager.isKeypadOpen.collectAsState()
    val canHold by CallManager.canHold.collectAsState()
    val audioRoute by CallManager.audioRoute.collectAsState()
    val simLabel by CallManager.simLabel.collectAsState()

    // Without permission to send one, offering to reply with a message would be offering nothing.
    val canReplyWithMessage = remember { QuickReply.canSend(context) }

    var isQuickSmsOpen by remember { mutableStateOf(false) }
    var inCallDialedDigits by remember { mutableStateOf("") }

    BackHandler(enabled = callStatus != CallStatus.IDLE) {
        if (isQuickSmsOpen) {
            isQuickSmsOpen = false
        } else if (isKeypadOpen) {
            CallManager.setKeypadOpen(false)
        } else if (callStatus == CallStatus.INCOMING) {
            CallManager.declineCall()
        } else {
            CallManager.endCall()
        }
    }

    if (callStatus == CallStatus.IDLE) return

    val statusText = when (callStatus) {
        CallStatus.INCOMING -> stringResource(R.string.call_incoming_ellipsis)
        CallStatus.OUTGOING -> stringResource(R.string.call_dialing)
        CallStatus.ACTIVE -> if (isOnHold) stringResource(R.string.call_on_hold) else CallManager.formatDuration(callDurationSeconds)
        CallStatus.ENDED -> stringResource(R.string.call_ended)
        CallStatus.IDLE -> ""
    }

    val currentTime = remember {
        SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())
    }

    val density = LocalDensity.current.density
    val flipAnim = remember { Animatable(0f) }

    LaunchedEffect(callStatus) {
        if (callStatus != CallStatus.IDLE && callStatus != CallStatus.ENDED) {
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        } else if (callStatus == CallStatus.ENDED) {
            flipAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val rotation = (1f - flipAnim.value) * -90f
                rotationX = rotation
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                cameraDistance = 12f * density
                alpha = flipAnim.value.coerceIn(0f, 1f)
            }
            .background(Color.Black)
    ) {
        if (callStatus == CallStatus.INCOMING) {
            // ─── Gelen Çağrı: Tam Ekran Kaydırma Hareketli Çağrı Ekranı ───
            WpIncomingCallSwipeScreen(
                contactName = contactName,
                phoneNumber = phoneNumber,
                photoUri = photoUri,
                currentTime = currentTime,
                simLabel = simLabel,
                canReply = canReplyWithMessage,
                onOpenQuickSms = { isQuickSmsOpen = true }
            )
        } else {
            // ─── Aktif / Giden Çağrı: Üst Fotoğraf + Alt 3x3 WP8 Karo Grid Layout ───
            Column(modifier = Modifier.fillMaxSize()) {

                // ─── 1. TOP SECTION (~70% Height): Full Contact Photo / Backdrop with Overlaid Text ───
                Box(
                    modifier = Modifier
                        .weight(2.0f)
                        .fillMaxWidth()
                ) {
                    val bitmap = remember(photoUri) { loadPhotoBitmap(context, photoUri) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = contactName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            zuneColors.accentColor.copy(alpha = 0.85f),
                                            Color(0xFF0F1B29),
                                            Color.Black
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (contactName.firstOrNull() ?: 'P').uppercaseChar().toString(),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Thin,
                                    fontSize = 120.sp
                                ),
                                color = Color.White.copy(alpha = 0.25f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )

                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 12.dp, end = 20.dp)
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 28.dp, start = 24.dp, end = 24.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 20.sp
                            ),
                            color = zuneColors.accentColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = contactName.ifBlank { phoneNumber },
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 44.sp,
                                lineHeight = 48.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (phoneNumber.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.call_mobile_number, phoneNumber),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 20.sp
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }

                        // Only ever set on a phone with more than one line, where it matters.
                        simLabel?.let { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // ─── 2. BOTTOM SECTION (~30% Height): WP8 Grid Control Tiles ───
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Row 1: speaker, mute, add call
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            WpGridSquareTile(
                                title = stringResource(R.string.call_speaker),
                                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                isActive = isSpeakerOn,
                                enabled = callStatus == CallStatus.ACTIVE,
                                onClick = { CallManager.toggleSpeaker() },
                                modifier = Modifier.weight(1f)
                            )
                            WpGridSquareTile(
                                title = stringResource(R.string.call_mute),
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                isActive = isMuted,
                                enabled = callStatus == CallStatus.ACTIVE,
                                onClick = { CallManager.toggleMute() },
                                modifier = Modifier.weight(1f)
                            )
                            WpGridSquareTile(
                                title = stringResource(R.string.call_add),
                                icon = Icons.Default.PersonAdd,
                                isActive = false,
                                enabled = callStatus == CallStatus.ACTIVE,
                                onClick = {
                                    Toast.makeText(context, context.getString(R.string.call_add_toast), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Row 2: hold, klavye, bluetooth
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            WpGridSquareTile(
                                title = stringResource(R.string.call_hold),
                                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                                isActive = isOnHold,
                                // Not every network lets a call be parked; the button says so.
                                enabled = callStatus == CallStatus.ACTIVE && canHold,
                                onClick = { CallManager.toggleHold() },
                                modifier = Modifier.weight(1f)
                            )
                            WpGridSquareTile(
                                title = stringResource(R.string.phone_keypad),
                                icon = Icons.Default.Dialpad,
                                isActive = isKeypadOpen,
                                enabled = callStatus == CallStatus.ACTIVE,
                                onClick = { CallManager.toggleKeypad() },
                                modifier = Modifier.weight(1f)
                            )
                            WpGridSquareTile(
                                title = "bluetooth",
                                icon = Icons.Default.Bluetooth,
                                isActive = audioRoute == CallAudioRoute.BLUETOOTH,
                                enabled = callStatus == CallStatus.ACTIVE,
                                onClick = {
                                    // Telecom decides whether there is a headset to route to; if
                                    // there is not, the route simply stays where it was.
                                    CallManager.setAudioRoute(
                                        if (audioRoute == CallAudioRoute.BLUETOOTH) {
                                            CallAudioRoute.EARPIECE
                                        } else {
                                            CallAudioRoute.BLUETOOTH
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Row 3: end call (kapat - 2/3 width), dialpad toggle (1/3 width)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                color = zuneColors.accentColor,
                                modifier = Modifier
                                    .weight(2f)
                                    .fillMaxHeight()
                                    .clickable { CallManager.endCall() }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = stringResource(R.string.call_hang_up),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 19.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            WpGridSquareTile(
                                title = "",
                                icon = Icons.Default.Dialpad,
                                isActive = isKeypadOpen,
                                enabled = callStatus == CallStatus.ACTIVE,
                                onClick = { CallManager.toggleKeypad() },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        // ─── 3. In-Call Keypad Overlay (Slide-Up Dialpad) ───
        AnimatedVisibility(
            visible = isKeypadOpen && callStatus == CallStatus.ACTIVE,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                color = Color(0xFF141414),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.phone_keypad),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.call_hang_up),
                            style = MaterialTheme.typography.titleMedium,
                            color = zuneColors.accentColor,
                            modifier = Modifier.clickable { CallManager.setKeypadOpen(false) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = inCallDialedDigits,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("*", "0", "#")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { digit ->
                                    Surface(
                                        color = Color(0xFF242424),
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(64.dp)
                                            .clickable {
                                                inCallDialedDigits += digit
                                                // The tone goes down the line, not just on screen.
                                                CallManager.playDtmf(digit.first())
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = digit,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 26.sp
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = zuneColors.accentColor,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(64.dp)
                            .clickable { CallManager.endCall() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.common_close_cap),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // ─── 4. Quick SMS Reply Dialog ───
        if (isQuickSmsOpen) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isQuickSmsOpen = false }
            ) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        color = Color(0xFF222222),
                        border = androidx.compose.foundation.BorderStroke(1.dp, zuneColors.accentColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.call_reply_message),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                                color = zuneColors.accentColor,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val messages = listOf(
                                stringResource(R.string.call_quick_1),
                                stringResource(R.string.call_quick_2),
                                stringResource(R.string.call_quick_3),
                                stringResource(R.string.call_quick_4)
                            )

                            messages.forEach { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isQuickSmsOpen = false
                                            // The caller is told off the line first; the message
                                            // itself is sent by CallManager, which outlives this
                                            // screen, and says afterwards whether it went.
                                            val who = contactName.ifBlank { phoneNumber }
                                            CallManager.declineWithMessage(context, msg) { sent ->
                                                Toast.makeText(
                                                    context,
                                                    if (sent) {
                                                        context.getString(R.string.call_reply_sent, who)
                                                    } else {
                                                        context.getString(R.string.call_reply_failed)
                                                    },
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                        .padding(vertical = 12.dp)
                                )
                                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.1f)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen Incoming Call screen with intuitive vertical swipe gestures.
 * Swipe UP -> Accept call
 * Swipe DOWN -> Decline call
 */
@Composable
private fun WpIncomingCallSwipeScreen(
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    currentTime: String,
    simLabel: String?,
    canReply: Boolean,
    onOpenQuickSms: () -> Unit
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
    val bounceAnim = remember { Animatable(0f) }

    // Periodic bounce loop: Photo bounces upward by -70dp every 2.4 seconds to reveal actions underneath
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2400L)
            if (dragOffsetY.value == 0f) {
                bounceAnim.animateTo(-70f, tween(320, easing = FastOutSlowInEasing))
                bounceAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
    }

    val totalOffsetY = dragOffsetY.value + bounceAnim.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── UNDERNEATH LAYER: Revealed when photo bounces or is dragged ───

        // 1. Revealed at Top (Decline call action when dragged DOWN)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = stringResource(R.string.call_ignore_cap),
                tint = Color(0xFFC0392B),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.call_ignore),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                color = Color(0xFFC0392B)
            )
        }

        // 2. Revealed at Bottom (Accept call action when bounced UP or dragged UP)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = stringResource(R.string.call_answer_cap),
                tint = Color(0xFF27AE60),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.call_answer),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                color = Color(0xFF27AE60)
            )
        }

        // ─── FOREGROUND PHOTO CARD: Draggable & Bouncing Layer ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = totalOffsetY
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY.value < -160f) {
                                CallManager.answerCall()
                            } else if (dragOffsetY.value > 160f) {
                                CallManager.declineCall()
                            } else {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                dragOffsetY.snapTo(dragOffsetY.value + dragAmount)
                            }
                        }
                    )
                }
        ) {
            val bitmap = remember(photoUri) { loadPhotoBitmap(context, photoUri) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = contactName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    zuneColors.accentColor.copy(alpha = 0.85f),
                                    Color(0xFF0F1B29),
                                    Color.Black
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (contactName.firstOrNull() ?: 'P').uppercaseChar().toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Thin,
                            fontSize = 140.sp
                        ),
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            // Dark gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Top Right Clock ("4:30")
            Text(
                text = currentTime,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp)
            )

            // Top Left Contact Info Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 28.dp, start = 24.dp, end = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.call_incoming_ellipsis),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 20.sp
                    ),
                    color = zuneColors.accentColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = contactName.ifBlank { phoneNumber },
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (phoneNumber.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.call_mobile_number, phoneNumber),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }

                // Which of the phone's two numbers is being rung; null on a phone with one.
                simLabel?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Bottom Reply with SMS Action Bar
            if (canReply) Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(0.85f)
                    .clickable { onOpenQuickSms() }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = stringResource(R.string.call_reply_message_cap),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.call_reply_message),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Authentic WP8 Call Screen Control Tile (#333333 dark tile).
 */
@Composable
private fun WpGridSquareTile(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    enabled: Boolean = true,
    activeColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val tileBgColor = when {
        !enabled -> Color(0xFF1E1E1E)
        isActive -> activeColor ?: zuneColors.accentColor
        else -> Color(0xFF333333)
    }

    Surface(
        color = tileBgColor,
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled) { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
            if (title.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun loadPhotoBitmap(context: Context, uriString: String?): android.graphics.Bitmap? {
    if (uriString.isNull_or_blank()) return null
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.trim().isEmpty()
}
