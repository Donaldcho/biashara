package com.biasharaai.ai

import android.content.SharedPreferences

/**
 * Minimal in-memory [SharedPreferences] for JVM unit tests.
 * Only APIs used by [InferenceSettingsStore] are implemented.
 */
class MemSharedPreferences : SharedPreferences {

    private val map = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val staged = linkedMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            staged[key!!] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            staged[key!!] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            staged[key!!] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) {
                map.clear()
                staged.clear()
                clearAll = false
                return
            }
            staged.forEach { (k, v) ->
                if (v == null) map.remove(k) else map[k] = v
            }
            staged.clear()
        }
    }
}
