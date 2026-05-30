package com.bikebike.app.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.LocaleList
import com.bikebike.app.data.AppSettings
import java.util.Locale

/**
 * Helper to apply locale changes at runtime.
 */
object LocaleHelper {

    fun applyLanguage(activity: Activity, langCode: String) {
        val locale = when (langCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault() // system
        }

        Locale.setDefault(locale)
        val config = activity.resources.configuration
        config.setLocale(locale)
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        // Save preference
        val ctx = activity.applicationContext
        AppSettings(ctx).language = AppSettings.Language.fromCode(langCode)

        // Recreate activity to apply
        activity.recreate()
    }

    fun getLocaleContext(context: Context): Context {
        val settings = AppSettings(context)
        val langCode = settings.language.code
        val locale = when (langCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> return context
        }

        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
