package com.serkantkn.zunelauncher.ui.screens.people

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneAlphabetIndex
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

@Composable
fun PeopleHubScreen(
    onBack: () -> Unit,
    onOpenMessaging: ((contactName: String, phoneNumber: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: PeopleHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val groupedContacts by viewModel.groupedContacts.collectAsState()
    val favoriteContacts by viewModel.favoriteContacts.collectAsState()
    val recentContacts by viewModel.recentContacts.collectAsState()
    val selectedContactDetail by viewModel.selectedContactDetail.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isSearchVisible by remember { mutableStateOf(false) }

    val hasInitialDetail = remember { selectedContactDetail != null && !isWideScreen }
    val hubHingeAnim = remember { Animatable(if (hasInitialDetail) 0f else 1f) }
    val newContactHingeAnim = remember { Animatable(0f) }
    val detailHingeAnim = remember { Animatable(if (hasInitialDetail) 1f else 0f) }
    var isTransitioning by remember { mutableStateOf(false) }

    val tabs = listOf("tümü", "favoriler", "son kullanılanlar")
    val actualPageCount = tabs.size
    val loopCount = 1000
    val initialPage = (loopCount / 2) * actualPageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopCount * actualPageCount }
    )
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val openNewContactScreen: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                hubHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                newContactHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val closeNewContactScreen: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                newContactHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                hubHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val selectContactWithAnimation: (ContactModel) -> Unit = { contact ->
        if (isWideScreen) {
            viewModel.selectContact(contact)
        } else {
            if (!isTransitioning) {
                isTransitioning = true
                viewModel.selectContact(contact)
                coroutineScope.launch {
                    hubHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    detailHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    isTransitioning = false
                }
            }
        }
    }

    val closeDetailWithAnimation: () -> Unit = {
        if (isWideScreen) {
            viewModel.selectContact(null)
        } else {
            if (!isTransitioning) {
                isTransitioning = true
                coroutineScope.launch {
                    detailHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    viewModel.selectContact(null)
                    hubHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    isTransitioning = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.READ_CONTACTS] == true
            viewModel.onPermissionResult(granted)
        }
    )

    BackHandler(enabled = newContactHingeAnim.value > 0f || detailHingeAnim.value > 0f || selectedContactDetail != null) {
        if (newContactHingeAnim.value > 0f) {
            closeNewContactScreen()
        } else if (detailHingeAnim.value > 0f || selectedContactDetail != null) {
            closeDetailWithAnimation()
        }
    }

    val bottomBarActions = remember {
        listOf(
            WpBarAction(
                icon = Icons.Default.Add,
                label = "yeni kişi",
                onClick = { openNewContactScreen() }
            ),
            WpBarAction(
                icon = Icons.Default.Search,
                label = "ara",
                onClick = { isSearchVisible = !isSearchVisible }
            )
        )
    }

    val bottomBarMenuItems = remember {
        listOf(
            WpBarMenuItem(
                text = "yenile",
                onClick = { viewModel.loadContacts() }
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidthDp.toPx() }
        val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
        val overflowYPx = with(density) { (-24).dp.toPx() }

        val renderPage: @Composable (String) -> Unit = { tabName ->
            ZunePageTransition {
                when (tabName) {
                    "tümü" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(visible = isSearchVisible) {
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChange = { viewModel.updateSearchQuery(it) },
                                    isVisible = isSearchVisible,
                                    modifier = Modifier.padding(bottom = ZuneDimens.SpacingLg)
                                )
                            }

                            if (groupedContacts.isEmpty()) {
                                EmptyStateView(
                                    if (searchQuery.isNotBlank()) "sonuç bulunamadı"
                                    else stringResource(R.string.no_contacts)
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f)) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(end = 28.dp),
                                        contentPadding = PaddingValues(bottom = 80.dp)
                                    ) {
                                        groupedContacts.forEach { (letter, contactsInGroup) ->
                                            item(key = "header_$letter") {
                                                LetterHeader(letter = letter)
                                            }
                                            items(contactsInGroup, key = { it.id }) { contact ->
                                                ContactListItem(contact = contact) {
                                                    selectContactWithAnimation(it)
                                                }
                                            }
                                        }
                                    }

                                    ZuneAlphabetIndex(
                                        availableLetters = groupedContacts.keys,
                                        onLetterSelected = { letter ->
                                            val index = groupedContacts.keys.indexOf(letter)
                                            if (index >= 0) {
                                                coroutineScope.launch {
                                                    var flatIndex = 0
                                                    for ((l, list) in groupedContacts) {
                                                        if (l == letter) break
                                                        flatIndex += 1 + list.size
                                                    }
                                                    listState.scrollToItem(flatIndex)
                                                }
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                            }
                        }
                    }
                    "favoriler" -> {
                        if (favoriteContacts.isEmpty()) {
                            EmptyStateView(stringResource(R.string.no_favorite_contacts))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(favoriteContacts, key = { it.id }) { contact ->
                                    ContactListItem(contact = contact) {
                                        selectContactWithAnimation(it)
                                    }
                                }
                            }
                        }
                    }
                    "son kullanılanlar" -> {
                        if (recentContacts.isEmpty()) {
                            EmptyStateView(stringResource(R.string.no_recent_contacts))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(recentContacts, key = { it.id }) { contact ->
                                    ContactListItem(contact = contact) {
                                        selectContactWithAnimation(it)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // People Hub Content Layer with Hinge Animation
        if (hubHingeAnim.value > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = hubHingeAnim.value
                        rotationY = -90f * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 12f * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                if (isWideScreen) {
                    Text(
                        text = "kişiler",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 96.sp,
                            letterSpacing = (-4).sp,
                            lineHeight = 96.sp
                        ),
                        color = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.padding(
                            start = 72.dp,
                            top = 4.dp,
                            bottom = 24.dp
                        ).graphicsLayer { translationY = overflowYPx }
                    )

                    if (!hasPermission) {
                        PermissionRequestView(
                            onRequestPermission = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                    )
                                )
                            }
                        )
                    } else if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = zuneColors.accentColor)
                        }
                    } else {
                        LazyRow(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 72.dp, end = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(48.dp)
                        ) {
                            items(tabs.size) { index ->
                                Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                                    Text(
                                        text = tabs[index],
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                                        color = zuneColors.accentColor,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        renderPage(tabs[index])
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mobile Header — Zune large typography
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 28.dp,
                                bottom = 4.dp,
                                start = ZuneDimens.ScreenPaddingHorizontal
                            )
                    ) {
                        val cycle = (pagerState.currentPage + pagerState.currentPageOffsetFraction) % actualPageCount
                        val actualCycle = if (cycle < 0) cycle + actualPageCount else cycle
                        val threshold = (actualPageCount - 1).toFloat()

                        val translationX1: Float
                        val translationX2: Float

                        if (actualCycle <= threshold) {
                            translationX1 = -actualCycle * parallaxMultiplierPx
                            translationX2 = screenWidthPx
                        } else {
                            val fraction = actualCycle - threshold
                            translationX1 = -threshold * parallaxMultiplierPx - fraction * screenWidthPx
                            translationX2 = screenWidthPx - fraction * screenWidthPx
                        }

                        Text(
                            text = "kişiler",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 96.sp,
                                letterSpacing = (-4).sp,
                                lineHeight = 96.sp
                            ),
                            color = if (zuneColors.isDark) Color.White else Color.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                translationX = translationX1
                                translationY = overflowYPx
                            }
                        )
                        Text(
                            text = "kişiler",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 96.sp,
                                letterSpacing = (-4).sp,
                                lineHeight = 96.sp
                            ),
                            color = if (zuneColors.isDark) Color.White else Color.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                translationX = translationX2
                                translationY = overflowYPx
                            }
                        )
                    }

                    // Tabs — Zune Pivot
                    ZunePivotTabs(
                        tabs = tabs,
                        pagerState = pagerState,
                        onSelected = { index ->
                            val current = pagerState.currentPage
                            val size = actualPageCount
                            val currentActual = ((current % size) + size) % size
                            var diff = index - currentActual
                            if (diff > size / 2) {
                                diff -= size
                            } else if (diff < -size / 2) {
                                diff += size
                            }
                            val targetPage = current + diff
                            coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                        },
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                    )

                    // Content area
                    if (!hasPermission) {
                        PermissionRequestView(
                            onRequestPermission = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                    )
                                )
                            }
                        )
                    } else if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = zuneColors.accentColor)
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = 48.dp
                            ),
                            pageSpacing = 24.dp
                        ) { page ->
                            val actualPage = page % actualPageCount
                            renderPage(tabs[actualPage])
                        }
                    }
                }

                // Windows Phone Bottom Bar
                if (hasPermission) {
                    WindowsPhoneBottomBar(
                        actions = bottomBarActions,
                        menuItems = bottomBarMenuItems
                    )
                }
            }
        }

        // Contact Detail Screen Overlay (Tablet Card or Mobile Hinge)
        if (isWideScreen) {
            ContactDetailScreen(
                detail = selectedContactDetail,
                onBack = { closeDetailWithAnimation() },
                onOpenMessaging = onOpenMessaging
            )
        } else if (detailHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = detailHingeAnim.value
                        rotationY = 90f * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 12f * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                ContactDetailScreen(
                    detail = selectedContactDetail,
                    onBack = { closeDetailWithAnimation() },
                    onOpenMessaging = onOpenMessaging
                )
            }
        }

        // 3D Door Hinge Animated New Contact Screen Overlay
        if (newContactHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = newContactHingeAnim.value
                        rotationY = 90f * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 12f * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                NewContactScreen(
                    onClose = { closeNewContactScreen() },
                    onSave = { firstName, lastName, phone, email, saveToGoogle ->
                        viewModel.createContact(firstName, lastName, phone, email, saveToGoogle) { success ->
                            if (success) {
                                closeNewContactScreen()
                            }
                        }
                    }
                )
            }
        }
    }
}

