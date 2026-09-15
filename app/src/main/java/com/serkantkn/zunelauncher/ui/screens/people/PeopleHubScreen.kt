package com.serkantkn.zunelauncher.ui.screens.people

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactGroup
import androidx.compose.material.icons.filled.Groups
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneAlphabetIndex
import com.serkantkn.zunelauncher.ui.components.ZuneEmptyState
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.components.rememberZuneHubEntranceState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.ui.components.ZunePivotHeader
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import java.util.Locale

/** People hub entrance: an even slower, heavier landing than the standard hub curve. */
private val PeopleHubEntranceEasing = CubicBezierEasing(0.0f, 0.98f, 0.05f, 1.0f)
private const val PeopleHubEntranceDurationMillis = 950
private const val PeopleHubBottomBarDurationMillis = 380

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
    val errorMessage by viewModel.errorMessage.collectAsState()
    val groupedContacts by viewModel.groupedContacts.collectAsState()
    val favoriteContacts by viewModel.favoriteContacts.collectAsState()
    val recentContacts by viewModel.recentContacts.collectAsState()
    val selectedContactDetail by viewModel.selectedContactDetail.collectAsState()
    val pinnedContactIds by viewModel.pinnedContactIds.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val openGroup by viewModel.openGroup.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()

    /** Open while a duplicate is being picked to join with the contact on screen. */
    var linkPickerOpen by remember { mutableStateOf(false) }
    var newGroupOpen by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isSearchVisible by remember { mutableStateOf(false) }

    val hasInitialDetail = remember { selectedContactDetail != null && !isWideScreen }
    val hubHingeAnim = remember { Animatable(if (hasInitialDetail) 0f else 1f) }
    val newContactHingeAnim = remember { Animatable(0f) }
    val detailHingeAnim = remember { Animatable(if (hasInitialDetail) 1f else 0f) }
    val editContactHingeAnim = remember { Animatable(0f) }
    var isTransitioning by remember { mutableStateOf(false) }

    // Hoisted so the entrance keeps playing even while the hub is hinged out behind a detail screen.
    val entrance = rememberZuneHubEntranceState(
        entranceDurationMillis = PeopleHubEntranceDurationMillis,
        entranceEasing = PeopleHubEntranceEasing,
        bottomBarDurationMillis = PeopleHubBottomBarDurationMillis
    )

    val tabs = listOf(
        stringResource(R.string.common_all),
        stringResource(R.string.people_tab_favorites),
        stringResource(R.string.recent_apps),
        stringResource(R.string.people_groups)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
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

    val openEditContactScreen: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                if (!isWideScreen) {
                    detailHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                }
                editContactHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val closeEditContactScreen: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                editContactHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                if (!isWideScreen) {
                    detailHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                }
                isTransitioning = false
            }
        }
    }

    val selectContactWithAnimation: (ContactModel) -> Unit = { contact ->
        coroutineScope.launch { editContactHingeAnim.snapTo(0f) }
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

    // Somebody pinned to the start board opens straight onto their card.
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        com.serkantkn.zunelauncher.data.repository.PeopleBridge.consume()?.let { contactId ->
            viewModel.contactById(contactId)?.let { selectContactWithAnimation(it) }
        }
    }


    val closeDetailWithAnimation: () -> Unit = {
        coroutineScope.launch { editContactHingeAnim.snapTo(0f) }
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

    BackHandler(
        enabled = editContactHingeAnim.value > 0f || newContactHingeAnim.value > 0f ||
            detailHingeAnim.value > 0f || selectedContactDetail != null || openGroup != null
    ) {
        if (openGroup != null && selectedContactDetail == null && detailHingeAnim.value == 0f) {
            viewModel.openGroup(null)
        } else if (editContactHingeAnim.value > 0f) {
            closeEditContactScreen()
        } else if (newContactHingeAnim.value > 0f) {
            closeNewContactScreen()
        } else if (detailHingeAnim.value > 0f || selectedContactDetail != null) {
            closeDetailWithAnimation()
        }
    }

    val onGroupsPage = pager.pagerState.currentPage % tabs.size == 3

    val bottomBarActions = listOf(
        WpBarAction(
            icon = Icons.Default.Add,
            // On the groups page the plus makes a group; everywhere else it makes a person.
            label = stringResource(
                if (onGroupsPage) R.string.people_new_group else R.string.people_new_contact
            ),
            onClick = { if (onGroupsPage) newGroupOpen = true else openNewContactScreen() }
        ),
        WpBarAction(
            icon = Icons.Default.Search,
            label = stringResource(R.string.common_search),
            onClick = { isSearchVisible = !isSearchVisible }
        )
    )

    val bottomBarMenuItems = listOf(
        WpBarMenuItem(
            text = stringResource(R.string.common_refresh),
            onClick = { viewModel.loadContacts() }
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidthDp.toPx() }
        val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
        val overflowYPx = with(density) { (-24).dp.toPx() }

        val renderPage: @Composable (Int) -> Unit = { tabIndex ->
            ZunePageTransition {
                when (tabIndex) {
                    0 -> {
                        val listState = rememberLazyListState()
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
                                ZuneEmptyState(
                                    errorMessage?.let { stringResource(R.string.people_load_failed, it) }
                                        ?: if (searchQuery.isNotBlank()) stringResource(R.string.common_no_results)
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
                    1 -> {
                        if (favoriteContacts.isEmpty()) {
                            ZuneEmptyState(errorMessage?.let { stringResource(R.string.people_load_failed, it) } ?: stringResource(R.string.no_favorite_contacts))
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    end = if (isWideScreen) 0.dp else 28.dp,
                                    bottom = 80.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(favoriteContacts, key = { _, contact -> contact.id }) { index, contact ->
                                    WpFavoriteContactTile(
                                        contact = contact,
                                        index = index,
                                        onClick = { selectContactWithAnimation(contact) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        if (recentContacts.isEmpty()) {
                            ZuneEmptyState(errorMessage?.let { stringResource(R.string.people_load_failed, it) } ?: stringResource(R.string.no_recent_contacts))
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
                    3 -> {
                        GroupsPage(
                            groups = groups,
                            openGroup = openGroup,
                            members = groupMembers,
                            onOpenGroup = { viewModel.openGroup(it) },
                            onContactClick = { selectContactWithAnimation(it) },
                            onDeleteGroup = { viewModel.deleteGroup(it) }
                        )
                    }
                }
            }
        }

        // People Hub Content & Background Layer: signature left-edge entrance, folded back out by the
        // hub hinge whenever a detail / edit screen is open on top.
        if (hubHingeAnim.value > 0f) {
            ZuneHubEntranceLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                state = entrance,
                progressScale = { hubHingeAnim.value },
                offscreenCompositing = false
            ) { bottomBarModifier ->
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen) {
                        ZuneWideHubTitle(text = stringResource(R.string.people_hub))

                        if (!hasPermission) {
                            PeoplePermissionRequest(
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
                            ZuneWidePanorama(
                                tabs = tabs,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                fillPageHeight = true
                            ) { index ->
                                renderPage(index)
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
                            val pageCount = pager.pageCount
                            val cycle = (pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction) % pageCount
                            val actualCycle = if (cycle < 0) cycle + pageCount else cycle
                            val threshold = (pageCount - 1).toFloat()

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
                                text = stringResource(R.string.people_hub),
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
                                text = stringResource(R.string.people_hub),
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
                            state = pager,
                            fontSize = 36.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                        )

                        // Content area
                        if (!hasPermission) {
                            PeoplePermissionRequest(
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
                            ZuneLoopingPager(
                                state = pager,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    start = ZuneDimens.ScreenPaddingHorizontal,
                                    end = 48.dp
                                ),
                                pageSpacing = 24.dp
                            ) { page ->
                                renderPage(page)
                            }
                        }
                    }

                    // Windows Phone Bottom Bar (Slides up from bottom when entrance completes)
                    if (hasPermission) {
                        WindowsPhoneBottomBar(
                            actions = bottomBarActions,
                            menuItems = bottomBarMenuItems,
                            modifier = bottomBarModifier
                        )
                    }
                }
            }
        }

        // Contact Detail Screen Overlay (Tablet Card or Mobile Hinge)
        if (isWideScreen) {
            ContactDetailScreen(
                detail = selectedContactDetail,
                onBack = { closeDetailWithAnimation() },
                onOpenMessaging = onOpenMessaging,
                onEditContact = { openEditContactScreen() },
                onDeleteContact = { contactId ->
                    viewModel.deleteContact(contactId) { success ->
                        if (success) closeDetailWithAnimation()
                    }
                },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onPinToStart = { viewModel.togglePinToStart(it) },
                isPinnedToStart = selectedContactDetail?.contact?.id in pinnedContactIds,
                allGroups = groups,
                onToggleGroup = { viewModel.toggleGroupMembership(it) },
                onLinkDuplicate = { linkPickerOpen = true },
                onUnlink = { viewModel.unlinkOpenContact() },
                onPhotoPicked = { viewModel.setPhoto(it) },
                onPhotoRemoved = { viewModel.removePhoto() }
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
                    onOpenMessaging = onOpenMessaging,
                    onEditContact = { openEditContactScreen() },
                    onDeleteContact = { contactId ->
                        viewModel.deleteContact(contactId) { success ->
                            if (success) closeDetailWithAnimation()
                        }
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onPinToStart = { viewModel.togglePinToStart(it) },
                    isPinnedToStart = selectedContactDetail?.contact?.id in pinnedContactIds,
                    allGroups = groups,
                    onToggleGroup = { viewModel.toggleGroupMembership(it) },
                    onLinkDuplicate = { linkPickerOpen = true },
                    onUnlink = { viewModel.unlinkOpenContact() },
                    onPhotoPicked = { viewModel.setPhoto(it) },
                    onPhotoRemoved = { viewModel.removePhoto() }
                )
            }
        }

        // 3D Door Hinge Animated Edit Contact Screen Overlay
        if (editContactHingeAnim.value > 0f && selectedContactDetail != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = editContactHingeAnim.value
                        rotationY = 90f * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 12f * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                EditContactScreen(
                    detail = selectedContactDetail!!,
                    onClose = { closeEditContactScreen() },
                    onSave = { edit ->
                        closeEditContactScreen()
                        viewModel.updateContact(selectedContactDetail!!.contact.id, edit) {}
                    }
                )
            }
        }

        // ── Picking the duplicate to join with ──
        if (linkPickerOpen && selectedContactDetail != null) {
            val open = selectedContactDetail!!.contact
            ContactPickerSheet(
                title = stringResource(R.string.people_link_pick),
                contacts = groupedContacts.values.flatten().filter { it.id != open.id },
                onPick = { other ->
                    linkPickerOpen = false
                    viewModel.linkWith(other)
                },
                onDismiss = { linkPickerOpen = false }
            )
        }

        // ── Naming a new group ──
        if (newGroupOpen) {
            NewGroupSheet(
                onCreate = {
                    viewModel.createGroup(it)
                    newGroupOpen = false
                },
                onDismiss = { newGroupOpen = false }
            )
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

    val bottomBarActions = listOf(
        WpBarAction(
            icon = Icons.Default.Check,
            label = stringResource(R.string.common_save),
            onClick = {
                if (firstName.isNotBlank() && !isSaving) {
                    isSaving = true
                    onSave(firstName, lastName, phone, email, saveToGoogle)
                }
            }
        ),
        WpBarAction(
            icon = Icons.Default.Close,
            label = stringResource(R.string.common_cancel),
            onClick = onClose
        )
    )

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
                ZunePivotHeader(
                    text = stringResource(R.string.people_new_contact),
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
                                text = stringResource(R.string.people_save_account),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                                color = zuneColors.textMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ChoiceChip(
                                    label = stringResource(R.string.people_account_google),
                                    selected = saveToGoogle,
                                    onSelect = { saveToGoogle = true },
                                    modifier = Modifier.weight(1f)
                                )
                                ChoiceChip(
                                    label = stringResource(R.string.people_account_device),
                                    selected = !saveToGoogle,
                                    onSelect = { saveToGoogle = false },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        InputFieldGroup(
                            label = stringResource(R.string.people_first_name),
                            value = firstName,
                            onValueChange = { firstName = it },
                            placeholder = stringResource(R.string.people_first_name_hint)
                        )
                    }

                    item {
                        InputFieldGroup(
                            label = stringResource(R.string.people_last_name),
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = stringResource(R.string.people_last_name_hint)
                        )
                    }

                    item {
                        InputFieldGroup(
                            label = stringResource(R.string.people_phone_number),
                            value = phone,
                            onValueChange = { phone = it },
                            placeholder = "05xx xxx xx xx"
                        )
                    }

                    item {
                        InputFieldGroup(
                            label = stringResource(R.string.people_email),
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
                    text = contact.name.take(1).uppercase(Locale.getDefault()),
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

/** Contacts permission prompt shared by the phone and wide-screen branches. */
@Composable
private fun PeoplePermissionRequest(onRequestPermission: () -> Unit) {
    ZunePermissionRequest(
        title = stringResource(R.string.contacts_permission_title).lowercase(),
        message = stringResource(R.string.contacts_permission_message),
        buttonLabel = stringResource(R.string.grant_permission).lowercase(),
        onRequest = onRequestPermission
    )
}

// ── Windows Phone Favorite Contact Live Tile ────────────────────────────────

/**
 * Authentic Windows Phone Live Contact Tile.
 *
 * Motion Cycle:
 * 1. Photo fills 100% of tile initially.
 * 2. Accent banner containing the contact name slides up from the bottom,
 *    lifting the contact photo upward simultaneously.
 * 3. Holds with name banner visible.
 * 4. Accent banner collapses back down, and photo slides back to fill 100% of tile.
 * 5. Holds with full photo.
 */
@Composable
private fun WpFavoriteContactTile(
    contact: ContactModel,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(contact.id) {
        val initialDelay = (index * 500L) % 2500L
        kotlinx.coroutines.delay(initialDelay)

        while (true) {
            // Full photo resting state
            kotlinx.coroutines.delay(3000L)

            // Lift photo & slide up name banner
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )

            // Name visible state
            kotlinx.coroutines.delay(2200L)

            // Collapse banner & slide photo back to full
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(0.dp))
            .background(zuneColors.accentColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tileHeightPx = constraints.maxHeight.toFloat()
            val bannerHeightPx = tileHeightPx * 0.30f
            val bannerHeightDp = with(LocalDensity.current) { bannerHeightPx.toDp() }

            val p = animProgress.value
            val photoOffsetYPx = -bannerHeightPx * p

            // 1. Contact Photo / Background Layer (slides up by photoOffsetYPx)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = photoOffsetYPx
                    }
            ) {
                if (contact.photoUri != null) {
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(zuneColors.accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(1).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 42.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // 2. Name Banner Layer (slides up from bottom edge)
            val currentBannerHeightDp = bannerHeightDp * p

            if (p > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(currentBannerHeightDp)
                        .background(
                            if (contact.photoUri != null) zuneColors.accentColor
                            else Color.Black.copy(alpha = 0.8f)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// GROUPS
// ════════════════════════════════════════════════════════════

/**
 * The groups page: the groups themselves, and whoever is inside the one that has been opened.
 *
 * Groups are the address book's own, so a group made here shows up in every other contacts app,
 * and one made elsewhere shows up here.
 */
@Composable
private fun GroupsPage(
    groups: List<ContactGroup>,
    openGroup: ContactGroup?,
    members: List<ContactModel>,
    onOpenGroup: (ContactGroup?) -> Unit,
    onContactClick: (ContactModel) -> Unit,
    onDeleteGroup: (Long) -> Unit
) {
    val zuneColors = LocalZuneColors.current

    if (openGroup != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = openGroup.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.common_back),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .clickable { onOpenGroup(null) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Text(
                    text = stringResource(R.string.common_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { onDeleteGroup(openGroup.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            if (members.isEmpty()) {
                ZuneEmptyState(stringResource(R.string.people_group_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(members, key = { it.id }) { contact ->
                        ContactListItem(contact = contact) { onContactClick(it) }
                    }
                }
            }
        }
        return
    }

    if (groups.isEmpty()) {
        ZuneEmptyState(stringResource(R.string.people_no_groups))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGroup(group) }
                    .padding(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = zuneColors.accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.people_group_members,
                            group.memberCount,
                            group.memberCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted
                    )
                }
            }
        }
    }
}

/** Picking one person out of the phone book — used to say which entry is the same person. */
@Composable
private fun ContactPickerSheet(
    title: String,
    contacts: List<ContactModel>,
    onPick: (ContactModel) -> Unit,
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
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = ZuneDimens.ScreenPaddingHorizontal,
                    vertical = 8.dp
                )
            )
            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_contacts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactListItem(contact = contact) { onPick(it) }
                    }
                }
            }
        }
    }
}

/** Naming a new group. */
@Composable
private fun NewGroupSheet(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    var title by remember { mutableStateOf("") }

    com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.people_new_group),
        confirmButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_save),
                onClick = { if (title.isNotBlank()) onCreate(title) },
                borderColor = zuneColors.accentColor
            )
        },
        dismissButton = {
            com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.people_group_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
