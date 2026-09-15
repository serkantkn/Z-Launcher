package com.serkantkn.zunelauncher.ui.screens.people

import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CallLogModel
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.serkantkn.zunelauncher.data.model.ContactEmail
import com.serkantkn.zunelauncher.data.model.ContactNumber
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.ContactAddress
import com.serkantkn.zunelauncher.data.model.ContactEdit
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import com.serkantkn.zunelauncher.data.model.ContactGroup
import androidx.compose.foundation.layout.navigationBarsPadding
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serkantkn.zunelauncher.data.model.ContactDetailModel
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactDetailScreen(
    detail: ContactDetailModel?,
    onBack: () -> Unit,
    onOpenMessaging: ((contactName: String, phoneNumber: String) -> Unit)? = null,
    onEditContact: (() -> Unit)? = null,
    onDeleteContact: ((contactId: String) -> Unit)? = null,
    onToggleFavorite: ((ContactModel) -> Unit)? = null,
    onPinToStart: ((ContactModel) -> Unit)? = null,
    isPinnedToStart: Boolean = false,
    allGroups: List<ContactGroup> = emptyList(),
    onToggleGroup: ((ContactGroup) -> Unit)? = null,
    onLinkDuplicate: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onPhotoPicked: ((android.graphics.Bitmap) -> Unit)? = null,
    onPhotoRemoved: (() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    var rememberedDetail by remember { mutableStateOf(detail) }
    if (detail != null) {
        rememberedDetail = detail
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val currentDisplayDetail = detail ?: rememberedDetail

    val context = LocalContext.current
    var photoSheetOpen by remember { mutableStateOf(false) }
    /** The picture that has been picked or taken and is waiting to be framed. */
    var photoToCrop by remember { mutableStateOf<Uri?>(null) }
    /** Where the camera is told to put the picture it takes. */
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }

    val picturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { picked -> if (picked != null) photoToCrop = picked }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { taken -> if (taken) photoToCrop = cameraTarget }

    fun openCamera() {
        val target = newCameraFile(context) ?: return
        cameraTarget = target
        cameraLauncher.launch(target)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(context, R.string.people_photo_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    if (currentDisplayDetail != null) {
        if (isWideScreen) {
            // Tablet Mode: Side Card sliding in from right edge
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
                            onOpenMessaging = onOpenMessaging,
                            onEditClick = { onEditContact?.invoke() },
                            onDeleteClick = { showDeleteConfirmDialog = true },
                            onCloseTablet = onBack,
                            isTabletCard = true,
                            onToggleFavorite = onToggleFavorite?.let { { it(currentDisplayDetail.contact) } },
                            onPinToStart = onPinToStart?.let { { it(currentDisplayDetail.contact) } },
                            isPinnedToStart = isPinnedToStart,
                            allGroups = allGroups,
                            onToggleGroup = onToggleGroup,
                            onLinkDuplicate = onLinkDuplicate,
                            onUnlink = onUnlink,
                            onPhotoClick = if (onPhotoPicked != null) {
                                { photoSheetOpen = true }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        } else {
            // Mobile Mode: Edge-to-edge layout
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (zuneColors.isDark) Color(0xFF0C0C0C) else Color(0xFFFAFAFA)
            ) {
                ContactDetailContent(
                    displayDetail = currentDisplayDetail,
                    onOpenMessaging = onOpenMessaging,
                    onEditClick = { onEditContact?.invoke() },
                    onDeleteClick = { showDeleteConfirmDialog = true },
                    onCloseTablet = null,
                    isTabletCard = false,
                    onToggleFavorite = onToggleFavorite?.let { { it(currentDisplayDetail.contact) } },
                    onPinToStart = onPinToStart?.let { { it(currentDisplayDetail.contact) } },
                    isPinnedToStart = isPinnedToStart,
                    allGroups = allGroups,
                    onToggleGroup = onToggleGroup,
                    onLinkDuplicate = onLinkDuplicate,
                    onUnlink = onUnlink,
                    onPhotoClick = if (onPhotoPicked != null) {
                        { photoSheetOpen = true }
                    } else {
                        null
                    }
                )
            }
        }
    }

    // Windows Phone 3D Flip Alert Dialog for Contact Deletion
    if (showDeleteConfirmDialog && currentDisplayDetail != null) {
        ZuneFlipDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = stringResource(R.string.people_delete_contact),
            confirmButton = {
                ZuneDialogButton(
                    text = stringResource(R.string.common_yes),
                    borderColor = Color.Red,
                    onClick = {
                        dismissWithAnim {
                            showDeleteConfirmDialog = false
                            onDeleteContact?.invoke(currentDisplayDetail.contact.id)
                        }
                    }
                )
            },
            dismissButton = {
                ZuneDialogButton(
                    text = stringResource(R.string.common_no),
                    onClick = {
                        dismissWithAnim {
                            showDeleteConfirmDialog = false
                        }
                    }
                )
            }
        ) {
            Text(
                text = stringResource(R.string.people_delete_confirm, currentDisplayDetail.contact.name),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

    }

    if (photoSheetOpen && currentDisplayDetail != null) {
        ContactPhotoSheet(
            hasPhoto = currentDisplayDetail.contact.photoUri != null,
            onChoose = {
                photoSheetOpen = false
                picturePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTake = {
                photoSheetOpen = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    openCamera()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            onRemove = {
                photoSheetOpen = false
                onPhotoRemoved?.invoke()
            },
            onDismiss = { photoSheetOpen = false }
        )
    }

    photoToCrop?.let { picked ->
        Dialog(
            onDismissRequest = { photoToCrop = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            ContactPhotoCropScreen(
                uri = picked,
                onApply = { bitmap ->
                    photoToCrop = null
                    onPhotoPicked?.invoke(bitmap)
                },
                onCancel = { photoToCrop = null }
            )
        }
    }
}

@Composable
private fun ContactDetailContent(
    displayDetail: ContactDetailModel,
    onOpenMessaging: ((contactName: String, phoneNumber: String) -> Unit)?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCloseTablet: (() -> Unit)?,
    isTabletCard: Boolean,
    onToggleFavorite: (() -> Unit)? = null,
    onPinToStart: (() -> Unit)? = null,
    isPinnedToStart: Boolean = false,
    allGroups: List<ContactGroup> = emptyList(),
    onToggleGroup: ((ContactGroup) -> Unit)? = null,
    onLinkDuplicate: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onPhotoClick: (() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val photoHeight = (configuration.screenHeightDp * 0.40f).dp

    val primaryNumber = displayDetail.phoneNumbers.firstOrNull() ?: ""
    val isWhatsAppAvailable = remember(context) { isWhatsAppInstalled(context) }

    val fallbackColor = remember(displayDetail.contact.id) {
        val colors = listOf(ZuneColors.Pink, ZuneColors.Orange, ZuneColors.Blue, ZuneColors.Green, ZuneColors.Purple)
        colors[abs(displayDetail.contact.id.hashCode()) % colors.size]
    }

    val dividerColor = if (zuneColors.isDark) Color(0xFF222222) else Color(0xFFE5E5E5)

    // Call logs for this contact
    var contactCallLogs by remember { mutableStateOf<List<CallLogModel>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(true) }

    LaunchedEffect(displayDetail.contact.id, displayDetail.phoneNumbers) {
        isLoadingLogs = true
        val repo = context.appContainer.callLogRepository
        contactCallLogs = repo.getCallLogsForContact(displayDetail.phoneNumbers)
        isLoadingLogs = false
    }

    val bottomBarActions = buildList {
        // Starring somebody is what fills the favourites page, and until now it could only be
        // done in the phone's own contacts app.
        if (onToggleFavorite != null) {
            add(
                WpBarAction(
                    icon = if (displayDetail.contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    // One short label; the star itself, filled or hollow, says which way it goes.
                    label = stringResource(R.string.people_favorite),
                    onClick = onToggleFavorite
                )
            )
        }
        add(
            WpBarAction(
                icon = Icons.Default.Edit,
                label = stringResource(R.string.common_edit),
                onClick = onEditClick
            )
        )
        add(
            WpBarAction(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.common_delete),
                onClick = onDeleteClick
            )
        )
    }

    val bottomBarMenu = buildList {
        if (onPinToStart != null) {
            add(
                com.serkantkn.zunelauncher.ui.components.WpBarMenuItem(
                    text = stringResource(
                        if (isPinnedToStart) R.string.people_unpin_from_start
                        else R.string.people_pin_to_start
                    ),
                    onClick = onPinToStart
                )
            )
        }
        add(
            com.serkantkn.zunelauncher.ui.components.WpBarMenuItem(
                text = stringResource(R.string.people_share_contact),
                onClick = { shareContact(context, displayDetail) }
            )
        )
        if (onLinkDuplicate != null) {
            add(
                com.serkantkn.zunelauncher.ui.components.WpBarMenuItem(
                    text = stringResource(R.string.people_link_duplicate),
                    onClick = onLinkDuplicate
                )
            )
        }
        if (onUnlink != null && displayDetail.linkedRecords > 1) {
            add(
                com.serkantkn.zunelauncher.ui.components.WpBarMenuItem(
                    text = stringResource(R.string.people_unlink),
                    onClick = onUnlink
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {
            // ─── 1. TOP PHOTO (40% OF SCREEN HEIGHT, EDGE-TO-EDGE, NO MARGINS) ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(photoHeight)
                    // Tapping the face is how you change it, which is where anybody would look.
                    .then(
                        if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick)
                        else Modifier
                    )
            ) {
                if (displayDetail.contact.photoUri != null) {
                    AsyncImage(
                        model = displayDetail.contact.photoUri,
                        contentDescription = displayDetail.contact.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fallbackColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayDetail.contact.name.take(1).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 110.sp,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // Subtle bottom gradient overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )

                // Contact Name at Bottom-Left of the Photo
                Text(
                    text = displayDetail.contact.name.lowercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 34.sp,
                        letterSpacing = (-1).sp,
                        lineHeight = 36.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )

                // Optional Tablet Close Button
                if (onCloseTablet != null) {
                    IconButton(
                        onClick = onCloseTablet,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close_cap),
                            tint = Color.White
                        )
                    }
                }
            }

            // ─── 2. ACTIONS & CONTENT CONTAINER ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // PRIMARY PHONE & SMS ACTIONS
                if (displayDetail.phoneNumbers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Call Action Button
                        WpPrimaryActionButton(
                            icon = Icons.Default.Call,
                            label = stringResource(R.string.call),
                            subtitle = primaryNumber,
                            accentColor = zuneColors.accentColor,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                com.serkantkn.zunelauncher.util.PhoneCaller.call(
                                    context = context,
                                    number = primaryNumber,
                                    name = displayDetail.contact.name,
                                    photo = displayDetail.contact.photoUri?.toString()
                                )
                            }
                        )

                        // SMS Action Button
                        WpPrimaryActionButton(
                            icon = Icons.Default.Sms,
                            label = stringResource(R.string.people_message),
                            subtitle = primaryNumber,
                            accentColor = zuneColors.accentColor,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (onOpenMessaging != null) {
                                    onOpenMessaging(displayDetail.contact.name, primaryNumber)
                                } else {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$primaryNumber"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }

                    // WHATSAPP ACTIONS ROW (ONLY IF WHATSAPP IS INSTALLED)
                    if (isWhatsAppAvailable) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "whatsapp",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 20.sp
                            ),
                            color = Color(0xFF25D366)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // WhatsApp Message
                            WpWhatsAppActionButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                label = stringResource(R.string.people_message),
                                modifier = Modifier.weight(1f),
                                onClick = { launchWhatsAppChat(context, primaryNumber) }
                            )

                            // WhatsApp Voice Call
                            WpWhatsAppActionButton(
                                icon = Icons.Default.Phone,
                                label = stringResource(R.string.people_voice_call),
                                modifier = Modifier.weight(1f),
                                onClick = { launchWhatsAppCall(context, primaryNumber, isVideo = false) }
                            )

                            // WhatsApp Video Call
                            WpWhatsAppActionButton(
                                icon = Icons.Default.Videocam,
                                label = stringResource(R.string.people_video_call),
                                modifier = Modifier.weight(1f),
                                onClick = { launchWhatsAppCall(context, primaryNumber, isVideo = true) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.people_no_number),
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                // ─── EVERY NUMBER AND ADDRESS ───
                // The screen used to show only the first number and never the email at all, even
                // though the "new contact" page asks for one.
                if (displayDetail.numbers.size > 1 || displayDetail.emails.isNotEmpty() ||
                    displayDetail.hasExtraDetails || allGroups.isNotEmpty() ||
                    displayDetail.linkedRecords > 1
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(18.dp))

                    displayDetail.numbers.forEach { entry ->
                        ContactChannelRow(
                            label = phoneTypeLabel(context, entry),
                            value = entry.number,
                            icon = Icons.Default.Call,
                            onClick = {
                                com.serkantkn.zunelauncher.util.PhoneCaller.call(
                                    context = context,
                                    number = entry.number,
                                    name = displayDetail.contact.name,
                                    photo = displayDetail.contact.photoUri?.toString()
                                )
                            }
                        )
                    }

                    displayDetail.emails.forEach { entry ->
                        ContactChannelRow(
                            label = emailTypeLabel(context, entry),
                            value = entry.address,
                            icon = Icons.Default.Email,
                            onClick = { sendEmail(context, entry.address) }
                        )
                    }

                    displayDetail.addresses.forEach { entry ->
                        ContactChannelRow(
                            label = addressTypeLabel(context, entry),
                            value = entry.address,
                            icon = Icons.Default.Place,
                            onClick = { openOnAMap(context, entry.address) }
                        )
                    }

                    if (displayDetail.birthday.isNotBlank()) {
                        ContactChannelRow(
                            label = stringResource(R.string.people_birthday),
                            value = formatBirthday(displayDetail.birthday),
                            icon = Icons.Default.Cake,
                            onClick = {}
                        )
                    }

                    val employment = listOfNotNull(
                        displayDetail.jobTitle.takeIf { it.isNotBlank() },
                        displayDetail.organization.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (employment.isNotEmpty()) {
                        ContactChannelRow(
                            label = stringResource(R.string.people_organization),
                            value = employment,
                            icon = Icons.Default.Business,
                            onClick = {}
                        )
                    }

                    if (displayDetail.note.isNotBlank()) {
                        ContactChannelRow(
                            label = stringResource(R.string.people_note),
                            value = displayDetail.note,
                            icon = Icons.AutoMirrored.Filled.StickyNote2,
                            onClick = {}
                        )
                    }

                    // Which groups this person is in, and a tap to change that.
                    if (allGroups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.people_in_groups),
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textMuted
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 6.dp)
                        ) {
                            allGroups.forEach { group ->
                                val inGroup = group.id in displayDetail.groupIds
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (inGroup) Color.White else zuneColors.textMuted,
                                    modifier = Modifier
                                        .background(if (inGroup) zuneColors.accentColor else Color.Transparent)
                                        .border(1.dp, if (inGroup) zuneColors.accentColor else zuneColors.textDim)
                                        .clickable { onToggleGroup?.invoke(group) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // A contact the phone has already joined together says so, and can be split.
                    if (displayDetail.linkedRecords > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.people_linked, displayDetail.linkedRecords),
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(18.dp))

                // ─── NOTES LINKED TO THIS CONTACT ───
                com.serkantkn.zunelauncher.ui.screens.notes.ContactNotesSection(
                    contactId = displayDetail.contact.id,
                    contactName = displayDetail.contact.name
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(18.dp))

                // ─── 3. CALL HISTORY LIST ───
                Text(
                    text = stringResource(R.string.people_call_history),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 22.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (contactCallLogs.isEmpty()) {
                    Text(
                        text = if (isLoadingLogs) stringResource(R.string.people_logs_loading) else stringResource(R.string.people_no_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        contactCallLogs.forEach { call ->
                            ContactCallLogItem(
                                call = call,
                                onClick = {
                                    com.serkantkn.zunelauncher.util.PhoneCaller.call(
                                        context = context,
                                        number = call.number,
                                        name = displayDetail.contact.name,
                                        photo = displayDetail.contact.photoUri?.toString()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Windows Phone Application Bar
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            WindowsPhoneBottomBar(
                actions = bottomBarActions,
                menuItems = bottomBarMenu
            )
        }
    }
}

@Composable
private fun WpPrimaryActionButton(
    icon: ImageVector,
    label: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Surface(
        color = if (zuneColors.isDark) Color(0xFF191919) else Color(0xFFEDEDED),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label.lowercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = zuneColors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WpWhatsAppActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val waColor = Color(0xFF25D366)

    Surface(
        color = if (zuneColors.isDark) Color(0xFF141414) else Color(0xFFF2F2F2),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, waColor.copy(alpha = 0.35f)),
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = waColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactCallLogItem(
    call: CallLogModel,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val format = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val dateString = format.format(Date(call.dateMillis)).lowercase()

    val (icon, tint, typeText) = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
            stringResource(R.string.call_incoming)
        )
        CallLog.Calls.OUTGOING_TYPE -> Triple(
            Icons.AutoMirrored.Filled.CallMade,
            if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
            stringResource(R.string.call_outgoing)
        )
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Triple(
            Icons.AutoMirrored.Filled.CallMissed,
            zuneColors.accentColor,
            if (call.type == CallLog.Calls.REJECTED_TYPE) stringResource(R.string.call_rejected) else stringResource(R.string.call_missed)
        )
        else -> Triple(
            Icons.Default.Call,
            zuneColors.textMuted,
            stringResource(R.string.call_generic)
        )
    }

    val durationText = formatDuration(call.durationSeconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFE8E8E8),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = typeText,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = typeText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = zuneColors.textDim
            )
        }

        if (durationText.isNotEmpty()) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = zuneColors.textDim
            )
        }
    }
}

@Composable
private fun formatDuration(durationSeconds: Long): String {
    if (durationSeconds <= 0) return ""
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return if (minutes > 0) stringResource(R.string.duration_min_sec, minutes, seconds) else stringResource(R.string.duration_sec, seconds)
}

private fun isWhatsAppInstalled(context: Context): Boolean {
    val pm = context.packageManager
    return try {
        pm.getPackageInfo("com.whatsapp", 0)
        true
    } catch (e: Exception) {
        try {
            pm.getPackageInfo("com.whatsapp.w4b", 0)
            true
        } catch (e2: Exception) {
            false
        }
    }
}

private fun launchWhatsAppChat(context: Context, phoneNumber: String) {
    if (phoneNumber.isBlank()) return
    try {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    } catch (e: Exception) {
        ZuneLog.e("ContactDetailScreen", "launchWhatsAppChat failed", e)
    }
}

private fun launchWhatsAppCall(context: Context, phoneNumber: String, isVideo: Boolean) {
    if (phoneNumber.isBlank()) return
    try {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        val targetMime = if (isVideo) {
            "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        } else {
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        }

        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DATA1
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(targetMime)

        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        var matchedId: Long? = null
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Data._ID)
            val data1Index = it.getColumnIndex(ContactsContract.Data.DATA1)
            while (it.moveToNext()) {
                val data1 = it.getString(data1Index) ?: ""
                val cleanData = data1.replace(Regex("[^0-9]"), "")
                if (cleanData.endsWith(cleanNumber.takeLast(7)) || cleanNumber.endsWith(cleanData.takeLast(7))) {
                    matchedId = it.getLong(idIndex)
                    break
                }
            }
        }

        if (matchedId != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, matchedId),
                    targetMime
                )
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }
    } catch (e: Exception) {
        ZuneLog.e("ContactDetailScreen", "launchWhatsAppCall failed", e)
    }

    // Fallback: Open WhatsApp Chat
    launchWhatsAppChat(context, phoneNumber)
}

/**
 * Editing a person.
 *
 * It used to be one name and one number: saving a contact who had a mobile and a work line wrote
 * the same number over both of them, and the email address the "new contact" page had asked for
 * was nowhere to be seen. Every number and every address is on this page now, each removable.
 */
@Composable
fun EditContactScreen(
    detail: ContactDetailModel,
    onClose: () -> Unit,
    onSave: (ContactEdit) -> Unit,
    modifier: Modifier = Modifier
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
    val numbers = remember(detail) {
        mutableStateListOf<ContactNumber>().apply {
            addAll(detail.numbers.ifEmpty { listOf(ContactNumber(number = "")) })
        }
    }
    val emails = remember(detail) {
        mutableStateListOf<ContactEmail>().apply { addAll(detail.emails) }
    }
    val addresses = remember(detail) {
        mutableStateListOf<ContactAddress>().apply { addAll(detail.addresses) }
    }
    var birthday by remember(detail) { mutableStateOf(detail.birthday) }
    var note by remember(detail) { mutableStateOf(detail.note) }
    var organization by remember(detail) { mutableStateOf(detail.organization) }
    var jobTitle by remember(detail) { mutableStateOf(detail.jobTitle) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.people_edit_contact),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 36.sp),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close_cap),
                        tint = if (zuneColors.isDark) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            EditField(stringResource(R.string.people_first_name), firstName) { firstName = it }
            Spacer(modifier = Modifier.height(16.dp))
            EditField(stringResource(R.string.people_last_name), lastName) { lastName = it }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.people_phone_number),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )

            numbers.forEachIndexed { index, entry ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        EditField(
                            label = null,
                            value = entry.number,
                            keyboard = KeyboardType.Phone
                        ) { numbers[index] = entry.copy(number = it) }
                    }
                    if (numbers.size > 1) {
                        RemoveRowButton { numbers.removeAt(index) }
                    }
                }
                PhoneTypeRow(selected = entry.type) { numbers[index] = entry.copy(type = it) }
            }

            AddRowButton(stringResource(R.string.people_add_number)) {
                numbers.add(ContactNumber(number = ""))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.people_email),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )

            emails.forEachIndexed { index, entry ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        EditField(
                            label = null,
                            value = entry.address,
                            keyboard = KeyboardType.Email
                        ) { emails[index] = entry.copy(address = it) }
                    }
                    RemoveRowButton { emails.removeAt(index) }
                }
            }

            AddRowButton(stringResource(R.string.people_add_email)) {
                emails.add(ContactEmail(address = ""))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.people_address),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )

            addresses.forEachIndexed { index, entry ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        EditField(label = null, value = entry.address) {
                            addresses[index] = entry.copy(address = it)
                        }
                    }
                    RemoveRowButton { addresses.removeAt(index) }
                }
            }

            AddRowButton(stringResource(R.string.people_add_address)) {
                addresses.add(ContactAddress(address = ""))
            }

            Spacer(modifier = Modifier.height(24.dp))
            EditField(
                label = stringResource(R.string.people_birthday),
                value = birthday,
                placeholder = stringResource(R.string.people_birthday_hint)
            ) { birthday = it }

            Spacer(modifier = Modifier.height(16.dp))
            EditField(stringResource(R.string.people_organization), organization) { organization = it }

            Spacer(modifier = Modifier.height(16.dp))
            EditField(stringResource(R.string.people_job_title), jobTitle) { jobTitle = it }

            Spacer(modifier = Modifier.height(16.dp))
            EditField(stringResource(R.string.people_note), note, singleLine = false) { note = it }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSave(
                        ContactEdit(
                            firstName = firstName,
                            lastName = lastName,
                            numbers = numbers.filter { it.number.isNotBlank() },
                            emails = emails.filter { it.address.isNotBlank() },
                            addresses = addresses.filter { it.address.isNotBlank() },
                            birthday = birthday.trim(),
                            note = note.trim(),
                            organization = organization.trim(),
                            jobTitle = jobTitle.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = zuneColors.accentColor, contentColor = Color.White),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_save).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditField(
    label: String?,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Column {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            placeholder = placeholder?.let { { Text(it, color = zuneColors.textDim) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = zuneColors.accentColor,
                unfocusedBorderColor = zuneColors.textDim,
                focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
            )
        )
    }
}