// ── New Contact Creation Screen (3D Door Hinge Animated) ─────────────────────

@Composable
private fun NewContactScreen(
    onClose: () -> Unit,
    onSave: (firstName: String, lastName: String, phone: String, email: String, saveToGoogle: Boolean) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var saveToGoogle by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val bottomBarActions = remember(firstName, phone, isSaving) {
        listOf(
            WpBarAction(
                icon = Icons.Default.Check,
                label = "kaydet",
                onClick = {
                    if (firstName.isNotBlank() && !isSaving) {
                        isSaving = true
                        onSave(firstName, lastName, phone, email, saveToGoogle)
                    }
                }
            ),
            WpBarAction(
                icon = Icons.Default.Close,
                label = "iptal",
                onClick = onClose
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp)
            ) {
                Text(
                    text = "yeni kişi",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 72.sp,
                        lineHeight = 72.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        Column {
                            Text(
                                text = "kaydedilecek hesap",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                                color = zuneColors.textMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ChoiceChip(
                                    label = "Google Hesabı",
                                    selected = saveToGoogle,
                                    onSelect = { saveToGoogle = true },
                                    modifier = Modifier.weight(1f)
                                )
                                ChoiceChip(
                                    label = "Telefon Hafızası",
                                    selected = !saveToGoogle,
                                    onSelect = { saveToGoogle = false },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        InputFieldGroup(
                            label = "ad",
                            value = firstName,
                            onValueChange = { firstName = it },
                            placeholder = "Adını girin"
                        )
                    }

                    item {
                        InputFieldGroup(
                            label = "soyad",
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = "Soyadını girin"
                        )
                    }

                    item {
                        InputFieldGroup(
                            label = "telefon numarası",
                            value = phone,
                            onValueChange = { phone = it },
                            placeholder = "05xx xxx xx xx"
                        )
                    }

                    item {
                        InputFieldGroup(
                            label = "e-posta adresi",
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "ornek@gmail.com"
                        )
                    }
                }
            }

            WindowsPhoneBottomBar(actions = bottomBarActions)
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onSelect),
        color = if (selected) zuneColors.accentColor else (if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFE5E5E5)),
        shape = RoundedCornerShape(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (selected || zuneColors.isDark) Color.White else Color.Black
            )
        }
    }
}

