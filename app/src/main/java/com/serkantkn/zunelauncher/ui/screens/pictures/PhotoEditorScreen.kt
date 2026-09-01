package com.serkantkn.zunelauncher.ui.screens.pictures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class EditorTool(val label: String) {
    CROP("kırp"),
    ROTATE("döndür"),
    MIRROR("aynala")
}

private enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("serbest", null),
    SQUARE("1:1 kare", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f)
}

/**
 * Windows Phone Metro styled Photo Editor with classic App Bar,
 * crisp typography, rule-of-thirds crop handles, rotation, mirroring and lossless export.
 */
@Composable
fun PhotoEditorScreen(
    photo: MediaImage,
    onSave: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val coroutineScope = rememberCoroutineScope()
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Transformation States
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    // Active tool & crop states
    var activeTool by remember { mutableStateOf(EditorTool.CROP) }
    var selectedAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }

    // Crop box normalized coordinates (0f..1f relative to displayed image)
    var cropRectNorm by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }

    // Load original bitmap from URI with proper EXIF orientation handling
    LaunchedEffect(photo.uri) {
        isLoading = true
        originalBitmap = withContext(Dispatchers.IO) {
            decodeBitmapWithExif(context, photo.uri)
        }
        isLoading = false
    }

    // Update crop rect when aspect ratio changes
    LaunchedEffect(selectedAspectRatio, rotationAngle) {
        val ratio = selectedAspectRatio.ratio
        if (ratio == null) {
            cropRectNorm = Rect(0f, 0f, 1f, 1f)
        } else {
            val isRotated90 = (rotationAngle.toInt() % 180 != 0)
            val bmpW = if (isRotated90) originalBitmap?.height ?: 1000 else originalBitmap?.width ?: 1000
            val bmpH = if (isRotated90) originalBitmap?.width ?: 1000 else originalBitmap?.height ?: 1000
            val imageRatio = bmpW.toFloat() / bmpH.toFloat()

            if (ratio >= imageRatio) {
                val newH = (1f / (ratio / imageRatio)).coerceIn(0.1f, 1f)
                val top = (1f - newH) / 2f
                cropRectNorm = Rect(0f, top, 1f, top + newH)
            } else {
                val newW = (ratio / imageRatio).coerceIn(0.1f, 1f)
                val left = (1f - newW) / 2f
                cropRectNorm = Rect(left, 0f, left + newW, 1f)
            }
        }
    }

    fun resetAll() {
        rotationAngle = 0f
        flipHorizontal = false
        flipVertical = false
        selectedAspectRatio = CropAspectRatio.FREE
        cropRectNorm = Rect(0f, 0f, 1f, 1f)
    }

    fun applyAndExport() {
        val src = originalBitmap ?: return
        isSaving = true
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val matrix = Matrix().apply {
                    postScale(
                        if (flipHorizontal) -1f else 1f,
                        if (flipVertical) -1f else 1f
                    )
                    postRotate(rotationAngle)
                }

                val transformedBitmap = Bitmap.createBitmap(
                    src,
                    0,
                    0,
                    src.width,
                    src.height,
                    matrix,
                    true
                )

                // Calculate crop pixel bounds
                val cropLeftPx = (cropRectNorm.left * transformedBitmap.width).toInt().coerceIn(0, transformedBitmap.width - 1)
                val cropTopPx = (cropRectNorm.top * transformedBitmap.height).toInt().coerceIn(0, transformedBitmap.height - 1)
                val cropWidthPx = (cropRectNorm.width * transformedBitmap.width).toInt().coerceIn(1, transformedBitmap.width - cropLeftPx)
                val cropHeightPx = (cropRectNorm.height * transformedBitmap.height).toInt().coerceIn(1, transformedBitmap.height - cropTopPx)

                val finalBitmap = Bitmap.createBitmap(
                    transformedBitmap,
                    cropLeftPx,
                    cropTopPx,
                    cropWidthPx,
                    cropHeightPx
                )

                withContext(Dispatchers.Main) {
                    isSaving = false
                    onSave(finalBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSaving = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── WINDOWS PHONE METRO HEADER ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FOTOĞRAFLAR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        ),
                        color = zuneColors.accentColor
                    )
                    Text(
                        text = "düzenle",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 42.sp,
                            letterSpacing = (-1.5).sp,
                            lineHeight = 44.sp
                        ),
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ─── MAIN PREVIEW & CROP CANVAS ───
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading || originalBitmap == null) {
                    CircularProgressIndicator(color = zuneColors.accentColor)
                } else {
                    val bmp = originalBitmap!!

                    // Preview transformed bitmap
                    val previewBitmap = remember(bmp, rotationAngle, flipHorizontal, flipVertical) {
                        val matrix = Matrix().apply {
                            postScale(
                                if (flipHorizontal) -1f else 1f,
                                if (flipVertical) -1f else 1f
                            )
                            postRotate(rotationAngle)
                        }
                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    }

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxWidthPx = constraints.maxWidth.toFloat()
                        val maxHeightPx = constraints.maxHeight.toFloat()

                        val imageAspect = previewBitmap.width.toFloat() / previewBitmap.height.toFloat()
                        val containerAspect = maxWidthPx / maxHeightPx

                        val displayedWidthPx: Float
                        val displayedHeightPx: Float
                        if (imageAspect > containerAspect) {
                            displayedWidthPx = maxWidthPx
                            displayedHeightPx = maxWidthPx / imageAspect
                        } else {
                            displayedHeightPx = maxHeightPx
                            displayedWidthPx = maxHeightPx * imageAspect
                        }

                        Box(
                            modifier = Modifier
                                .size(
                                    width = (displayedWidthPx / LocalContext.current.resources.displayMetrics.density).dp,
                                    height = (displayedHeightPx / LocalContext.current.resources.displayMetrics.density).dp
                                )
                        ) {
                            // Base Image
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Önizleme",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Interactive Crop Overlay with Windows Phone styled 3x3 Grid
                            CropOverlay(
                                cropRectNorm = cropRectNorm,
                                onCropRectChange = { cropRectNorm = it }
                            )
                        }
                    }
                }
            }

            // ─── SUB-TOOL OPTIONS BAR (Metro Horizontal Typography Chips) ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (activeTool) {
                    EditorTool.CROP -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CropAspectRatio.entries.forEach { ratioOption ->
                                val isSelected = selectedAspectRatio == ratioOption
                                Text(
                                    text = ratioOption.label,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 15.sp
                                    ),
                                    color = if (isSelected) zuneColors.accentColor else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .clickable { selectedAspectRatio = ratioOption }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    EditorTool.ROTATE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WpSubToolTextButton(
                                icon = Icons.AutoMirrored.Filled.RotateLeft,
                                label = "90° sola",
                                onClick = {
                                    rotationAngle = (rotationAngle - 90f + 360f) % 360f
                                }
                            )
                            WpSubToolTextButton(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                label = "90° sağa",
                                onClick = {
                                    rotationAngle = (rotationAngle + 90f) % 360f
                                }
                            )
                            WpSubToolTextButton(
                                icon = Icons.Default.Rotate90DegreesCw,
                                label = "180° çevir",
                                onClick = {
                                    rotationAngle = (rotationAngle + 180f) % 360f
                                }
                            )
                        }
                    }

                    EditorTool.MIRROR -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WpSubToolTextButton(
                                icon = Icons.Default.Flip,
                                label = "yatay aynala ↔",
                                isSelected = flipHorizontal,
                                onClick = { flipHorizontal = !flipHorizontal }
                            )
                            WpSubToolTextButton(
                                icon = Icons.Default.Flip,
                                label = "dikey aynala ↕",
                                isSelected = flipVertical,
                                onClick = { flipVertical = !flipVertical }
                            )
                        }
                    }
                }
            }

            // ─── STANDARD WINDOWS PHONE BOTTOM APPLICATION BAR ───
            WindowsPhoneBottomBar(
                actions = listOf(
                    WpBarAction(
                        icon = Icons.Default.Check,
                        label = if (isSaving) "kaydediliyor" else "kaydet",
                        onClick = { if (!isSaving) applyAndExport() }
                    ),
                    WpBarAction(
                        icon = Icons.Default.Crop,
                        label = "kırp",
                        onClick = { activeTool = EditorTool.CROP }
                    ),
                    WpBarAction(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "döndür",
                        onClick = { activeTool = EditorTool.ROTATE }
                    ),
                    WpBarAction(
                        icon = Icons.Default.Flip,
                        label = "aynala",
                        onClick = { activeTool = EditorTool.MIRROR }
                    )
                ),
                menuItems = listOf(
                    WpBarMenuItem(
                        text = "tümünü sıfırla",
                        onClick = { resetAll() }
                    ),
                    WpBarMenuItem(
                        text = "iptal et",
                        onClick = onCancel
                    )
                )
            )
        }
    }
}

