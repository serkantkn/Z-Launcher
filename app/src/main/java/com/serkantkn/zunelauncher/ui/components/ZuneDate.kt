package com.serkantkn.zunelauncher.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZuneDate(
    modifier: Modifier = Modifier
) {
    var currentDate by remember { mutableStateOf(getCurrentDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentDate = getCurrentDate()
            delay(60_000L)
        }
    }

    Text(
        text = currentDate,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Light
        ),
        color = LocalZuneColors.current.textMuted,
        modifier = modifier
    )
}

private fun getCurrentDate(): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(Date()).lowercase()
}
