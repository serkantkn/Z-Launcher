package com.serkantkn.zunelauncher.di

import android.content.Context
import com.serkantkn.zunelauncher.ZuneLauncherApp
import com.serkantkn.zunelauncher.data.datastore.AlarmDataStore
import com.serkantkn.zunelauncher.data.datastore.CalculatorDataStore
import com.serkantkn.zunelauncher.data.datastore.CalendarDataStore
import com.serkantkn.zunelauncher.data.datastore.ClockDataStore
import com.serkantkn.zunelauncher.data.datastore.CloudDataStore
import com.serkantkn.zunelauncher.data.datastore.EmailDataStore
import com.serkantkn.zunelauncher.data.datastore.FavoritePhotosDataStore
import com.serkantkn.zunelauncher.data.datastore.KeyboardDataStore
import com.serkantkn.zunelauncher.data.datastore.NotesDataStore
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.datastore.CameraDataStore
import com.serkantkn.zunelauncher.data.datastore.PhoneDataStore
import com.serkantkn.zunelauncher.data.datastore.WeatherDataStore
import com.serkantkn.zunelauncher.data.repository.AppRepository
import com.serkantkn.zunelauncher.data.repository.IconPackRepository
import com.serkantkn.zunelauncher.data.repository.CalendarRepository
import com.serkantkn.zunelauncher.data.repository.CallLogRepository
import com.serkantkn.zunelauncher.data.repository.CloudAuthRepository
import com.serkantkn.zunelauncher.data.repository.CloudStorageRepository
import com.serkantkn.zunelauncher.data.repository.ContactRepository
import com.serkantkn.zunelauncher.data.repository.EmailCache
import com.serkantkn.zunelauncher.data.repository.EmailRepository
import com.serkantkn.zunelauncher.data.repository.MediaRepository
import com.serkantkn.zunelauncher.data.repository.MusicRepository
import com.serkantkn.zunelauncher.data.repository.WeatherRepository
import com.serkantkn.zunelauncher.data.service.AlarmScheduler
import com.serkantkn.zunelauncher.data.service.EmailSyncScheduler
import com.serkantkn.zunelauncher.data.service.EventReminderScheduler
import com.serkantkn.zunelauncher.data.service.TimerScheduler
import com.serkantkn.zunelauncher.util.SecretStore
import com.serkantkn.zunelauncher.util.TileIconFactory

/**
 * Process-wide, manually wired dependency container.
 *
 * Every repository / data store is created once and shared across all ViewModels and
 * composables, so caches (e.g. [AppRepository]'s icon LRU) are not duplicated per screen.
 * Everything is lazy: nothing is touched until a screen actually needs it.
 *
 * Obtain it via [Context.appContainer].
 */
class AppContainer(private val appContext: Context) {

    // --- Data stores ---
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(appContext) }
    val favoritePhotosDataStore: FavoritePhotosDataStore by lazy { FavoritePhotosDataStore(appContext) }
    val alarmDataStore: AlarmDataStore by lazy { AlarmDataStore(appContext) }
    val clockDataStore: ClockDataStore by lazy { ClockDataStore(appContext) }
    val calendarDataStore: CalendarDataStore by lazy { CalendarDataStore(appContext) }
    val notesDataStore: NotesDataStore by lazy { NotesDataStore(appContext) }
    val emailDataStore: EmailDataStore by lazy { EmailDataStore(appContext) }
    val calculatorDataStore: CalculatorDataStore by lazy { CalculatorDataStore(appContext) }
    val cloudDataStore: CloudDataStore by lazy { CloudDataStore(appContext) }
    val keyboardDataStore: KeyboardDataStore by lazy { KeyboardDataStore(appContext) }
    val weatherDataStore: WeatherDataStore by lazy { WeatherDataStore(appContext) }
    val cameraDataStore: CameraDataStore by lazy { CameraDataStore(appContext) }
    val phoneDataStore: PhoneDataStore by lazy { PhoneDataStore(appContext) }

    // --- Repositories ---
    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val iconPackRepository: IconPackRepository by lazy { IconPackRepository(appContext) }
    val mediaRepository: MediaRepository by lazy { MediaRepository(appContext) }
    val contactRepository: ContactRepository by lazy { ContactRepository(appContext) }
    val cloudAuthRepository: CloudAuthRepository by lazy { CloudAuthRepository(appContext, secretStore) }
    val cloudStorageRepository: CloudStorageRepository by lazy {
        CloudStorageRepository(appContext, cloudDataStore, cloudAuthRepository)
    }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(appContext) }
    val calendarRepository: CalendarRepository by lazy { CalendarRepository(appContext) }
    val weatherRepository: WeatherRepository by lazy { WeatherRepository(appContext, weatherDataStore) }
    val musicRepository: MusicRepository by lazy { MusicRepository(appContext) }
    val lyricsRepository: com.serkantkn.zunelauncher.data.repository.LyricsRepository by lazy {
        com.serkantkn.zunelauncher.data.repository.LyricsRepository(appContext)
    }
    val artworkRepository: com.serkantkn.zunelauncher.data.repository.ArtworkRepository by lazy {
        com.serkantkn.zunelauncher.data.repository.ArtworkRepository(appContext)
    }
    val musicDataStore: com.serkantkn.zunelauncher.data.datastore.MusicDataStore by lazy {
        com.serkantkn.zunelauncher.data.datastore.MusicDataStore(appContext)
    }
    val emailCache: EmailCache by lazy { EmailCache(appContext) }
    val emailRepository: EmailRepository by lazy { EmailRepository(appContext, secretStore, emailCache) }

    // --- Services ---
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(appContext) }
    val timerScheduler: TimerScheduler by lazy { TimerScheduler(appContext) }
    val eventReminderScheduler: EventReminderScheduler by lazy { EventReminderScheduler(appContext) }
    val emailSyncScheduler: EmailSyncScheduler by lazy { EmailSyncScheduler(appContext) }
    val secretStore: SecretStore by lazy { SecretStore(appContext) }
    val tileIconFactory: TileIconFactory by lazy {
        TileIconFactory(appContext, appRepository, iconPackRepository)
    }
}

/**
 * Shared [AppContainer] for this process. Works from any Context (Activity, Application,
 * Service, or a composable's LocalContext) because it always resolves through the
 * application context.
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as ZuneLauncherApp).container
