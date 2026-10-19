package com.serkantkn.zunelauncher.ui.screens.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.ActionCenterPermissions
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * How Z Launcher becomes Home on a television. Google TV keeps the remote's Home button for its
 * own launcher whatever Android's default is, so there the button is caught by the accessibility
 * service; older Android TV boxes take the default home screen as a phone does.
 *
 * [checks] goes up whenever the user comes back from a system page, so what is shown is read
 * afresh.
 */
@Composable
internal fun TvHomeGroup(checks: Int, roleLauncher: ActivityResultLauncher<Intent>) {
    val context = LocalContext.current
    val serviceOn = remember(checks) { ZuneKeyAccessibilityService.isEnabled(context) }
    val isHome = remember(checks) { holdsHome(context) }
    val googleTv = remember { isGoogleTv(context) }
    var disclosureOpen by remember { mutableStateOf(false) }
    if (disclosureOpen) {
        AccessibilityDisclosureDialog(
            onAccept = { ActionCenterPermissions.open(context, ActionCenterPermissions.SERVICE) },
            onDismiss = { disclosureOpen = false },
            body = stringResource(R.string.a11y_disclosure_body_tv)
        )
    }

    SettingGroup(title = stringResource(R.string.tv_home_group)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.tv_home_intro),
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            SystemSettingRow(
                title = stringResource(R.string.tv_home_key),
                subtitle = stringResource(if (serviceOn) R.string.tv_home_key_on else R.string.tv_home_key_off),
                isActive = serviceOn,
                // Turning it off needs no explaining; turning it on does.
                onClick = {
                    if (serviceOn) ActionCenterPermissions.open(context, ActionCenterPermissions.SERVICE)
                    else disclosureOpen = true
                }
            )
            // An app not installed from a store is kept from accessibility until the user allows
            // it on its own page (Android 13 on).
            if (!serviceOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SystemSettingRow(
                    title = stringResource(R.string.ac_perm_restricted),
                    subtitle = stringResource(R.string.ac_perm_restricted_sub),
                    onClick = { ActionCenterPermissions.open(context, ActionCenterPermissions.APP_INFO) }
                )
            }
            SystemSettingRow(
                title = stringResource(R.string.tv_home_default),
                subtitle = stringResource(if (googleTv) R.string.tv_home_default_google else R.string.tv_home_default_sub),
                isActive = isHome && !googleTv,
                onClick = { askForHome(context, roleLauncher) }
            )
        }
    }
}

private fun holdsHome(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

/** Google TV's own launcher is installed: the Home button goes to it whatever the default. */
private fun isGoogleTv(context: Context): Boolean =
    runCatching { context.packageManager.getPackageInfo(GOOGLE_TV_LAUNCHER, 0) }.isSuccess

/** Android's own question, where it has one; its home settings page where not. */
private fun askForHome(context: Context, roleLauncher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            runCatching { roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onSuccess { return }
                .onFailure { ZuneLog.w(TAG, "the home role could not be asked for", it) }
        }
    }
    listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS)).forEach { intent ->
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

private const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"
private const val TAG = "TvHomeSettings"