@Composable
private fun WpSubToolTextButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) zuneColors.accentColor else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            ),
            color = if (isSelected) zuneColors.accentColor else Color.White.copy(alpha = 0.85f)
        )
    }
}

/**
 * Interactive Windows Phone styled Crop Frame with rule-of-thirds grid and sharp corner handles
 */
@Composable
private fun CropOverlay(
    cropRectNorm: Rect,
    onCropRectChange: (Rect) -> Unit
) {
    val currentCrop by rememberUpdatedState(cropRectNorm)
    val currentOnChange by rememberUpdatedState(onCropRectChange)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density = LocalContext.current.resources.displayMetrics.density

        val leftPx = cropRectNorm.left * widthPx
        val topPx = cropRectNorm.top * heightPx
        val rightPx = cropRectNorm.right * widthPx
        val bottomPx = cropRectNorm.bottom * heightPx

        val cropWidthPx = rightPx - leftPx
        val cropHeightPx = bottomPx - topPx

        // Dimmed area and rule-of-thirds lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw dimmed outer regions
            // Top
            drawRect(
                color = Color.Black.copy(alpha = 0.58f),
                topLeft = Offset(0f, 0f),
                size = Size(widthPx, topPx)
            )
            // Bottom
            drawRect(
                color = Color.Black.copy(alpha = 0.58f),
                topLeft = Offset(0f, bottomPx),
                size = Size(widthPx, heightPx - bottomPx)
            )
            // Left
            drawRect(
                color = Color.Black.copy(alpha = 0.58f),
                topLeft = Offset(0f, topPx),
                size = Size(leftPx, cropHeightPx)
            )
            // Right
            drawRect(
                color = Color.Black.copy(alpha = 0.58f),
                topLeft = Offset(rightPx, topPx),
                size = Size(widthPx - rightPx, cropHeightPx)
            )

            // Crop Rectangle Border
            drawRect(
                color = Color.White,
                topLeft = Offset(leftPx, topPx),
                size = Size(cropWidthPx, cropHeightPx),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Rule of Thirds Grid Lines inside crop box
            val oneThirdW = cropWidthPx / 3f
            val oneThirdH = cropHeightPx / 3f

            // Vertical grid lines
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(leftPx + oneThirdW, topPx),
                end = Offset(leftPx + oneThirdW, bottomPx),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(leftPx + oneThirdW * 2f, topPx),
                end = Offset(leftPx + oneThirdW * 2f, bottomPx),
                strokeWidth = 1.dp.toPx()
            )

            // Horizontal grid lines
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(leftPx, topPx + oneThirdH),
                end = Offset(rightPx, topPx + oneThirdH),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(leftPx, topPx + oneThirdH * 2f),
                end = Offset(rightPx, topPx + oneThirdH * 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draggable Crop Box (Body Pan)
        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(
                    width = (cropWidthPx / density).dp,
                    height = (cropHeightPx / density).dp
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dxNorm = dragAmount.x / widthPx
                        val dyNorm = dragAmount.y / heightPx

                        val cur = currentCrop
                        val curW = cur.width
                        val curH = cur.height

                        val newLeft = (cur.left + dxNorm).coerceIn(0f, 1f - curW)
                        val newTop = (cur.top + dyNorm).coerceIn(0f, 1f - curH)

                        currentOnChange(
                            Rect(
                                left = newLeft,
                                top = newTop,
                                right = newLeft + curW,
                                bottom = newTop + curH
                            )
                        )
                    }
                }
        )

        // Top-Left Corner Handle
        CornerHandle(
            x = leftPx,
            y = topPx,
            onDelta = { dx, dy ->
                val cur = currentCrop
                val newLeft = (cur.left + dx / widthPx).coerceIn(0f, cur.right - 0.1f)
                val newTop = (cur.top + dy / heightPx).coerceIn(0f, cur.bottom - 0.1f)
                currentOnChange(Rect(newLeft, newTop, cur.right, cur.bottom))
            }
        )

        // Top-Right Corner Handle
        CornerHandle(
            x = rightPx,
            y = topPx,
            onDelta = { dx, dy ->
                val cur = currentCrop
                val newRight = (cur.right + dx / widthPx).coerceIn(cur.left + 0.1f, 1f)
                val newTop = (cur.top + dy / heightPx).coerceIn(0f, cur.bottom - 0.1f)
                currentOnChange(Rect(cur.left, newTop, newRight, cur.bottom))
            }
        )

        // Bottom-Left Corner Handle
        CornerHandle(
            x = leftPx,
            y = bottomPx,
            onDelta = { dx, dy ->
                val cur = currentCrop
                val newLeft = (cur.left + dx / widthPx).coerceIn(0f, cur.right - 0.1f)
                val newBottom = (cur.bottom + dy / heightPx).coerceIn(cur.top + 0.1f, 1f)
                currentOnChange(Rect(newLeft, cur.top, cur.right, newBottom))
            }
        )

        // Bottom-Right Corner Handle
        CornerHandle(
            x = rightPx,
            y = bottomPx,
            onDelta = { dx, dy ->
                val cur = currentCrop
                val newRight = (cur.right + dx / widthPx).coerceIn(cur.left + 0.1f, 1f)
                val newBottom = (cur.bottom + dy / heightPx).coerceIn(cur.top + 0.1f, 1f)
                currentOnChange(Rect(cur.left, cur.top, newRight, newBottom))
            }
        )
    }
}

@Composable
private fun CornerHandle(
    x: Float,
    y: Float,
    onDelta: (dx: Float, dy: Float) -> Unit
) {
    val handleTouchSize = 56.dp
    val density = LocalContext.current.resources.displayMetrics.density
    val halfTouchPx = 28f * density
    val currentOnDelta by rememberUpdatedState(onDelta)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - halfTouchPx).roundToInt(),
                    (y - halfTouchPx).roundToInt()
                )
            }
            .size(handleTouchSize)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDelta(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color.White)
                .border(2.dp, Color.Black)
        )
    }
}

/**
 * Decodes a bitmap from URI and applies EXIF orientation if needed so portrait photos stay portrait.
 */
private fun decodeBitmapWithExif(context: android.content.Context, uri: android.net.Uri): Bitmap? {
    return try {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = android.media.ExifInterface(stream)
            exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return rawBitmap
        }

        val orientedBitmap = Bitmap.createBitmap(
            rawBitmap,
            0,
            0,
            rawBitmap.width,
            rawBitmap.height,
            matrix,
            true
        )
        if (orientedBitmap != rawBitmap) {
            rawBitmap.recycle()
        }
        orientedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
