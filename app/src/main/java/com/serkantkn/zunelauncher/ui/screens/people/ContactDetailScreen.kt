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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
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
    onBack: () -> Unit,
    onOpenMessaging: ((contactName: String, phoneNumber: String) -> Unit)? = null,
    onUpdateContact: ((contactId: String, firstName: String, lastName: String, phoneNumber: String) -> Unit)? = null,
    onDeleteContact: ((contactId: String) -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current

    var rememberedDetail by remember { mutableStateOf(detail) }
    if (detail != null) {
        rememberedDetail = detail
    }

    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentDisplayDetail = detail ?: rememberedDetail

    if (currentDisplayDetail != null) {
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
                            displayDetail = currentDisplayDetail,
                            onBack = onBack,
                            onOpenMessaging = onOpenMessaging,
                            onEditClick = { showEditSheet = true },
                            onDeleteClick = { showDeleteConfirmDialog = true },
                            isTabletCard = true
                        )
                    }
                }
            }
        } else {
            // Mobile Mode: Rendered inside the 3D hinge container
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
            ) {
                ContactDetailContent(
                    displayDetail = currentDisplayDetail,
                    onBack = onBack,
                    onOpenMessaging = onOpenMessaging,
                    onEditClick = { showEditSheet = true },
                    onDeleteClick = { showDeleteConfirmDialog = true },
                    isTabletCard = false
                )
            }
        }
    }

    // Edit Contact Dialog / Sheet
    if (showEditSheet && currentDisplayDetail != null) {
        EditContactSheet(
            detail = currentDisplayDetail,
            onClose = { showEditSheet = false },
            onSave = { firstName, lastName, phoneNumber ->
                showEditSheet = false
                onUpdateContact?.invoke(currentDisplayDetail.contact.id, firstName, lastName, phoneNumber)
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && currentDisplayDetail != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Kişiyi Sil",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )
            },
            text = {
                Text(
                    text = "\"${currentDisplayDetail.contact.name}\" rehberinizden tamamen silinecek. Emin misiniz?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteContact?.invoke(currentDisplayDetail.contact.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text("SİL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("İPTAL", color = zuneColors.textMuted)
                }
            },
            containerColor = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
private fun ContactDetailContent(
    displayDetail: ContactDetailModel,
    onBack: () -> Unit,
    onOpenMessaging: ((contactName: String, phoneNumber: String) -> Unit)?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
                        if (onOpenMessaging != null) {
                            onOpenMessaging(displayDetail.contact.name, number)
                        } else {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                            context.startActivity(intent)
                        }
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
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Contact Management Actions (Edit & Delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionItem(
                icon = Icons.Default.Edit,
                label = "düzenle",
                subtitle = "kişi bilgilerini güncelle",
                accentColor = zuneColors.accentColor,
                onClick = onEditClick,
                modifier = Modifier.weight(1f)
            )

            ActionItem(
                icon = Icons.Default.Delete,
                label = "sil",
                subtitle = "rehberden kaldır",
                accentColor = Color.Red,
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EditContactSheet(
    detail: ContactDetailModel,
    onClose: () -> Unit,
    onSave: (firstName: String, lastName: String, phoneNumber: String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val nameParts = remember(detail.contact.name) {
        val parts = detail.contact.name.split(" ")
        val first = parts.firstOrNull() ?: ""
        val last = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
        first to last
    }

    var firstName by remember { mutableStateOf(nameParts.first) }
    var lastName by remember { mutableStateOf(nameParts.second) }
    var phoneNumber by remember { mutableStateOf(detail.phoneNumbers.firstOrNull() ?: "") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "kişiyi düzenle",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 36.sp),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )

                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = if (zuneColors.isDark) Color.White else Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // First Name Field
            Text("ad", style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = zuneColors.accentColor,
                    unfocusedBorderColor = zuneColors.textDim,
                    focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                    unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Last Name Field
            Text("soyad", style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = zuneColors.accentColor,
                    unfocusedBorderColor = zuneColors.textDim,
                    focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                    unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone Number Field
            Text("telefon numarası", style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = zuneColors.accentColor,
                    unfocusedBorderColor = zuneColors.textDim,
                    focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                    unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save Button
            Button(
                onClick = { onSave(firstName, lastName, phoneNumber) },
                colors = ButtonDefaults.buttonColors(containerColor = zuneColors.accentColor, contentColor = Color.White),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("KAYDET", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
        Spacer(modifier = Modifier.width(12.dp))
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
