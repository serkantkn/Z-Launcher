package com.serkantkn.zunelauncher.ui.screens.people

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactDetailModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

@Composable
fun ContactDetailScreen(
    detail: ContactDetailModel?,
    onBack: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current

    // Remember the last non-null detail so exit animation can render content
    var rememberedDetail by remember { mutableStateOf(detail) }
    if (detail != null) {
        rememberedDetail = detail
    }

    AnimatedVisibility(
        visible = detail != null,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(400)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(400)
        )
    ) {
        val displayDetail = rememberedDetail ?: return@AnimatedVisibility

        val fallbackColor = remember(displayDetail.contact.id) {
            val colors = listOf(ZuneColors.Pink, ZuneColors.Orange, ZuneColors.Blue, ZuneColors.Green)
            colors[abs(displayDetail.contact.id.hashCode()) % colors.size]
        }

        val bgColor = if (zuneColors.isDark) Color.Black else ZuneColors.LightBackground
        val dividerColor = if (zuneColors.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = 60.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal,
                        bottom = 80.dp
                    )
            ) {
                // ── Back row ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(bottom = ZuneDimens.SpacingXl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = zuneColors.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "profil",
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textMuted
                    )
                }

                // ── Profile header ──────────────────────────────────────
                // W10M style: Circular photo + large name beside
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (displayDetail.contact.photoUri != null) {
                        AsyncImage(
                            model = displayDetail.contact.photoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(fallbackColor, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(fallbackColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayDetail.contact.name.take(1).uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // W10M uses uppercase bold name
                    Text(
                        text = displayDetail.contact.name.uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                Spacer(modifier = Modifier.height(20.dp))

                // ── Actions section ─────────────────────────────────────
                if (displayDetail.phoneNumbers.isNotEmpty()) {
                    displayDetail.phoneNumbers.forEach { number ->
                        // "Ara" action
                        ActionItem(
                            icon = Icons.Default.Call,
                            label = stringResource(R.string.call),
                            subtitle = number,
                            accentColor = zuneColors.accentColor,
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // "Mesaj gönder" action
                        ActionItem(
                            icon = Icons.Default.Sms,
                            label = stringResource(R.string.message),
                            subtitle = number,
                            accentColor = zuneColors.accentColor,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    Text(
                        text = "telefon numarası yok",
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ── Action Item (W10M style: icon + label + subtitle) ───────────────────────

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZuneColors.current.textDim
            )
        }
    }
}

private fun abs(n: Int): Int = if (n < 0) -n else n
