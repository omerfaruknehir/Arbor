package app.xylune.chat.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    TURKISH("tr"),
}

internal const val XYLUNE_APP_SETTINGS_PREFERENCES = "xylune_app_settings"
internal const val KEY_APP_LANGUAGE = "app_language"

internal fun storedAppLanguage(context: Context): AppLanguage {
    val raw = context.getSharedPreferences(XYLUNE_APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
        .getString(KEY_APP_LANGUAGE, null)
    return runCatching { raw?.let { AppLanguage.valueOf(it) } }.getOrNull() ?: AppLanguage.SYSTEM
}

internal fun Context.withStoredXyluneLanguage(): Context {
    if (Build.VERSION.SDK_INT >= 33) return this
    val language = storedAppLanguage(this)
    val tag = language.languageTag ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(tag))
    return createConfigurationContext(config)
}
