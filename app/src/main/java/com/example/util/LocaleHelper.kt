package com.example.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.example.data.PreferencesManager
import com.example.findActivity
import java.util.Locale

object LocaleHelper {
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_GERMAN = "de"
    const val LANGUAGE_UKRAINIAN = "uk"

    val supportedLanguages: List<String> = listOf(
        LANGUAGE_SYSTEM,
        LANGUAGE_ENGLISH,
        LANGUAGE_GERMAN,
        LANGUAGE_UKRAINIAN
    )

    fun normalizeLanguageTag(tag: String?): String {
        val cleaned = tag?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            cleaned == LANGUAGE_ENGLISH || cleaned.startsWith("en-") || cleaned.startsWith("en_") -> LANGUAGE_ENGLISH
            cleaned == LANGUAGE_GERMAN || cleaned.startsWith("de-") || cleaned.startsWith("de_") -> LANGUAGE_GERMAN
            cleaned == LANGUAGE_UKRAINIAN || cleaned.startsWith("uk-") || cleaned.startsWith("uk_") -> LANGUAGE_UKRAINIAN
            else -> LANGUAGE_SYSTEM
        }
    }

    fun getSystemDefaultLocale(): Locale {
        return try {
            val locales = Resources.getSystem().configuration.locales
            if (!locales.isEmpty) locales[0] else Locale.getDefault()
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }

    fun resolveLocale(languageTag: String): Locale {
        val normalized = normalizeLanguageTag(languageTag)
        return if (normalized == LANGUAGE_SYSTEM) {
            getSystemDefaultLocale()
        } else {
            Locale.forLanguageTag(normalized)
        }
    }

    fun wrapContext(base: Context): Context {
        val storedTag = try {
            normalizeLanguageTag(PreferencesManager(base).appLanguage)
        } catch (_: Exception) {
            LANGUAGE_SYSTEM
        }

        if (storedTag == LANGUAGE_SYSTEM) {
            val systemLocale = getSystemDefaultLocale()
            Locale.setDefault(systemLocale)
            return base
        }

        val locale = Locale.forLanguageTag(storedTag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun applyLanguage(context: Context, languageTag: String) {
        val normalized = normalizeLanguageTag(languageTag)
        val prefs = PreferencesManager(context)
        prefs.appLanguage = normalized

        val targetLocale = resolveLocale(normalized)
        Locale.setDefault(targetLocale)

        var handledByLocaleManager = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (localeManager != null) {
                    val targetList = if (normalized == LANGUAGE_SYSTEM) {
                        LocaleList.getEmptyLocaleList()
                    } else {
                        LocaleList.forLanguageTags(normalized)
                    }
                    val previousList = localeManager.applicationLocales
                    localeManager.applicationLocales = targetList
                    handledByLocaleManager = previousList != targetList
                }
            } catch (_: Exception) {
                handledByLocaleManager = false
            }
        }

        if (!handledByLocaleManager) {
            context.findActivity()?.recreate()
        }
    }
}
