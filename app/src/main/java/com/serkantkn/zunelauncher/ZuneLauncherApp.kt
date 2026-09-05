package com.serkantkn.zunelauncher

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.di.AppContainer

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "zune_settings")

class ZuneLauncherApp : Application() {

    /** Single shared dependency container for the whole process. See [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(this) }
}
