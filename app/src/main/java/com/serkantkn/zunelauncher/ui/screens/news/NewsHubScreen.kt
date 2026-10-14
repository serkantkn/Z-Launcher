package com.serkantkn.zunelauncher.ui.screens.news

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.NewsSection
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec
import com.serkantkn.zunelauncher.ui.components.MetroChip
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHub
import com.serkantkn.zunelauncher.ui.components.ZuneHubBar
import com.serkantkn.zunelauncher.ui.components.ZuneHubPane
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZuneWideEmptyPane
import com.serkantkn.zunelauncher.ui.components.bottomBarHeight
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay

/** The phone's sub-page standing over the hub, besides a story: the hub's settings. */
private object SettingsPage

/**
 * The news hub: Google News's front page and topics as headings, then "my sources" - every feed
 * the user follows, newest first. A story opens to its picture, headline and summary, and from
 * there goes on to its site in the launcher's own browser.
 *
 * Built on the hub standard ([ZuneHub]): a pivot on a phone with the story hinging in over it,
 * headings down the left on a tablet with the story in the pane.
 */
@Composable
fun NewsHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsHubViewModel = viewModel()
) {
    val isWideScreen = LocalIsWideScreen.current
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current

    val topics by viewModel.topics.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val status by viewModel.status.collectAsState()
    val openStory by viewModel.openStory.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val readingLinks by viewModel.readingLinks.collectAsState()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val keys = remember(topics) { topics.map { it.sectionKey } + NewsSection.SOURCES_KEY }
    val tabs = keys.map { stringResource(NewsHubViewModel.titleOf(it)) }
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentKey = keys.getOrElse(pager.currentPage) { keys.first() }

    // A topic switched on or off puts a page in or out among the others, and the pager would stay
    // at the same position and so show another heading. The heading being read is kept instead,
    // or the front page when it was the one switched off.
    var keysSeen by remember { mutableStateOf(keys) }
    var keptKey by remember { mutableStateOf(keys.first()) }
    if (keysSeen == keys) {
        SideEffect { keptKey = currentKey }
    }
    LaunchedEffect(keys) {
        if (keysSeen == keys) return@LaunchedEffect
        val index = keys.indexOf(keptKey).takeIf { it >= 0 } ?: 0
        if (pager.currentPage != index) pager.scrollToPage(index)
        keysSeen = keys
    }
    LaunchedEffect(currentKey) { viewModel.ensureFresh(currentKey) }
    LaunchedEffect(Unit) { viewModel.ensureFresh(keys.first()) }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2600)
            viewModel.clearStatus()
        }
    }

    // ── The phone's sub-page: a story or the settings, hinging in over the hub ──────────────
    val hingeSpec = rememberHingeSpec()
    val hinge = remember { Animatable(0f) }
    val phoneSubject: Any? = openStory ?: if (settingsOpen) SettingsPage else null
    var shownSubject by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(phoneSubject, isWideScreen) {
        if (isWideScreen) {
            hinge.snapTo(0f)
            shownSubject = null
            return@LaunchedEffect
        }
        if (phoneSubject != null) {
            shownSubject = phoneSubject
            hinge.animateTo(1f, hingeSpec)
        } else {
            hinge.animateTo(0f, hingeSpec)
            shownSubject = null
        }
    }

    BackHandler {
        when {
            openStory != null -> viewModel.close()
            settingsOpen -> settingsOpen = false
            else -> onBack()
        }
    }

    val openSettings = {
        viewModel.close()
        settingsOpen = true
    }
    val openStoryOf: (NewsItem) -> Unit = { story ->
        settingsOpen = false
        viewModel.open(story)
    }

    val barActions = buildList {
        add(WpBarAction(Icons.Default.Refresh, stringResource(R.string.news_refresh)) { viewModel.refresh(currentKey) })
        if (currentKey == NewsSection.SOURCES_KEY) {
            add(WpBarAction(Icons.Default.Add, stringResource(R.string.news_add_source)) { openSettings() })
        }
        add(WpBarAction(Icons.Default.Tune, stringResource(R.string.news_settings)) { openSettings() })
    }
    val barMenu = listOf(
        WpBarMenuItem(stringResource(R.string.news_refresh_all)) { keys.forEach { viewModel.refresh(it) } }
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = hinge.value
                    rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                    alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            ZuneHub(
                title = stringResource(HubType.NEWS.titleRes),
                tabs = tabs,
                pager = pager,
                bar = ZuneHubBar(barActions, barMenu),
                phonePagePadding = PaddingValues(0.dp),
                widePane = ZuneHubPane(
                    key = when {
                        settingsOpen -> SettingsPage
                        openStory != null -> openStory?.link
                        else -> null
                    }
                ) { key, barModifier ->
                    val story = openStory
                    when {
                        key == SettingsPage -> NewsSettingsScreen(
                            viewModel = viewModel,
                            inPane = true,
                            onClose = { settingsOpen = false },
                            barModifier = barModifier
                        )
                        key != null && story != null -> NewsStoryScreen(
                            story = story,
                            inPane = true,
                            onClose = { viewModel.close() },
                            onRead = { viewModel.readOnSite(it) },
                            onToggleReadingList = { viewModel.toggleReadingList(story) },
                            onReadingList = story.readUrl in readingLinks,
                            barModifier = barModifier
                        )
                        else -> ZuneWideEmptyPane(
                            title = stringResource(R.string.news_pick_story),
                            action = stringResource(R.string.news_settings),
                            onAction = openSettings
                        )
                    }
                }
            ) { page ->
                val key = keys.getOrElse(page) { keys.first() }
                NewsPage(
                    key = key,
                    section = sections[key],
                    isRefreshing = key in refreshing,
                    hasSources = sources.isNotEmpty(),
                    sourceNames = sources.map { it.name },
                    sourceFilter = sourceFilter,
                    onFilter = viewModel::setSourceFilter,
                    openLink = if (isWideScreen) openStory?.link else null,
                    onOpen = openStoryOf,
                    onRefresh = { viewModel.refresh(key) },
                    onAddSource = openSettings
                )
            }
        }

        // The phone's sub-page, hinging in from the left edge as the hub swings away.
        if (!isWideScreen && (phoneSubject != null || hinge.value > 0f)) {
            val subject = shownSubject ?: phoneSubject
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = hinge.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                when (subject) {
                    SettingsPage -> NewsSettingsScreen(
                        viewModel = viewModel,
                        inPane = false,
                        onClose = { settingsOpen = false }
                    )
                    is NewsItem -> {
                        // The open story, once its page has filled in its picture.
                        val story = openStory?.takeIf { it.link == subject.link } ?: subject
                        NewsStoryScreen(
                            story = story,
                            inPane = false,
                            onClose = { viewModel.close() },
                            onRead = { viewModel.readOnSite(it) },
                            onToggleReadingList = { viewModel.toggleReadingList(story) },
                            onReadingList = story.readUrl in readingLinks
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = status != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(zuneColors.accentColor)
                    .statusBarsPadding()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp)
            ) {
                Text(
                    text = status?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White
                )
            }
        }
    }
}

