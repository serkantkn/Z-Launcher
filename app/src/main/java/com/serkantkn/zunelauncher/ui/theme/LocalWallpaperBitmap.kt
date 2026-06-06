package com.serkantkn.zunelauncher.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntSize

import androidx.compose.ui.layout.LayoutCoordinates

val LocalWallpaperBitmap = compositionLocalOf<Bitmap?> { null }
val LocalBlurredWallpaperBitmap = compositionLocalOf<Bitmap?> { null }
val LocalBackgroundSize = compositionLocalOf { IntSize.Zero }
val LocalBackgroundCoordinates = compositionLocalOf<LayoutCoordinates?> { null }
