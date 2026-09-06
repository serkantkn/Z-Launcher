package com.serkantkn.zunelauncher.di

import android.content.Context
import com.serkantkn.zunelauncher.ZuneLauncherApp
import com.serkantkn.zunelauncher.data.datastore.AlarmDataStore
import com.serkantkn.zunelauncher.data.datastore.CalendarDataStore
import com.serkantkn.zunelauncher.data.datastore.EmailDataStore
import com.serkantkn.zunelauncher.data.datastore.FavoritePhotosDataStore
import com.serkantkn.zunelauncher.data.datastore.NotesDataStore
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.repository.AppRepository
import com.serkantkn.zunelauncher.data.repository.CallLogRepository
import com.serkantkn.zunelauncher.data.repository.ContactRepository
import com.serkantkn.zunelauncher.data.repository.EmailCache
import com.serkantkn.zunelauncher.data.repository.EmailRepository
import com.serkantkn.zunelauncher.data.repository.MediaRepository
import com.serkantkn.zunelauncher.data.repository.MusicRepository
import com.serkantkn.zunelauncher.data.service.AlarmScheduler
import com.serkantkn.zunelauncher.data.service.EmailSyncScheduler
import com.serkantkn.zunelauncher.util.SecretStore

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
    val calendarDataStore: CalendarDataStore by lazy { CalendarDataStore(appContext) }
    val notesDataStore: NotesDataStore by lazy { NotesDataStore(appContext) }
    val emailDataStore: EmailDataStore by lazy { EmailDataStore(appContext) }

    // --- Repositories ---
    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val mediaRepository: MediaRepository by lazy { MediaRepository(appContext) }
    val contactRepository: ContactRepository by lazy { ContactRepository(appContext) }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(appContext) }
    val musicRepository: MusicRepository by lazy { MusicRepository(appContext) }
    val emailCache: EmailCache by lazy { EmailCache(appContext) }
    val emailRepository: EmailRepository by lazy { EmailRepository(appContext, secretStore, emailCache) }

    // --- Services ---
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(appContext) }
    val emailSyncScheduler: EmailSyncScheduler by lazy { EmailSyncScheduler(appContext) }
    val secretStore: SecretStore by lazy { SecretStore(appContext) }
}

/**
 * Shared [AppContainer] for this process. Works from any Context (Activity, Application,
 * Service, or a composable's LocalContext) because it always resolves through the
 * application context.
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as ZuneLauncherApp).container
