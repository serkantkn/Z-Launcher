package com.serkantkn.zunelauncher.ui.screens.news

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneWidePaneHeader
import com.serkantkn.zunelauncher.ui.components.bottomBarHeight
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.util.Locale

/**
 * A story opened from the hub: its picture, headline and the site's own summary, then the way on
 * to the whole story on its site, and - for a Google News story - the same story as other papers
 * tell it. On a phone it stands over the hub; on a tablet it is the pane beside the list.
 */
@Composable
internal fun NewsStoryScreen(
    story: NewsItem,
    inPane: Boolean,
    onClose: () -> Unit,
    onRead: (String) -> Unit,
    onToggleReadingList: () -> Unit = {},
    onReadingList: Boolean = false,
    barModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (inPane) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (inPane) {
                ZuneWidePaneHeader(section = story.source, onClose = onClose)
            } else {
                Text(
                    text = stringResource(HubType.NEWS.titleRes).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal)
                )
            }
            NewsStoryBody(
                story = story,
                onRead = onRead,
                contentPadding = PaddingValues(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = if (inPane) 32.dp else ZuneDimens.ScreenPaddingHorizontal,
                    top = 12.dp,
                    bottom = bottomBarHeight() + 24.dp
                ),
                modifier = Modifier.weight(1f)
            )
        }
        WindowsPhoneBottomBar(
            actions = listOf(
                WpBarAction(Icons.Default.OpenInBrowser, stringResource(R.string.news_read_on)) { onRead(story.readUrl) },
                WpBarAction(Icons.Default.Share, stringResource(R.string.common_share)) { share(context, story) }
            ),
            menuItems = listOf(
                WpBarMenuItem(
                    stringResource(if (onReadingList) R.string.browser_remove_reading else R.string.browser_add_reading)
                ) { onToggleReadingList() },
                WpBarMenuItem(stringResource(R.string.news_copy_link)) { copyLink(context, story.readUrl) }
            ),
            modifier = barModifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun NewsStoryBody(
    story: NewsItem,
    onRead: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        if (story.imageUrl != null) {
            AsyncImage(
                model = story.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(zuneColors.accentColor.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = story.source,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = zuneColors.accentColor
        )
        Text(
            text = story.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = 30.sp,
                lineHeight = 36.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp)
        )
        relativeTime(story.publishedAt)?.let { time ->
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (story.summary.isNotBlank()) {
            Text(
                text = story.summary,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 19.sp,
                    lineHeight = 28.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 18.dp)
            )
        }

        MetroButton(
            label = stringResource(R.string.news_read_on),
            onClick = { onRead(story.readUrl) },
            modifier = Modifier.padding(top = 24.dp)
        )

        if (story.related.isNotEmpty()) {
            Text(
                text = stringResource(R.string.news_related),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
            )
            story.related.forEach { other ->
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wpTilt(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onRead(other.link) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = other.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (other.source.isNotBlank()) {
                        Text(
                            text = other.source,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = zuneColors.accentColor
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.news_story_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textDim,
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

/** A Windows Phone button: the word in a rectangle drawn with a line, filled in while pressed. */
@Composable
internal fun MetroButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.4f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .wpTilt(interaction)
            .border(2.dp, ink)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp),
            color = ink
        )
    }
}

/** "5 min. ago", "yesterday" - in the phone's language and lower case; null for a story with no date. */
internal fun relativeTime(publishedAt: Long, now: Long = System.currentTimeMillis()): String? {
    if (publishedAt <= 0L) return null
    return DateUtils.getRelativeTimeSpanString(
        publishedAt.coerceAtMost(now),
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString().lowercase(Locale.getDefault())
}

private fun share(context: Context, story: NewsItem) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, story.title)
        .putExtra(Intent.EXTRA_TEXT, "${story.title}\n${story.readUrl}")
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyLink(context: Context, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(link, link))
}
