sed -i '' '/val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")/a\
        val DYNAMIC_THEME_COLOR = intPreferencesKey("dynamic_theme_color")\
' app/src/main/java/com/serkantkn/zunelauncher/data/datastore/SettingsDataStore.kt