/** Which kind of number this is — the thing every phone book asks and this one never did. */
@Composable
private fun PhoneTypeRow(selected: Int, onSelected: (Int) -> Unit) {
    val zuneColors = LocalZuneColors.current
    val types = listOf(
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE to R.string.people_type_mobile,
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME to R.string.people_type_home,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK to R.string.people_type_work,
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER to R.string.people_type_other
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        types.forEach { (type, labelRes) ->
            val active = type == selected
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) Color.White else zuneColors.textMuted,
                modifier = Modifier
                    .background(if (active) zuneColors.accentColor else Color.Transparent)
                    .border(1.dp, if (active) zuneColors.accentColor else zuneColors.textDim)
                    .clickable { onSelected(type) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun RemoveRowButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.common_remove),
            tint = LocalZuneColors.current.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AddRowButton(label: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = zuneColors.accentColor,
        modifier = Modifier
            .padding(top = 10.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    )
}

private fun abs(n: Int): Int = if (n < 0) -n else n

/** One way of reaching somebody: the kind of line, the address itself, and what tapping it does. */
@Composable
private fun ContactChannelRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = zuneColors.accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted
            )
        }
    }
}

/** Android names these kinds itself, in the phone's own language. */
private fun phoneTypeLabel(context: Context, number: ContactNumber): String =
    ContactsContract.CommonDataKinds.Phone
        .getTypeLabel(context.resources, number.type, number.customLabel ?: "")
        .toString()

