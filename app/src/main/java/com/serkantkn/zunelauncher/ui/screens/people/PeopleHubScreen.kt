package com.serkantkn.zunelauncher.ui.screens.people

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.ui.components.ZuneAlphabetIndex
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

@Composable
fun PeopleHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val groupedContacts by viewModel.groupedContacts.collectAsState()
    val favoriteContacts by viewModel.favoriteContacts.collectAsState()
    val recentContacts by viewModel.recentContacts.collectAsState()
    val selectedContactDetail by viewModel.selectedContactDetail.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val tabs = listOf("tümü", "favoriler", "son kullanılanlar")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onPermissionResult(granted)
        }
    )

    BackHandler(enabled = selectedContactDetail != null) {
        viewModel.selectContact(null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header — Zune large typography
            Text(
                text = "kişiler",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                ),
                color = zuneColors.accentColor,
                modifier = Modifier.padding(
                    top = 60.dp,
                    bottom = 4.dp,
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal
                )
            )

            // Tabs — W10M style -> Zune Pivot
            ZunePivotTabs(
                tabs = tabs,
                pagerState = pagerState,
                onSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
            )

            // Content area
            if (!hasPermission) {
                PermissionRequestView(
                    onRequestPermission = {
                        permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                )
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = zuneColors.accentColor)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = 48.dp
                    ),
                    pageSpacing = 24.dp
                ) { page ->
                    ZunePageTransition {
                        when (tabs[page]) {
                        "tümü" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChange = { viewModel.updateSearchQuery(it) },
                                    modifier = Modifier.padding(bottom = ZuneDimens.SpacingLg)
                                )
                                
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
                                                        viewModel.selectContact(it)
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
                                            viewModel.selectContact(it)
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
                                            viewModel.selectContact(it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Detail Screen Overlay
        ContactDetailScreen(
            detail = selectedContactDetail,
            onBack = { viewModel.selectContact(null) }
        )
    }
}

// ── Search Bar ──────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val borderColor = if (zuneColors.isDark) Color(0xFF444444) else Color(0xFFCCCCCC)
    val bgColor = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val textColor = MaterialTheme.colorScheme.onBackground
    val hintColor = zuneColors.textDim

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
                    modifier = Modifier.fillMaxWidth()
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
        // Circular profile photo — W10M style
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

        // Contact name — clean, not too large
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
