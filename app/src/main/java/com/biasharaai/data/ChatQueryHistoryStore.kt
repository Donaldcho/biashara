package com.biasharaai.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recent chat questions for empty-state chips (Google AI Edge Gallery–style input history).
 * Persists across sessions; not cleared on New chat (transcript/memory are separate).
 */
@Singleton
class ChatQueryHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<ArrayList<String>>() {}.type

    fun getRecent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            gson.fromJson<ArrayList<String>>(raw, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordQuery(text: String) {
        val t = text.trim()
        if (t.length < MIN_QUERY_LEN) return
        val capped = t.take(MAX_QUERY_CHARS)
        val cur = getRecent().toMutableList()
        cur.removeAll { it.equals(capped, ignoreCase = true) }
        cur.add(0, capped)
        while (cur.size > MAX_ENTRIES) cur.removeAt(cur.lastIndex)
        prefs.edit().putString(KEY_RECENT, gson.toJson(cur)).apply()
    }

    companion object {
        private const val PREFS_NAME = "chat_query_history"
        private const val KEY_RECENT = "recent"
        private const val MAX_ENTRIES = 20
        private const val MIN_QUERY_LEN = 3
        private const val MAX_QUERY_CHARS = 400
    }
}
