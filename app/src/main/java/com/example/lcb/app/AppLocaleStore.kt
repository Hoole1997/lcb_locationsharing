package com.example.lcb.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocaleStore {
    private const val PREFS_NAME = "app_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    const val DEFAULT_LANGUAGE_TAG = "en"

    fun currentLanguageTag(context: Context): String {
        return preferences(context).getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
            ?: DEFAULT_LANGUAGE_TAG
    }

    fun saveLanguageTag(context: Context, languageTag: String) {
        preferences(context).edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
    }

    fun applyStoredLanguage(context: Context) {
        applyLanguageTag(currentLanguageTag(context))
    }

    fun applyLanguageTag(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(languageTag)
        )
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