/** One heading's stories: the first large, with its picture, the rest as a list. */
@Composable
private fun NewsPage(
    key: String,
    section: NewsSection?,
    isRefreshing: Boolean,
    hasSources: Boolean,
    sourceNames: List<String>,
    sourceFilter: String?,
    onFilter: (String?) -> Unit,
    openLink: String?,
    onOpen: (NewsItem) -> Unit,
    onRefresh: () -> Unit,
    onAddSource: () -> Unit
) {
    val isSources = key == NewsSection.SOURCES_KEY
    val isWideScreen = LocalIsWideScreen.current
    val horizontal = if (isWideScreen) 0.dp else ZuneDimens.ScreenPaddingHorizontal
    val stories = remember(section, sourceFilter, isSources) {
        val all = section?.items.orEmpty()
        if (isSources && sourceFilter != null) all.filter { it.source == sourceFilter } else all
    }
    ZunePageTransition {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isRefreshing) NewsLoadingDots(modifier = Modifier.padding(horizontal = horizontal))
            if (isSources && sourceNames.size > 1) {
                SourceFilterRow(
                    names = sourceNames,
                    selected = sourceFilter,
                    onSelect = onFilter,
                    edge = horizontal,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
            when {
                isSources && !hasSources -> MetroEmpty(
                    message = stringResource(R.string.news_no_sources),
                    actionLabel = stringResource(R.string.news_add_source),
                    onAction = onAddSource,
                    modifier = Modifier.padding(horizontal = horizontal)
                )
                stories.isEmpty() && isRefreshing -> Text(
                    text = stringResource(R.string.news_loading),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                    color = LocalZuneColors.current.textMuted,
                    modifier = Modifier.padding(horizontal = horizontal, vertical = ZuneDimens.SpacingLg)
                )
                stories.isEmpty() -> MetroEmpty(
                    message = stringResource(R.string.news_nothing_yet),
                    actionLabel = stringResource(R.string.news_refresh),
                    onAction = onRefresh,
                    modifier = Modifier.padding(horizontal = horizontal)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = horizontal, end = horizontal, bottom = bottomBarHeight() + 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "lead:" + stories.first().link) {
                        LeadStory(stories.first(), highlighted = stories.first().link == openLink) { onOpen(stories.first()) }
                    }
                    items(stories.drop(1), key = { it.link }) { story ->
                        StoryRow(story, highlighted = story.link == openLink, showSource = true) { onOpen(story) }
                    }
                }
            }
        }
    }
}

