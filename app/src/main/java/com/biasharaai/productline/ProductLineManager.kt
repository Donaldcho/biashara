package com.biasharaai.productline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gates Pro-only ledger events (services, vouchers, warranty) until Pro SKU is wired.
 * Default install is **Shop** (product-only).
 */
@Singleton
class ProductLineManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isProEnabled(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO_ENABLED, false)

    fun setProEnabledForTests(enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRO_ENABLED, enabled)
            .apply()
    }

    companion object {
        private const val PREFS = "biashara_product_line"
        private const val KEY_PRO_ENABLED = "pro_enabled"
    }
}
