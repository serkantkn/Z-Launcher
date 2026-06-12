cat app/src/main/java/com/serkantkn/zunelauncher/ui/screens/settings/SettingsViewModel.kt > tmp_vm.kt
sed -i '' '/val customWallpaperPath/a\
    val dynamicThemeColor: StateFlow<Int?> = settingsRepository.dynamicThemeColor\
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)\
' tmp_vm.kt
sed -i '' '/settingsRepository.setCustomWallpaperPath(file.absolutePath)/a\
\
                // Extract dominant color\
                androidx.palette.graphics.Palette.from(bitmap).generate { palette ->\
                    val dominantColor = palette?.dominantSwatch?.rgb ?: palette?.vibrantSwatch?.rgb\
                    viewModelScope.launch {\
                        settingsRepository.setDynamicThemeColor(dominantColor)\
                    }\
                }\
' tmp_vm.kt
sed -i '' '/settingsRepository.setCustomWallpaperPath(null)/a\
            settingsRepository.setDynamicThemeColor(null)\
' tmp_vm.kt
cat tmp_vm.kt > app/src/main/java/com/serkantkn/zunelauncher/ui/screens/settings/SettingsViewModel.kt
