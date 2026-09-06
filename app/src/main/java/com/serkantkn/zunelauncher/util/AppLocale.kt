package com.serkantkn.zunelauncher.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import java.util.Locale

/** Languages offered in Ayarlar > sistem > dil. [tag] is null for "follow the system". */
enum class AppLanguage(val tag: String?, @StringRes val titleRes: Int) {
    SYSTEM(null, R.string.language_system),
    TURKISH("tr", R.string.language_turkish),
    ENGLISH("en", R.string.language_english);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && tag != null && tag.startsWith(it.tag) } ?: SYSTEM
    }
}

/**
 * Per-app language without AppCompat. On Android 13+ the platform [LocaleManager] stores the
 * choice and applies it to every context of the process. On older versions the choice lives in a
 * small SharedPreferences file and is applied by wrapping activity contexts in
 * [wrap] (see the activities' attachBaseContext) and application contexts in [localized].
 */
object AppLocale {

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    fun current(context: Context): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeIf { !it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
        } else {
            prefs(context).getString(KEY_TAG, null)
        }
        return AppLanguage.fromTag(tag)
    }

    /**
     * Stores and applies [language]. Returns true when the caller must recreate its activity
     * itself (pre-Android 13); on Android 13+ the system recreates activities on its own.
     */
    fun apply(context: Context, language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return false
            manager.applicationLocales = language.tag
                ?.let { LocaleList.forLanguageTags(it) }
                ?: LocaleList.getEmptyLocaleList()
            return false
        }
        prefs(context).edit().putString(KEY_TAG, language.tag).apply()
        return true
    }

    /** Wraps [base] with the chosen locale on pre-Android 13 devices; a no-op otherwise. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAG, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Application/service contexts do not go through attachBaseContext; use this for their strings. */
fun Context.localized(): Context = AppLocale.wrap(this)

/** getString on a context that honours the in-app language choice on every Android version. */
fun Context.localizedString(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) localized().getString(id) else localized().getString(id, *args)
