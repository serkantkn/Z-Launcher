package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * Standalone Metro style Date & Time Settings screen with 3D Hinge transition.
 */
@Composable
fun DateTimeSettingsScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val timeFormat by viewModel.timeFormat.collectAsState()
    val dateFormat by viewModel.dateFormat.collectAsState()

    BackHandler { onClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── Top Header Area (WP style) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        top = 24.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                // Small breadcrumb: "ayarlar > sistem" (clickable to go back)
                Text(
                    text = "ayarlar > sistem",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f)
                    else Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(bottom = 4.dp)
                )

                // Giant page title
                Text(
                    text = "tarih+saat ayarları",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 42.sp,
                        letterSpacing = (-1).sp,
                        lineHeight = 46.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "ana ekrandaki saat ve tarih gösterim formatını seçin",
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                    .navigationBarsPadding()
            ) {
                SettingGroup(title = "saat formatı") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingChoiceRow(
                            title = "24 saat",
                            subtitle = "14:30",
                            selected = timeFormat == "HH:mm",
                            onClick = { viewModel.setTimeFormat("HH:mm") }
                        )
                        SettingChoiceRow(
                            title = "12 saat (AM/PM)",
                            subtitle = "02:30 PM",
                            selected = timeFormat == "hh:mm a",
                            onClick = { viewModel.setTimeFormat("hh:mm a") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingGroup(title = "tarih formatı") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingChoiceRow(
                            title = "gün, gün ay",
                            subtitle = "çarşamba, 12 ağustos",
                            selected = dateFormat == "EEEE, MMMM d",
                            onClick = { viewModel.setDateFormat("EEEE, MMMM d") }
                        )
                        SettingChoiceRow(
                            title = "gün ay, gün",
                            subtitle = "12 ağustos, çarşamba",
                            selected = dateFormat == "d MMMM, EEEE",
                            onClick = { viewModel.setDateFormat("d MMMM, EEEE") }
                        )
                        SettingChoiceRow(
                            title = "gün ay yıl",
                            subtitle = "12 ağustos 2026",
                            selected = dateFormat == "d MMMM yyyy",
                            onClick = { viewModel.setDateFormat("d MMMM yyyy") }
                        )
                        SettingChoiceRow(
                            title = "sayısal",
                            subtitle = "12/08/2026",
                            selected = dateFormat == "dd/MM/yyyy",
                            onClick = { viewModel.setDateFormat("dd/MM/yyyy") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