@Composable
private fun InputFieldGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val zuneColors = LocalZuneColors.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = zuneColors.textMuted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = zuneColors.accentColor,
                unfocusedBorderColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
            )
        )
    }
}

// ── Search Bar ──────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val bgColor = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val textColor = MaterialTheme.colorScheme.onBackground
    val hintColor = zuneColors.textDim
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(bgColor, RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_contacts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = hintColor
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    singleLine = true,
                    cursorBrush = SolidColor(zuneColors.accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}

// ── Letter Header ───────────────────────────────────────────────────────────

@Composable
private fun LetterHeader(letter: Char) {
    val zuneColors = LocalZuneColors.current

    Text(
        text = letter.lowercase(),
        style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 42.sp
        ),
        color = zuneColors.accentColor,
        modifier = Modifier.padding(
            top = ZuneDimens.SpacingLg,
            bottom = ZuneDimens.SpacingSm
        )
    )
}

// ── Contact List Item (W10M Style) ──────────────────────────────────────────

@Composable
private fun ContactListItem(
    contact: ContactModel,
    onClick: (ContactModel) -> Unit
) {
    val fallbackColor = remember(contact.id) {
        val colors = listOf(ZuneColors.Pink, ZuneColors.Orange, ZuneColors.Blue, ZuneColors.Green)
        colors[Math.abs(contact.id.hashCode()) % colors.size]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(contact) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (contact.photoUri != null) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(fallbackColor, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(fallbackColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Permission Request ──────────────────────────────────────────────────────

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.padding(top = ZuneDimens.SpacingHuge)) {
        Text(
            text = stringResource(R.string.contacts_permission_title).lowercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.contacts_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            color = zuneColors.textMuted
        )
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = zuneColors.accentColor,
                contentColor = Color.White
            )
        ) {
            Text(text = stringResource(R.string.grant_permission).lowercase())
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateView(message: String) {
    Text(
        text = message.lowercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textDim,
        modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
    )
}
