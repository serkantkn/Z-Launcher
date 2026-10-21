package com.serkantkn.zeta.ui.screens.browser

import com.serkantkn.zeta.ui.components.swallowTaps
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import java.util.Locale

// ── How safe the page is ─────────────────────────────────────────────────────────────────────

/** What the address bar says of the connection to the page on show. */
enum class PageSecurity {
    /** No page: the start screen. */
    NONE,

    /** https with a certificate that held up. */
    SECURE,

    /** Plain http: nothing sent or received is encrypted. */
    INSECURE,

    /** https whose certificate did not hold up, opened anyway at the user's word. */
    CERTIFICATE_IGNORED,

    /** https whose certificate did not hold up, with the warning on show. */
    CERTIFICATE_PROBLEM;

    companion object {
        fun of(url: String, certificateIgnored: Boolean): PageSecurity = when {
            url.isBlank() -> NONE
            url.startsWith("https://") -> if (certificateIgnored) CERTIFICATE_IGNORED else SECURE
            url.startsWith("http://") -> INSECURE
            else -> NONE
        }
    }
}

/**
 * The padlock at the start of the address: closed and quiet for a secure page, open for a plain
 * one, a warning in the accent for a page whose certificate was waved through. Touched, it says
 * in a balloon what that means.
 */
@Composable
internal fun SecurityBadge(security: PageSecurity, balloonBelow: Boolean, modifier: Modifier = Modifier) {
    if (security == PageSecurity.NONE) return
    val zuneColors = LocalZuneColors.current
    var balloon by remember(security) { mutableStateOf(false) }
    LaunchedEffect(balloon) {
        if (balloon) {
            delay(SECURITY_BALLOON_MILLIS)
            balloon = false
        }
    }
    val (icon, tint) = when (security) {
        PageSecurity.SECURE -> Icons.Default.Lock to zuneColors.textMuted
        PageSecurity.INSECURE -> Icons.Default.LockOpen to zuneColors.textMuted
        else -> Icons.Default.Warning to zuneColors.accentColor
    }
    val (title, message) = when (security) {
        PageSecurity.SECURE -> R.string.browser_security_secure to R.string.browser_security_secure_detail
        PageSecurity.INSECURE -> R.string.browser_security_insecure to R.string.browser_security_insecure_detail
        PageSecurity.CERTIFICATE_PROBLEM -> R.string.browser_security_ignored to R.string.browser_security_problem_detail
        else -> R.string.browser_security_ignored to R.string.browser_security_ignored_detail
    }
    Box(
        modifier = modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { balloon = !balloon },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(title),
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        if (balloon) {
            MetroBalloon(
                title = stringResource(title),
                message = stringResource(message),
                below = balloonBelow,
                onDismiss = { balloon = false }
            )
        }
    }
}

private const val SECURITY_BALLOON_MILLIS = 6_000L

// ── A certificate that does not hold up ──────────────────────────────────────────────────────

/** The page being opened, its certificate's fault, and the way on or back. */
internal class CertificateProblem(
    val url: String,
    error: SslError,
    private val handler: android.webkit.SslErrorHandler
) {
    /**
     * The fault worth telling: a certificate can fail several ways at once, and Android names
     * the dullest ("the date is invalid") as the primary one when it has simply expired.
     */
    val reason: Int = listOf(
        SslError.SSL_IDMISMATCH,
        SslError.SSL_EXPIRED,
        SslError.SSL_NOTYETVALID,
        SslError.SSL_UNTRUSTED,
        SslError.SSL_DATE_INVALID
    ).firstOrNull { error.hasError(it) } ?: SslError.SSL_INVALID

    val host: String get() = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()

    fun goBack() = runCatching { handler.cancel() }
    fun proceed() = runCatching { handler.proceed() }
}

/**
 * What stands in place of a page whose certificate does not hold up: what is wrong, in plain
 * words, and the way back as the thing to do. Going on regardless is there, quieter, as it is in
 * every browser - it is the user's connection.
 */
