package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable

@Composable
fun TestLerp() {
    val a: TextUnit = 16.sp
    val b: TextUnit = 24.sp
    val c = lerp(a, b, 0.5f)
}
