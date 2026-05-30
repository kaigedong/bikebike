package com.bikebike.app.data

import android.app.Activity
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * Helper to apply locale changes at runtime.
 *
 * Key design: split into two functions:
 * - applyLocale(): silent, only sets config. Called in onCreate every time. NO recreate.
 * - switchLanguage(): called by user action. Sets config + saves pref + recreates.
 */
object LocaleHelper {

    fun applyLocale(context: Context): Context {
        val settings = AppSettings(context.applicationContext)
        val langCode = settings.language.code
        val locale = when (langCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> return context // system default, no change needed
        }

        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)
        return context.createConfigurationContext(config)
    }

    /**
     * Called from Settings when user picks a new language.
     * Saves preference and recreates activity.
     */
    fun switchLanguage(activity: Activity, langCode: String) {
        // Save preference first
        val ctx = activity.applicationContext
        AppSettings(ctx).language = AppSettings.Language.fromCode(langCode)

        // Apply and recreate
        val locale = when (langCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }

        Locale.setDefault(locale)
        val config = activity.resources.configuration
        config.setLocale(locale)
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        activity.recreate()
    }
}
