package com.biasharaai.enterprise

import com.biasharaai.data.local.db.StaffMember

object EnterpriseRolePermissions {

    const val PERMISSION_MANAGE_STAFF = "manage_staff"
    const val PERMISSION_VIEW_PROFIT = "view_profit"
    const val PERMISSION_EXPORT_DATA = "export_data"
    const val PERMISSION_EDIT_LEDGER = "edit_ledger"
    const val PERMISSION_MANAGE_CATALOG = "manage_catalog"
    const val PERMISSION_CHANGE_PRICES = "change_prices"
    const val PERMISSION_SELL = "sell"
    const val PERMISSION_VIEW_INVENTORY = "view_inventory"

    val supportedRoles: List<String> = listOf(
        StaffMember.ROLE_OWNER,
        StaffMember.ROLE_MANAGER,
        StaffMember.ROLE_STAFF,
    )

    fun permissionsFor(role: String): List<String> = when (normalizeRole(role)) {
        StaffMember.ROLE_OWNER -> listOf(
            PERMISSION_MANAGE_STAFF,
            PERMISSION_VIEW_PROFIT,
            PERMISSION_EXPORT_DATA,
            PERMISSION_EDIT_LEDGER,
            PERMISSION_MANAGE_CATALOG,
            PERMISSION_CHANGE_PRICES,
            PERMISSION_SELL,
            PERMISSION_VIEW_INVENTORY,
        )
        StaffMember.ROLE_MANAGER -> listOf(
            PERMISSION_VIEW_PROFIT,
            PERMISSION_EDIT_LEDGER,
            PERMISSION_MANAGE_CATALOG,
            PERMISSION_CHANGE_PRICES,
            PERMISSION_SELL,
            PERMISSION_VIEW_INVENTORY,
        )
        else -> listOf(
            PERMISSION_SELL,
            PERMISSION_VIEW_INVENTORY,
        )
    }

    fun can(role: String, permission: String): Boolean =
        permission in permissionsFor(role)

    fun normalizeRole(role: String): String =
        supportedRoles.firstOrNull { it.equals(role.trim(), ignoreCase = true) }
            ?: StaffMember.ROLE_STAFF
}
