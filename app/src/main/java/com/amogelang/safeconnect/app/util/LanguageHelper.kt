package com.amogelang.safeconnect.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageHelper {
    fun getLanguageCode(displayName: String): String = when (displayName) {
        "isiZulu" -> "zu"
        "Afrikaans" -> "af"
        "Sesotho" -> "st"
        else -> "en"
    }

    fun getDisplayName(code: String): String = when (code) {
        "zu" -> "isiZulu"
        "af" -> "Afrikaans"
        "st" -> "Sesotho"
        else -> "English"
    }

    fun applyLocale(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}
