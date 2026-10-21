package com.serkantkn.zeta.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.util.DeviceInfo

/**
 * "this device", under the launcher's own version on the about page: the device's name, model,
 * Android and its security update, the build, kernel, radio and bootloader, then the processor,
 * memory, storage and screen - as Windows Phone's about page listed the phone under its software.
 * Holding a line copies its value, for a bug report or a support chat.
 */
@Composable
internal fun AboutDeviceGroup() {
    val context = LocalContext.current
    val info = remember { DeviceInfo.read(context) }
    SettingGroup(title = stringResource(R.string.about_this_device)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoLine(stringResource(R.string.about_device_name), info.deviceName)
            InfoLine(stringResource(R.string.about_model), info.model)
            InfoLine(stringResource(R.string.about_android), stringResource(R.string.about_android_value, info.androidVersion, info.apiLevel))
            info.securityPatch?.let { InfoLine(stringResource(R.string.about_security_patch), it) }
            InfoLine(stringResource(R.string.about_build), info.build)
            info.kernel?.let { InfoLine(stringResource(R.string.about_kernel), it) }
            info.baseband?.let { InfoLine(stringResource(R.string.about_baseband), it) }
            info.bootloader?.let { InfoLine(stringResource(R.string.about_bootloader), it) }
            InfoLine(stringResource(R.string.about_processor), stringResource(R.string.about_processor_value, info.processor, info.cores))
            info.memory?.let { InfoLine(stringResource(R.string.about_memory), it) }
            if (info.storageTotal != null && info.storageFree != null) {
                InfoLine(stringResource(R.string.about_storage), stringResource(R.string.about_storage_value, info.storageTotal, info.storageFree))
            }
            if (info.screenWidth > 0) {
                InfoLine(
                    stringResource(R.string.about_screen),
                    stringResource(R.string.about_screen_value, info.screenWidth, info.screenHeight, info.screenDpi)
                )
            }
            Text(
                text = stringResource(R.string.about_copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textDim,
                modifier = Modifier.padding(start = 4.dp)
            )
            SystemSettingRow(
                title = stringResource(R.string.about_more),
                subtitle = stringResource(R.string.about_more_sub),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            )
        }
    }
}

/** A label in small grey type and its value under it; holding it copies the value. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoLine(label: String, value: String) {
    val context = LocalContext.current
    val copied = stringResource(R.string.msg_copied)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = { copy(context, label, value, copied) }
            )
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = LocalZuneColors.current.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun copy(context: Context, label: String, value: String, copied: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
}