@Composable
internal fun CertificateWarningPage(problem: CertificateProblem, onBack: () -> Unit, onProceed: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val reason = when (problem.reason) {
        SslError.SSL_EXPIRED -> R.string.browser_cert_expired
        SslError.SSL_NOTYETVALID -> R.string.browser_cert_not_yet_valid
        SslError.SSL_IDMISMATCH -> R.string.browser_cert_mismatch
        SslError.SSL_UNTRUSTED -> R.string.browser_cert_untrusted
        SslError.SSL_DATE_INVALID -> R.string.browser_cert_date_invalid
        else -> R.string.browser_cert_invalid
    }
    var showMore by remember(problem) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = zuneColors.accentColor,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = stringResource(R.string.browser_cert_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 36.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = problem.host,
            style = MaterialTheme.typography.titleMedium,
            color = zuneColors.accentColor,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = stringResource(reason),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = stringResource(R.string.browser_cert_advice),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
        PageButton(
            label = stringResource(R.string.browser_cert_back),
            filled = true,
            onClick = onBack,
            modifier = Modifier.padding(top = 28.dp)
        )
        Text(
            text = stringResource(if (showMore) R.string.browser_cert_less else R.string.browser_cert_more),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = zuneColors.textMuted,
            modifier = Modifier
                .padding(top = 24.dp)
                .clickable { showMore = !showMore }
                .padding(vertical = 6.dp)
        )
        if (showMore) {
            Text(
                text = stringResource(R.string.browser_cert_proceed_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(R.string.browser_cert_proceed, problem.host),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(onClick = onProceed)
                    .padding(vertical = 6.dp)
            )
        }
    }
}

/** A Windows Phone button: the word in a ruled rectangle, or on the accent when it is the one to press. */
@Composable
internal fun PageButton(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .wpTilt(interaction)
            .then(if (filled) Modifier.background(zuneColors.accentColor) else Modifier.border(2.dp, ink))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = if (filled) Color.White else ink
        )
    }
}

// ── What can be done with a link or a picture ────────────────────────────────────────────────

internal class PageMenuAction(val label: String, val onClick: () -> Unit)

/**
 * The menu for a link or a picture that was held down. On a phone it rises from the bottom, the
 * width of the screen, as Windows Phone's menus did; on a tablet it opens where the finger is.
 * The address it is about heads it, so it is plain what is being opened or copied.
 */
@Composable
internal fun PageContextMenu(
    target: PageTarget,
    x: Float,
    y: Float,
    wide: Boolean,
    actions: List<PageMenuAction>,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val menuColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
    val appear = remember(target) { MutableTransitionState(false).apply { targetState = true } }
    val subject = (target.link ?: target.image).orEmpty().let { if (it.startsWith("data:")) it.take(40) + "…" else it }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        val density = LocalDensity.current
        val panelWidth = 340.dp
        val content: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .background(menuColor)
                    .swallowTaps()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = zuneColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                actions.forEach { action ->
                    WP8MenuItem(text = action.label, subtitle = null, enabled = true) {
                        onDismiss()
                        action.onClick()
                    }
                }
            }
        }
        if (wide) {
            // Opened at the finger, kept wholly on the page.
            val widthPx = with(density) { panelWidth.toPx() }
            val left = x.coerceIn(0f, (constraints.maxWidth - widthPx).coerceAtLeast(0f))
            val estimatedHeight = with(density) { (64 + 44 * actions.size).dp.toPx() }
            val top = y.coerceIn(0f, (constraints.maxHeight - estimatedHeight).coerceAtLeast(0f))
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.92f),
                modifier = Modifier
                    .offset { IntOffset(left.toInt(), top.toInt()) }
                    .width(panelWidth)
            ) { content() }
        } else {
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) { content() }
        }
    }
}

/** Puts [text] on the clipboard; the phone says so itself from Android 13, older ones get a word. */
internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.browser_copied), Toast.LENGTH_SHORT).show()
    }
}

// ── The menu's further entries ───────────────────────────────────────────────────────────────

/**
 * An entry the hub adds to the browser's menu - the same list for the phone's bar and the
 * tablet's header, so the two never offer different things.
 */
class BrowserMenuEntry(val label: String, val enabled: Boolean, val onClick: () -> Unit)

// ── Text size ────────────────────────────────────────────────────────────────────────────────

/**
 * The text size for the site on show: the sizes as a row of squares, the chosen one filled in.
 * The size stays with the site; "use on every site" makes it every site's.
 */
