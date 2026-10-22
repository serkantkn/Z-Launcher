package com.serkantkn.zeta

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zeta.data.model.NoteFallbackTitles
import com.serkantkn.zeta.data.service.ClockRearm
import com.serkantkn.zeta.di.AppContainer
import com.serkantkn.zeta.util.DeviceClass
import com.serkantkn.zeta.util.TimeText
import com.serkantkn.zeta.util.WallpaperImages
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "zune_settings")

class ZuneLauncherApp : Application(), coil.ImageLoaderFactory {

    /**
     * The image loader every [coil.compose.AsyncImage] in the launcher uses.
     *
     * It exists for one reason: a video is not a picture file, so the default loader hands the raw
     * stream to the bitmap decoder and gets nothing. [coil.decode.VideoFrameDecoder] pulls the
     * first frame instead, which is what a clip looks like in the gallery.
     */
    override fun newImageLoader(): coil.ImageLoader =
        coil.ImageLoader.Builder(this)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) }
            .crossfade(true)
            // A launcher is meant to still be in memory when it is asked for, so it takes a
            // smaller share of the heap for pictures than an app that can afford to be killed —
            // and a smaller share again on a phone that has little to give.
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(if (DeviceClass.isLowEnd(this)) 0.10 else 0.20)
                    .build()
            }
            .build()

    /** Single shared dependency container for the whole process. See [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * A configuration change resets the process's default locale to the phone's. Started by a
     * service, the launcher's own language is not part of the process configuration, so it is
     * put back each time.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.serkantkn.zeta.util.AppLocale.applyDefault(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Whatever started the process — an activity, the accessibility service, a receiver —
        // the dates and numbers it formats come out in the launcher's language.
        com.serkantkn.zeta.util.AppLocale.applyDefault(this)
        // Pro from what was last known, before anything is drawn; then from Google Play.
        com.serkantkn.zeta.data.billing.ProStore.start(this)
        // Other apps' hubs (PLATFORM.md): which are installed, kept up to date as apps come and go.
        com.serkantkn.zeta.data.hubs.HubDirectory.start(this)
        com.serkantkn.zeta.data.hubs.HubTileStore.start(this)
        NoteFallbackTitles.refresh(localized())
        bindTimeText()
        rearmClock()
        sealLockedNotes()
        // A TV listens for its paired phones from the start (data/link); a phone does nothing here.
        if (com.serkantkn.zeta.ui.theme.isTelevision(this)) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { com.serkantkn.zeta.data.link.TvLink.start(this@ZuneLauncherApp) }
                    .onFailure { ZuneLog.w("ZuneLauncherApp", "the phone link could not start", it) }
                runCatching { com.serkantkn.zeta.ui.tv.TvAppNews.start(this@ZuneLauncherApp) }
                    .onFailure { ZuneLog.w("ZuneLauncherApp", "app news could not start", it) }
            }
        }
    }

    /**
     * Keeps [TimeText] — the process-wide "12 or 24 hours" — in step with the setting, so every
     * clock reading in the launcher is written the same way the Start tile writes it.
     */
    private fun bindTimeText() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { TimeText.bind(container.settingsDataStore.timeFormat) }
                .onFailure { ZuneLog.w("ZuneLauncherApp", "the time format could not be followed", it) }
        }
    }

    /**
     * Locks away any note that was marked private before notes were encrypted.
     *
     * Done here rather than in the hub because a note somebody locked is locked whether or not
     * they ever open the notes hub again, and until it has been written back it is sitting in
     * the file in plain text.
     */
    private fun sealLockedNotes() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.notesDataStore.sealLegacyNotes() }
                .onFailure { ZuneLog.w("ZuneLauncherApp", "locked notes could not be sealed", it) }
        }
    }

    /**
     * Gives memory back when Android asks for it.
     *
     * The launcher is the process every other app is launched from, so being killed for holding
     * onto app icons and a wallpaper is the worst trade it could make: coming back from a kill
     * is a cold start with a blank screen, while re-reading an icon is one call to the package
     * manager. Anything that can be read again is let go of as soon as memory is short.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_RUNNING_LOW) return
        runCatching {
            coil.Coil.imageLoader(this).memoryCache?.clear()
            container.appRepository.trimIconCache()
            if (level >= TRIM_MEMORY_COMPLETE) WallpaperImages.trim()
        }.onFailure { ZuneLog.w("ZuneLauncherApp", "could not give memory back", it) }
    }

    /**
     * Books the alarms, the reminders and any running timer again on a cold start.
     *
     * Force-stopping an app cancels every alarm it had booked — being force-stopped, updated or
     * cleared is exactly when an alarm silently stops existing, and the person who set it has no
     * way of knowing. Re-booking on start costs nothing (the same PendingIntent simply replaces
     * itself) and is the difference between an alarm that rings and one that does not. The walk
     * itself is [ClockRearm], the same one the boot and time-change receivers make, so a reminder
     * whose moment has already passed is marked missed here too rather than booked in the past.
     */
    private fun rearmClock() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ClockRearm.rearmAll(this@ZuneLauncherApp, "cold start")
            } catch (e: Exception) {
                ZuneLog.e("ZuneLauncherApp", "the clock would not re-arm on start", e)
            }
        }
    }
}
