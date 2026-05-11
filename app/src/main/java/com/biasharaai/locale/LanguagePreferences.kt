package com.biasharaai.locale

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguagePreferences {

    private const val PREFS_NAME = "biashara_prefs"
    private const val KEY_LOCALE_TAG = "locale_tag"

    fun hasPersistedLocale(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_LOCALE_TAG)

    fun getPersistedLocaleTag(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE_TAG, null)

    fun persistLocale(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE_TAG, languageTag)
            .apply()
    }

    /**
     * Call from [android.app.Activity.onCreate] before [android.app.Activity.super.onCreate]
     * so the first [android.view.LayoutInflater] pass uses the correct locale.
     */
    fun applyPersistedLocales(context: Context) {
        val tag = getPersistedLocaleTag(context) ?: return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
