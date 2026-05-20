package com.biasharaai.enterprise

import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.data.local.db.StaffMemberDao
import com.biasharaai.productline.ProductLineManager
import javax.inject.Inject
import javax.inject.Singleton

data class EnterprisePermissionCheck(
    val allowed: Boolean,
    val operator: StaffMember? = null,
)

@Singleton
class EnterprisePermissionRepository @Inject constructor(
    private val productLineManager: ProductLineManager,
    private val staffMemberDao: StaffMemberDao,
    private val enterpriseOperatorStore: EnterpriseOperatorStore,
    private val enterpriseAuditRepository: EnterpriseAuditRepository,
) {
    suspend fun requirePermission(
        permission: String,
        action: String,
        entityType: String,
        entityId: String? = null,
        summary: String,
        metadata: String? = null,
    ): EnterprisePermissionCheck {
        if (!productLineManager.isEnterprisePro()) {
            return EnterprisePermissionCheck(allowed = true)
        }
        val operator = currentOperator()
            ?: return EnterprisePermissionCheck(allowed = true)
        if (EnterpriseRolePermissions.can(operator.role, permission)) {
            return EnterprisePermissionCheck(allowed = true, operator = operator)
        }
        enterpriseAuditRepository.record(
            action = "PERMISSION_DENIED",
            entityType = entityType,
            entityId = entityId ?: permission,
            summary = summary,
            metadata = buildString {
                append("action=")
                append(action)
                append("; permission=")
                append(permission)
                if (!metadata.isNullOrBlank()) {
                    append("; ")
                    append(metadata)
                }
            },
            actorStaffId = operator.id,
            actorRole = operator.role,
        )
        return EnterprisePermissionCheck(allowed = false, operator = operator)
    }

    private suspend fun currentOperator(): StaffMember? {
        val id = enterpriseOperatorStore.selectedStaffId() ?: return null
        val member = staffMemberDao.getById(id)?.takeIf { it.isActive }
        if (member == null) {
            enterpriseOperatorStore.clear()
        }
        return member
    }
}