private fun addressTypeLabel(context: Context, address: ContactAddress): String =
    ContactsContract.CommonDataKinds.StructuredPostal
        .getTypeLabel(context.resources, address.type, "")
        .toString()

private fun openOnAMap(context: Context, address: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        ZuneLog.w("ContactDetailScreen", "nothing here shows a map", e)
    }
}

private fun emailTypeLabel(context: Context, email: ContactEmail): String =
    ContactsContract.CommonDataKinds.Email
        .getTypeLabel(context.resources, email.type, "")
        .toString()

private fun sendEmail(context: Context, address: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        ZuneLog.w("ContactDetailScreen", "nothing here sends mail", e)
    }
}

/**
 * Passes a person on to another app as plain text.
 *
 * A proper vCard would have to be written to a file and shared with a content provider; the name
 * and the numbers as text is what every messaging app actually accepts.
 */
private fun shareContact(context: Context, detail: ContactDetailModel) {
    val lines = buildList {
        add(detail.contact.name)
        detail.numbers.forEach { add(it.number) }
        detail.emails.forEach { add(it.address) }
    }
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, detail.contact.name)
                    putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
                },
                detail.contact.name
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        ZuneLog.w("ContactDetailScreen", "nothing here shares a contact", e)
    }
}

/**
 * Somewhere for the camera to put the picture it takes.
 *
 * The camera app writes the full-size photograph into a file of ours rather than handing back the
 * postage stamp that a thumbnail-only capture returns.
 */
private fun newCameraFile(context: Context): Uri? = try {
    val directory = java.io.File(context.cacheDir, "contact_photos").apply { mkdirs() }
    val file = java.io.File(directory, "capture.jpg")
    androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
} catch (e: Exception) {
    ZuneLog.e("ContactDetailScreen", "nowhere to put a photograph", e)
    null
}

/** Choosing where a contact photo comes from, or taking the one there away. */
@Composable
private fun ContactPhotoSheet(
    hasPhoto: Boolean,
    onChoose: () -> Unit,
    onTake: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.people_photo),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            PhotoSheetAction(stringResource(R.string.people_photo_choose), onChoose)
            PhotoSheetAction(stringResource(R.string.people_photo_take), onTake)
            if (hasPhoto) {
                PhotoSheetAction(
                    stringResource(R.string.people_photo_remove),
                    onRemove,
                    MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PhotoSheetAction(label: String, onClick: () -> Unit, color: Color? = null) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
