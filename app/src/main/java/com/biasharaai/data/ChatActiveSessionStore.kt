package com.biasharaai.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists which chat session is active (separate from Room for O(1) read on cold start). */
@Singleton
class ChatActiveSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getActiveSessionId(): Long = prefs.getLong(KEY_ACTIVE, NO_SESSION)

    fun setActiveSessionId(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE, id).apply()
    }

    companion object {
        private const val PREFS = "chat_active_session"
        private const val KEY_ACTIVE = "active_session_id"
        const val NO_SESSION = -1L
    }
}
