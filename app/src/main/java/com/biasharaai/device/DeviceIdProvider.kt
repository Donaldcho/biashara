package com.biasharaai.device

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable per-install device id for ledger audit fields ([com.biasharaai.data.local.db.LedgerEntry.deviceId]).
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): String {
        prefs.getString(KEY_ID, null)?.let { return it }
        val generated = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, generated).apply()
        return generated
    }

    companion object {
        private const val PREFS = "biashara_device_id"
        private const val KEY_ID = "device_id"
    }
}
