package com.openring.ui.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"
    private const val LANGUAGE_EN = "en"
    private const val LANGUAGE_ZH_TW = "zh-TW"

    fun getSelectedLanguage(context: Context): String {
        val prefs = prefs(context)
        val saved = prefs.getString(KEY_SELECTED_LANGUAGE, null)
        if (saved != null) return saved

        val initial = inferInitialLanguageTag(Locale.getDefault())
        prefs.edit().putString(KEY_SELECTED_LANGUAGE, initial).apply()
        return initial
    }

    fun setSelectedLanguage(context: Context, languageTag: String) {
        val normalized = when (languageTag) {
            LANGUAGE_ZH_TW -> LANGUAGE_ZH_TW
            else -> LANGUAGE_EN
        }
        prefs(context).edit().putString(KEY_SELECTED_LANGUAGE, normalized).apply()
    }

    fun wrapContext(base: Context): Context {
        val tag = getSelectedLanguage(base)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun isEnglishSelected(context: Context): Boolean = getSelectedLanguage(context) == LANGUAGE_EN

    private fun inferInitialLanguageTag(systemLocale: Locale): String {
        return if (systemLocale.language.lowercase(Locale.ROOT) == "zh") LANGUAGE_ZH_TW else LANGUAGE_EN
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
