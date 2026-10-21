package com.serkantkn.zeta.ui.screens.settings

import com.serkantkn.zeta.ui.components.HubGround
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.serkantkn.zeta.BuildConfig
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.ReleaseNotes
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import com.serkantkn.zeta.util.ReleaseNote
import com.serkantkn.zeta.util.releaseHistory
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The launcher itself, opened from the version line on the about page: its icon, its name and
 * which build this is, and under them what every version so far brought, newest first.
 */
@Composable
fun AboutVersionScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    // The icon the phone shows for the launcher, whichever shape it is drawn in.
    val icon: ImageBitmap? = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap()
        }.getOrNull()
    }
    val history = remember { releaseHistory(ReleaseNotes.ALL, BuildConfig.VERSION_CODE) }

    BackHandler { onClose() }

    HubGround(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal, top = 24.dp, bottom = 48.dp)
                .then(if (isWideScreen) Modifier.widthIn(max = 720.dp) else Modifier)
        ) {
            Text(
                text = stringResource(R.string.settings_breadcrumb_about),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.clickable { onClose() }.padding(bottom = 28.dp)
            )

            // The launcher: its icon beside its name and the build.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(88.dp))
                    Spacer(modifier = Modifier.width(20.dp))
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 36.sp, letterSpacing = (-0.5).sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = zuneColors.textMuted,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_about_inspired),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 18.dp)
            )

            Text(
                text = stringResource(R.string.settings_release_notes),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 40.dp, bottom = 8.dp)
            )
            history.forEach { note -> ReleaseNoteBlock(note) }
        }
    }
}

/** One version: its name and day, what it was, and what it brought, a line each. */
@Composable
private fun ReleaseNoteBlock(note: ReleaseNote) {
    val zuneColors = LocalZuneColors.current
    val day = remember(note.released) {
        runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(note.released)
            SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(parsed!!).lowercase(Locale.getDefault())
        }.getOrDefault("")
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = note.name.ifBlank { note.version.toString() },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp),
                color = zuneColors.accentColor,
                maxLines = 1
            )
            if (day.isNotBlank()) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }
        }
        Text(
            text = stringResource(note.titleRes),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            note.lineRes.forEach { line ->
                Row {
                    // Windows Phone's lists marked a line with a small square, not a round bullet.
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 12.dp)
                            .size(6.dp)
                            .background(zuneColors.accentColor)
                    )
                    Text(
                        text = stringResource(line),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
