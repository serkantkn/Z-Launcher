package com.serkantkn.zunelauncher.ui.components

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.TimeText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Windows Phone action center, as it comes down over whatever is on screen.
 *
 * This is the first cut: the clock, the date and the battery across the top, the way to all
 * settings, and the bar at the bottom to push it back up. Notifications, the quick actions and
 * the music card come next.
 *
 * The sheet places itself from [progress] (0 away, 1 open) so the finger on the status bar and
 * the finger on the sheet move the same thing.
 */
@Composable
fun ActionCenterPanel(
    progress: () -> Float,
    onDragBy: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onClose: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val sheet = if (zuneColors.isDark) Color(0xFF1B1B1B) else Color(0xFFF2F2F2)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val battery = remember(now / 30_000L) {
        (context.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }
    // The launcher may speak another language than the phone; the panel speaks the launcher's.
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val date = remember(now / 60_000L, locale) {
        SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now)).lowercase(locale)
    }

    // Behind the sheet the screen dims as it comes down; a tap there puts it away.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress() }
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -(1f - progress()) * size.height }
            .background(sheet)
            // The sheet takes its own taps so they do not fall through to the scrim.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            .draggable(
                state = rememberDraggableState { delta -> onDragBy(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> onDragEnd(velocity) }
            )
            .padding(top = statusTop + 12.dp, bottom = navBottom)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = TimeText.format(now, locale = locale),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 44.sp),
                color = ink
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = ink.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 8.dp).weight(1f)
            )
            battery?.let {
                Text(
                    text = java.text.NumberFormat.getPercentInstance(locale).format(it / 100.0),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.action_center_all_settings),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
            color = zuneColors.accentColor,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onClose()
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.SETTINGS.name)
                    )
                }
        )

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(ink.copy(alpha = 0.12f))
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(R.string.action_center_empty),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                color = ink.copy(alpha = 0.6f)
            )
        }

        // The bar to push it back up, as Windows Phone drew it at the foot of the sheet.
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(width = 48.dp, height = 4.dp).background(ink.copy(alpha = 0.45f)))
        }
    }
}
