cat app/src/main/java/com/serkantkn/zunelauncher/ui/screens/settings/SettingsViewModel.kt > tmp_vm.kt
sed -i '' '/import kotlinx.coroutines.flow.StateFlow/a\
import kotlinx.coroutines.flow.firstOrNull\
' tmp_vm.kt
sed -i '' '/class SettingsViewModel/a\
\
    init {\
        viewModelScope.launch {\
            val path = settingsRepository.customWallpaperPath.firstOrNull()\
            val color = settingsRepository.dynamicThemeColor.firstOrNull()\
            if (path != null && color == null) {\
                try {\
                    val file = java.io.File(path)\
                    if (file.exists()) {\
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)\
                        if (bitmap != null) {\
                            androidx.palette.graphics.Palette.from(bitmap).generate { palette ->\
                                val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch ?: palette?.swatches?.maxByOrNull { it.population }\
                                if (swatch != null) {\
                                    viewModelScope.launch {\
                                        settingsRepository.setDynamicThemeColor(swatch.rgb)\
                                    }\
                                }\
                            }\
                        }\
                    }\
                } catch (e: Exception) {}\
            }\
        }\
    }\
' tmp_vm.kt
sed -i '' 's/val dominantColor = palette?.dominantSwatch?.rgb ?: palette?.vibrantSwatch?.rgb/val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch ?: palette?.swatches?.maxByOrNull { it.population }\
                    val dominantColor = swatch?.rgb/g' tmp_vm.kt
cat tmp_vm.kt > app/src/main/java/com/serkantkn/zunelauncher/ui/screens/settings/SettingsViewModel.kt
