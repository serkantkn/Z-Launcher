package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSheet() {
    val state = rememberBottomSheetScaffoldState()
    val progress = derivedStateOf {
        try {
            state.bottomSheetState.requireOffset()
        } catch(e: Exception) {
            0f
        }
    }
}
