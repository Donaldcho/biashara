package com.biasharaai.enterprise

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnterpriseOperatorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedStaffId(): Long? =
        prefs.getLong(KEY_SELECTED_STAFF_ID, NO_STAFF_ID).takeIf { it > 0L }

    fun selectStaff(staffId: Long) {
        prefs.edit().putLong(KEY_SELECTED_STAFF_ID, staffId).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SELECTED_STAFF_ID).apply()
    }

    companion object {
        private const val PREFS = "enterprise_operator"
        private const val KEY_SELECTED_STAFF_ID = "selected_staff_id"
        private const val NO_STAFF_ID = 0L
    }
}
