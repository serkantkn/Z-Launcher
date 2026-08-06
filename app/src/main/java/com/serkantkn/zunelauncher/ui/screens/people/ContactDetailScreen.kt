package com.serkantkn.zunelauncher.ui.screens.people

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
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
    val isWideScreen = LocalIsWideScreen.current

    var rememberedDetail by remember { mutableStateOf(detail) }
    if (detail != null) {
        rememberedDetail = detail
    }

    if (isWideScreen) {
        // Tablet Mode: Side Card sliding in from the right edge
        AnimatedVisibility(
            visible = detail != null,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(360)
            ) + fadeIn(tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            val displayDetail = rememberedDetail ?: return@AnimatedVisibility

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                // Dimmed backdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onBack() }
                )

                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(420.dp),
                    color = if (zuneColors.isDark) Color(0xFF141414) else Color.White,
                    tonalElevation = 16.dp,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                ) {
                    ContactDetailContent(
                        displayDetail = displayDetail,
                        onBack = onBack,
                        isTabletCard = true
                    )
                }
            }
        }
    } else {
        // Mobile Mode: Rendered inside the 3D hinge container
        if (detail != null || rememberedDetail != null) {
            val displayDetail = detail ?: rememberedDetail!!
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
            ) {
                ContactDetailContent(
                    displayDetail = displayDetail,
                    onBack = onBack,
                    isTabletCard = false
                )
            }
        }
    }
}

@Composable
private fun ContactDetailContent(
    displayDetail: ContactDetailModel,
    onBack: () -> Unit,
    isTabletCard: Boolean
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current

    val fallbackColor = remember(displayDetail.contact.id) {
        val colors = listOf(ZuneColors.Pink, ZuneColors.Orange, ZuneColors.Blue, ZuneColors.Green)
        colors[abs(displayDetail.contact.id.hashCode()) % colors.size]
    }

    val dividerColor = if (zuneColors.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = if (isTabletCard) 24.dp else 48.dp,
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal,
                bottom = 40.dp
            )
    ) {
        // Header Bar (Back / Close)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZuneDimens.SpacingXl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.clickable { onBack() },
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
                    text = "kişi profili",
                    style = MaterialTheme.typography.bodyLarge,
                    color = zuneColors.textMuted
                )
            }

            if (isTabletCard) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = zuneColors.textMuted
                    )
                }
            }
        }

        // Profile Header (Avatar + Name)
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

        // Phone Actions
        if (displayDetail.phoneNumbers.isNotEmpty()) {
            displayDetail.phoneNumbers.forEach { number ->
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
