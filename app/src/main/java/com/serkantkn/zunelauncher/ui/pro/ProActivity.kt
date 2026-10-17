package com.serkantkn.zunelauncher.ui.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.data.billing.ProStore
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import java.util.Locale

/**
 * "z launcher pro": what Pro adds, what it costs, and the way to buy it - Google Play's own
 * purchase sheet. Every locked thing in the free build leads here, saying at the top what was
 * asked for.
 */
class ProActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reason = intent.getIntExtra(EXTRA_REASON, 0).takeIf { it != 0 }
        ProStore.restore()
        setContent {
            val settings = appContainer.settingsDataStore
            val theme by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
            val accent by settings.accentColor.collectAsState(initial = AccentColor.MAGENTA)
            val dynamic by settings.dynamicThemeColor.collectAsState(initial = null)
            val custom by settings.customThemeColor.collectAsState(initial = null)
            val fontScale by settings.fontScale.collectAsState(initial = 1.0f)
            ZuneLauncherTheme(
                themeMode = theme,
                accentColor = accent,
                dynamicThemeColor = dynamic,
                customThemeColor = custom,
                fontScale = fontScale
            ) {
                ProPage(reason = reason, onBuy = { ProStore.buy(this) }, onRestore = { ProStore.restore() }, onClose = ::finish)
            }
        }
    }

    companion object {
        /** What was asked for, as a string resource; shown at the top of the page. */
        const val EXTRA_REASON = "pro_reason"
    }
}

@Composable
private fun ProPage(@StringRes reason: Int?, onBuy: () -> Unit, onRestore: () -> Unit, onClose: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    val status by ProStore.status.collectAsState()
    val product by ProStore.product.collectAsState()
    val price = product?.oneTimePurchaseOfferDetails?.formattedPrice

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Z LAUNCHER",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                color = ink
            )
            Text(
                text = stringResource(R.string.pro_title),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light, fontSize = 64.sp),
                color = ink
            )
            reason?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = zuneColors.accentColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.pro_lead),
                style = MaterialTheme.typography.bodyLarge,
                color = ink.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp)
            )

            ProGroup(R.string.pro_group_looks, R.string.pro_group_looks_body)
            ProGroup(R.string.pro_group_media, R.string.pro_group_media_body)
            ProGroup(R.string.pro_group_hubs, R.string.pro_group_hubs_body)
            ProGroup(R.string.pro_group_system, R.string.pro_group_system_body)

            Text(
                text = stringResource(R.string.pro_nothing_locked_away),
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 20.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))
            when (status) {
                ProStore.Status.OWNED -> StatusLine(stringResource(R.string.pro_owned), accent = true)
                ProStore.Status.PENDING -> StatusLine(stringResource(R.string.pro_pending))
                ProStore.Status.CONNECTING -> StatusLine(stringResource(R.string.pro_connecting))
                ProStore.Status.UNAVAILABLE -> StatusLine(stringResource(R.string.pro_unavailable))
                ProStore.Status.READY -> BuyButton(
                    label = if (price != null) stringResource(R.string.pro_buy_for, price) else stringResource(R.string.pro_buy),
                    onClick = onBuy
                )
            }
            if (status != ProStore.Status.OWNED) {
                Text(
                    text = stringResource(R.string.pro_restore),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRestore)
                        .padding(vertical = 6.dp)
                )
            }
            Text(
                text = stringResource(R.string.common_close),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = ink.copy(alpha = 0.75f),
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 32.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                    .padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProGroup(@StringRes title: Int, @StringRes body: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
        color = LocalZuneColors.current.accentColor,
        modifier = Modifier.padding(top = 22.dp)
    )
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun StatusLine(text: String, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
        color = if (accent) LocalZuneColors.current.accentColor else MaterialTheme.colorScheme.onBackground
    )
}

/** The accent, filled, with the price in it: the one thing on the page meant to be pressed. */
@Composable
private fun BuyButton(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalZuneColors.current.accentColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 20.sp),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp).size(0.dp))
    }
}
