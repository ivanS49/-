package com.vibe.browser

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageHelper {
    private const val PREF_NAME = "app_lang_pref"
    private const val KEY_LANG = "selected_language"

    val supportedLanguages = listOf(
        Pair("Русский", "ru"),
        Pair("English", "en"),
        Pair("Français", "fr"),
        Pair("Deutsch", "de"),
        Pair("中文", "zh"),
        Pair("العربية", "ar"),
        Pair("Español", "es")
    )

    fun getSavedLanguage(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lang = prefs.getString(KEY_LANG, null)
            if (!lang.isNullOrEmpty()) {
                lang
            } else {
                "ru"
            }
        } catch (e: Exception) {
            "ru"
        }
    }

    fun saveLanguage(context: Context, langCode: String) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LANG, langCode).commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyLanguage(context: Context, langCode: String): Context {
        val locale = when (langCode.lowercase()) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ar" -> Locale("ar")
            "ru" -> Locale("ru")
            "en" -> Locale("en")
            "de" -> Locale("de")
            "fr" -> Locale("fr")
            "es" -> Locale("es")
            else -> Locale(langCode)
        }
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val localeList = android.os.LocaleList(locale)
            android.os.LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }

        val configContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.createConfigurationContext(config)
        } else {
            context
        }
        try {
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
        } catch (e: Exception) {}

        return configContext
    }
}
