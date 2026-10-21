package com.serkantkn.zeta.ui.openwith

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.MediaImage
import com.serkantkn.zeta.data.model.ThemeMode
import com.serkantkn.zeta.data.repository.PicturesBridge
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.ui.components.PhotoViewer
import com.serkantkn.zeta.ui.theme.ZuneLauncherTheme
import com.serkantkn.zeta.util.AppLocale
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The launcher as the phone's gallery. A picture from the library opens in the pictures hub, among
 * the rest of its album; any other picture - an attachment, a download another app holds - opens
 * here, in the hub's own viewer, on its own.
 */
class ImageViewerActivity : ComponentActivity() {

    private var photo by mutableStateOf<MediaImage?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        setContent {
            val settings = appContainer.settingsDataStore
            val accent by settings.accentColor.collectAsState(initial = com.serkantkn.zeta.data.model.AccentColor.MAGENTA)
            val dynamic by settings.dynamicThemeColor.collectAsState(initial = null)
            val custom by settings.customThemeColor.collectAsState(initial = null)
            val fontScale by settings.fontScale.collectAsState(initial = 1.0f)
            val animations by settings.animationsEnabled.collectAsState(initial = true)
            // A picture is looked at on black, whatever the launcher's own theme.
            ZuneLauncherTheme(
                themeMode = ThemeMode.DARK,
                accentColor = accent,
                dynamicThemeColor = dynamic,
                customThemeColor = custom,
                fontScale = fontScale,
                animationsEnabled = animations
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    PhotoViewer(
                        photos = photo?.let { listOf(it) },
                        favoritePhotoIds = emptySet(),
                        onDismiss = { finish() },
                        onToggleFavorite = {},
                        onShare = { share(it) },
                        onSetWallpaper = { setAsWallpaper(it) },
                        standalone = true
                    )
                }
            }
        }
        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent?) {
        val uri = intent?.data ?: run { finish(); return }
        OpenWith.libraryUri(this, uri, OpenWith.Kind.IMAGE)?.let { library ->
            PicturesBridge.openPhoto(library)
            OpenWith.openHub(this, HubType.PICTURES)
            finish()
            return
        }
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { OpenWith.nameOf(this@ImageViewerActivity, uri) }
            val copy: File? = withContext(Dispatchers.IO) { OpenWith.copyIn(this@ImageViewerActivity, uri, "opened_images", name) }
            if (copy == null) {
                finish()
                return@launch
            }
            val shared = FileProvider.getUriForFile(this@ImageViewerActivity, "$packageName.fileprovider", copy)
            photo = MediaImage(
                id = -1L,
                uri = shared,
                dateAdded = copy.lastModified() / 1000,
                bucketId = -1L,
                bucketName = "",
                displayName = name,
                sizeBytes = copy.length(),
                mimeType = intent.type ?: contentResolver.getType(uri) ?: "image/*"
            )
        }
    }

    private fun share(image: MediaImage) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND)
                .setType(image.mimeType.ifBlank { "image/*" })
                .putExtra(Intent.EXTRA_STREAM, image.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri(null, image.uri)
            startActivity(Intent.createChooser(send, null))
        }.onFailure { ZuneLog.w(TAG, "nothing here shares a picture", it) }
    }

    private fun setAsWallpaper(image: MediaImage) {
        runCatching {
            val attach = Intent(Intent.ACTION_ATTACH_DATA)
                .setDataAndType(image.uri, image.mimeType.ifBlank { "image/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra("mimeType", image.mimeType.ifBlank { "image/*" })
            attach.clipData = android.content.ClipData.newRawUri(null, image.uri)
            startActivity(Intent.createChooser(attach, null))
        }.onFailure { ZuneLog.w(TAG, "the wallpaper chooser would not open", it) }
    }

    private companion object {
        const val TAG = "ImageViewerActivity"
    }
}