/** The heading's first story, the width of the page: its picture with the headline over it. */
@Composable
private fun LeadStory(story: NewsItem, highlighted: Boolean, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .wpTilt(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .aspectRatio(if (story.imageUrl != null) 16f / 10f else 16f / 7f)
            .background(zuneColors.accentColor)
    ) {
        if (story.imageUrl != null) {
            AsyncImage(
                model = story.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 22.sp,
                    lineHeight = 27.sp
                ),
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metaLine(story),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (highlighted) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(5.dp).fillMaxHeight().background(Color.White))
        }
    }
}

/** A story in the list: a square of its picture when it has one, its headline, the paper and when. */
@Composable
private fun StoryRow(story: NewsItem, highlighted: Boolean, showSource: Boolean, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) zuneColors.accentColor.copy(alpha = 0.18f) else Color.Transparent)
            .wpTilt(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        if (story.imageUrl != null) {
            AsyncImage(
                model = story.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .background(zuneColors.accentColor.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 18.sp,
                    lineHeight = 23.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showSource) metaLine(story) else relativeTime(story.publishedAt).orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = zuneColors.accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private fun metaLine(story: NewsItem): String =
    listOfNotNull(story.source.takeIf { it.isNotBlank() }, relativeTime(story.publishedAt)).joinToString(" · ")

/** "My sources" narrowed to one feed, or all of them: the feeds' names as a row to pick from. */
@Composable
private fun SourceFilterRow(
    names: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    edge: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    // The names run on past the page's margin to the screen's edge, so it shows there are more.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = edge)
    ) {
        MetroChip(label = stringResource(R.string.news_all_sources), selected = selected == null, onClick = { onSelect(null) })
        names.forEach { name ->
            MetroChip(label = name, selected = selected == name, onClick = { onSelect(if (selected == name) null else name) })
        }
    }
}

/**
 * Windows Phone's progress: dots in the accent colour running across the top of the page, slow
 * in the middle and quick at the ends, for as long as the stories are being fetched.
 */
@Composable
private fun NewsLoadingDots(modifier: Modifier = Modifier) {
    val accent = LocalZuneColors.current.accentColor
    val transition = rememberInfiniteTransition(label = "news_dots")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "news_dots_progress"
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(4.dp)) {
        val width = maxWidth
        repeat(5) { index ->
            // Each dot starts a little after the one before it; the cubic makes them bunch up in
            // the middle and race at the edges, as the phone's did.
            val t = (progress * 1.6f - index * 0.08f).coerceIn(0f, 1f)
            val eased = 0.5f + 4f * (t - 0.5f) * (t - 0.5f) * (t - 0.5f)
            if (t > 0f && t < 1f) {
                Box(
                    modifier = Modifier
                        .offset(x = width * eased)
                        .size(4.dp)
                        .background(accent)
                )
            }
        }
    }
}
