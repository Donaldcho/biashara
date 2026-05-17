package com.biasharaai.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional Hugging Face access token for gated model downloads (e.g. FunctionGemma).
 *
 * Create a read token at huggingface.co/settings/tokens after accepting the model license.
 */
@Singleton
class HuggingFaceTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun hasToken(): Boolean = getToken() != null

    fun setToken(token: String?) {
        val trimmed = token?.trim().orEmpty()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_TOKEN) else putString(KEY_TOKEN, trimmed)
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "huggingface_auth"
        private const val KEY_TOKEN = "access_token"
    }
}