@Composable
internal fun TextSizePanel(
    host: String?,
    current: Int,
    wide: Boolean,
    onPick: (Int) -> Unit,
    onPickForAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val menuColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { if (wide) -it / 3 else it / 3 },
            modifier = Modifier
                .align(if (wide) Alignment.TopEnd else Alignment.BottomCenter)
                .then(if (wide) Modifier.padding(top = 4.dp, end = 12.dp).width(420.dp) else Modifier.fillMaxWidth())
        ) {
            Column(
                modifier = Modifier
                    .background(menuColor)
                    .swallowTaps()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.browser_text_size),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (host != null) {
                    Text(
                        text = stringResource(R.string.browser_text_size_site, host),
                        style = MaterialTheme.typography.bodySmall,
                        color = zuneColors.textMuted
                    )
                }
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    com.serkantkn.zeta.ui.screens.browser.TextSizes.CHOICES.forEach { size ->
                        val chosen = size == current
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 44.dp)
                                .border(2.dp, if (chosen) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.5f))
                                .background(if (chosen) zuneColors.accentColor else Color.Transparent)
                                .clickable { onPick(size) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$size%",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = (11 + size / 25).sp),
                                color = if (chosen) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.browser_text_size_all),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable(onClick = onPickForAll)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

// ── Pull to refresh ──────────────────────────────────────────────────────────────────────────

/**
 * How far the page has been pulled down past its top: a line in the accent growing across the
 * top of the page, and once it is far enough, the word that letting go will load the page again.
 */
@Composable
internal fun PullToRefreshIndicator(fraction: Float, modifier: Modifier = Modifier) {
    if (fraction <= 0f) return
    val zuneColors = LocalZuneColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .size(height = 4.dp, width = 0.dp)
                .background(zuneColors.accentColor)
        )
        if (fraction >= 1f) {
            Text(
                text = stringResource(R.string.browser_pull_release),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(zuneColors.accentColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ── Ads and trackers ─────────────────────────────────────────────────────────────────────────

/** What the blocker has done on the page on show, for the badge beside the padlock. */
class BlockingState(val blocked: Int, val offHere: Boolean, val host: String?) {
    val shown: Boolean get() = host != null && (blocked > 0 || offHere)
}

/**
 * A shield with the number refused on this page; touched, a balloon says so and offers to switch
 * blocking off for the site - or, where it is off, back on.
 */
@Composable
internal fun BlockingBadge(state: BlockingState, balloonBelow: Boolean, onToggleSite: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.shown) return
    val zuneColors = LocalZuneColors.current
    var balloon by remember(state.host) { mutableStateOf(false) }
    LaunchedEffect(balloon) {
        if (balloon) {
            delay(SECURITY_BALLOON_MILLIS)
            balloon = false
        }
    }
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { balloon = !balloon }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (state.offHere) Icons.Default.RemoveModerator else Icons.Default.Shield,
            contentDescription = stringResource(R.string.browser_blocking),
            tint = if (state.offHere) zuneColors.textMuted else zuneColors.accentColor,
            modifier = Modifier.size(16.dp)
        )
        if (!state.offHere) {
            Text(
                text = state.blocked.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = zuneColors.accentColor,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        if (balloon) {
            MetroBalloon(
                title = if (state.offHere) stringResource(R.string.browser_blocking_off_here)
                else androidx.compose.ui.res.pluralStringResource(R.plurals.browser_blocked_count, state.blocked, state.blocked),
                message = stringResource(if (state.offHere) R.string.browser_blocking_turn_on_here else R.string.browser_blocking_turn_off_here),
                below = balloonBelow,
                onDismiss = { balloon = false },
                onClick = {
                    balloon = false
                    onToggleSite()
                }
            )
        }
    }
}

// ── The address bar put away while reading ───────────────────────────────────────────────────

/**
 * The phone's address bar folded down while a page is being read, as Internet Explorer on
 * Windows Phone 8.1 folded it: a thin strip with the site's name and the padlock, which opens the
 * bar again when touched.
 */
@Composable
internal fun FoldedAddressBar(host: String, security: PageSecurity, progress: Float, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val barColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
    ) {
        if (progress > 0f && progress < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .size(height = 2.dp, width = 0.dp)
                    .background(zuneColors.accentColor)
            )
        }
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            val icon = when (security) {
                PageSecurity.SECURE -> Icons.Default.Lock
                PageSecurity.INSECURE -> Icons.Default.LockOpen
                PageSecurity.NONE -> null
                else -> Icons.Default.Warning
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (security == PageSecurity.SECURE || security == PageSecurity.INSECURE) zuneColors.textMuted else zuneColors.accentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = host,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f)
            )
            Text(
                text = "•••",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }
    }
}
