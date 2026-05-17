package com.biasharaai.net

import android.content.Context
import android.net.ConnectivityManager

/**
 * Lightweight default-network check (Prompt U10 — airplane mode / offline UX).
 *
 * When [ConnectivityManager] is unavailable (e.g. some JVM tests), returns **true** so callers
 * are not blocked.
 */
object NetworkStatus {

    fun hasDefaultNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return cm.activeNetwork != null
    }
}
