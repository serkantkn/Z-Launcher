package com.serkantkn.zunelauncher

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.NoteFallbackTitles
import com.serkantkn.zunelauncher.data.service.TimerNotifier
import com.serkantkn.zunelauncher.di.AppContainer
import com.serkantkn.zunelauncher.util.DeviceClass
import com.serkantkn.zunelauncher.util.WallpaperImages
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

    override fun onCreate() {
        super.onCreate()
        NoteFallbackTitles.refresh(localized())
        rearmClock()
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
     * Books the alarms and any running timer again on a cold start.
     *
     * Force-stopping an app cancels every alarm it had booked — being force-stopped, updated or
     * cleared is exactly when an alarm silently stops existing, and the person who set it has no
     * way of knowing. Re-booking on start costs nothing (the same PendingIntent simply replaces
     * itself) and is the difference between an alarm that rings and one that does not.
     */
    private fun rearmClock() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val alarms = container.alarmDataStore.alarmsFlow.first()
                alarms.filter { it.isEnabled }.forEach { container.alarmScheduler.schedule(it) }

                val timer = container.clockDataStore.timer.first()
                if (timer.isRunning && timer.endsAtMillis > System.currentTimeMillis()) {
                    container.timerScheduler.schedule(timer.endsAtMillis)
                    TimerNotifier.showRunning(this@ZuneLauncherApp, timer.endsAtMillis, timer.label)
                }
            } catch (e: Exception) {
                ZuneLog.e("ZuneLauncherApp", "the clock would not re-arm on start", e)
            }
        }
    }
}
