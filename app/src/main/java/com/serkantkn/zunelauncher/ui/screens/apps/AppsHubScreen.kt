package com.serkantkn.zunelauncher.ui.screens.apps

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.components.ZuneAlphabetIndex
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import com.serkantkn.zunelauncher.util.toImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation

@Composable
fun AppsHubScreen(
    isCurrentPage: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: AppsHubViewModel = viewModel()
) {
    val groupedApps by viewModel.groupedApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val availableLetters by viewModel.availableLetters.collectAsState()
    val favoritePackages by viewModel.favoritePackages.collectAsState()
    val gridState = rememberLazyGridState()
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()

    val animationProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var clickedItemKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            clickedItemKey = null
            animationProgress.snapTo(1f)
        } else if (animationProgress.value > 1f) {
            clickedItemKey = null
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
            )
        }
    }

    fun handleLaunch(key: String, action: () -> Unit) {
        clickedItemKey = key
        coroutineScope.launch {
            animationProgress.animateTo(
                targetValue = 2f,
                animationSpec = tween(durationMillis = 700, easing = LinearEasing)
            )
            action()
        }
    }

    // Track which app's accordion menu is currently expanded
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    // Build flat list with section headers
    val flatList = remember(groupedApps) {
        buildList {
            groupedApps.forEach { (letter, apps) ->
                add(AppsListItem.Header(letter))
                apps.forEach { app ->
                    add(AppsListItem.App(app))
                }
            }
        }
    }

    // Map letter -> index for fast scrolling
    val letterIndexMap = remember(flatList) {
        buildMap {
            flatList.forEachIndexed { index, item ->
                if (item is AppsListItem.Header) {
                    put(item.letter, index)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val overflowYPx = with(density) { (-24).dp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                    end = 40.dp,
                    top = 48.dp
                )
        ) {
            Text(
                text = "uygulamalar",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                ),
                color = if (LocalZuneColors.current.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search bar
            ZuneSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .w10mStaggeredAnimation(animationProgress.value, 1)
                    .padding(bottom = ZuneDimens.SpacingLg),
                placeholder = "uygulama ara"
            )

            // App list
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(if (isWideScreen) 8 else 1),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = flatList,
                    key = { _, item ->
                        when (item) {
                            is AppsListItem.Header -> "header_${item.letter}"
                            is AppsListItem.App -> "app_${item.appInfo.packageName}"
                        }
                    },
                    span = { _, item ->
                        if (item is AppsListItem.Header) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    }
                ) { index, item ->
                    val globalIndex = 2 + index
                    when (item) {
                        is AppsListItem.Header -> {
                            val key = "header_${item.letter}"
                            Box(modifier = Modifier.w10mStaggeredAnimation(animationProgress.value, globalIndex, clickedItemKey == key)) {
                                LetterHeader(letter = item.letter)
                            }
                        }
                        is AppsListItem.App -> {
                            val pkg = item.appInfo.packageName
                            val isFavorite = pkg in favoritePackages
                            val isExpanded = expandedPackage == pkg

                            val key = "app_${item.appInfo.packageName}"

                            Column(
                                modifier = Modifier
                                    .w10mStaggeredAnimation(animationProgress.value, globalIndex, clickedItemKey == key)
                                    .padding(end = if (isWideScreen) 8.dp else 0.dp)
                            ) {
                                AppRow(
                                    appInfo = item.appInfo,
                                    icon = viewModel.getAppIcon(pkg),
                                    onClick = {
                                        expandedPackage = null
                                        handleLaunch(key) { viewModel.launchApp(pkg) }
                                    },
                                    onLongPress = {
                                        expandedPackage = if (isExpanded) null else pkg
                                    }
                                )

                                // Accordion menu — pushes items below downward
                                AccordionMenu(
                                    visible = isExpanded,
                                    isFavorite = isFavorite,
                                    onToggleFavorite = {
                                        if (!isFavorite && !com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM && favoritePackages.size >= 10) {
                                            android.widget.Toast.makeText(context, "Ücretsiz sürümde en fazla 10 favori uygulama ekleyebilirsiniz. Sınırı kaldırmak için Z Launcher Pro'ya geçin.", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.toggleFavorite(pkg)
                                        }
                                        expandedPackage = null
                                    },
                                    onDismiss = {
                                        expandedPackage = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Alphabet index on right edge
        ZuneAlphabetIndex(
            availableLetters = availableLetters,
            onLetterSelected = { letter ->
                letterIndexMap[letter]?.let { index ->
                    coroutineScope.launch {
                        gridState.animateScrollToItem(index)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxHeight(0.60f)
                .padding(end = 4.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp)
        )
    }
}

// ── Accordion Menu ──────────────────────────────────────────────────────────

@Composable
private fun AccordionMenu(
    visible: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current

    val menuBg = if (zuneColors.isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(250),
            expandFrom = Alignment.Top
        ),
        exit = shrinkVertically(
            animationSpec = tween(200),
            shrinkTowards = Alignment.Top
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(menuBg)
                .clickable { onToggleFavorite() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) zuneColors.accentColor else zuneColors.textMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isFavorite) "favorilerden kaldır" else "favorilere ekle",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// ── Letter Header ───────────────────────────────────────────────────────────

@Composable
private fun LetterHeader(letter: Char) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .padding(top = ZuneDimens.SpacingMd, bottom = ZuneDimens.SpacingSm)
            .size(42.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(zuneColors.accentColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString().lowercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = Color.White
        )
    }
}

// ── App Row (with long press support) ───────────────────────────────────────

@Composable
private fun AppRow(
    appInfo: AppInfo,
    icon: Drawable?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "app_row_scale"
    )

    val isWideScreen = LocalIsWideScreen.current

    if (isWideScreen) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() }
                    )
                }
                .padding(vertical = 10.dp)
        ) {
            icon?.let { drawable ->
                val bitmap = remember(drawable) { drawable.toImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = appInfo.label,
                    modifier = Modifier
                        .size(ZuneDimens.AppIconSize)
                        .clip(RoundedCornerShape(10.dp))
                )
            } ?: Box(
                modifier = Modifier
                    .size(ZuneDimens.AppIconSize)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.height(8.dp))

            val zuneColors = LocalZuneColors.current
            Text(
                text = appInfo.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    shadow = if (!zuneColors.isDark) Shadow(
                        color = Color.White.copy(alpha = 0.9f),
                        offset = Offset(1f, 1f),
                        blurRadius = 4f
                    ) else null
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() }
                    )
                }
                .padding(vertical = 10.dp)
        ) {
            icon?.let { drawable ->
                val bitmap = remember(drawable) { drawable.toImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = appInfo.label,
                    modifier = Modifier
                        .size(ZuneDimens.AppIconSize)
                        .clip(RoundedCornerShape(10.dp))
                )
            } ?: Box(
                modifier = Modifier
                    .size(ZuneDimens.AppIconSize)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(ZuneDimens.SpacingMd))

            val zuneColors = LocalZuneColors.current
            Text(
                text = appInfo.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    shadow = if (!zuneColors.isDark) Shadow(
                        color = Color.White.copy(alpha = 0.9f),
                        offset = Offset(1f, 1f),
                        blurRadius = 4f
                    ) else null
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

private sealed class AppsListItem {
    data class Header(val letter: Char) : AppsListItem()
    data class App(val appInfo: AppInfo) : AppsListItem()
}
