package com.serkantkn.zunelauncher.ui.openwith

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.MusicBridge
import com.serkantkn.zunelauncher.util.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher as the phone's music player: a song another app opens plays in the music hub. Has
 * no screen of its own.
 */
class OpenAudioActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val track = withContext(Dispatchers.IO) { trackOf(uri) }
            if (track != null) MusicBridge.playTrack(track)
            OpenWith.openHub(this@OpenAudioActivity, HubType.MUSIC)
            finish()
        }
    }

    private fun trackOf(uri: Uri): MusicBridge.Track? {
        OpenWith.libraryUri(this, uri, OpenWith.Kind.AUDIO)?.let { library ->
            return MusicBridge.Track.FromLibrary(library.lastPathSegment!!.toLong())
        }
        val name = OpenWith.nameOf(this, uri)
        val copy = OpenWith.copyIn(this, uri, "opened_audio", name) ?: return null
        var title = name.substringBeforeLast('.')
        var artist = ""
        runCatching {
            MediaMetadataRetriever().apply {
                setDataSource(copy.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                release()
            }
        }
        return MusicBridge.Track.FromFile(Uri.fromFile(copy), title, artist)
    }
}
