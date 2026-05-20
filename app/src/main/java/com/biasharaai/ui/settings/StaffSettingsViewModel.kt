package com.biasharaai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.data.local.db.StaffMemberDao
import com.biasharaai.enterprise.EnterpriseAuditRepository
import com.biasharaai.enterprise.EnterpriseOperatorStore
import com.biasharaai.enterprise.EnterprisePinHasher
import com.biasharaai.enterprise.EnterpriseRolePermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StaffSettingsViewModel @Inject constructor(
    private val staffMemberDao: StaffMemberDao,
    private val enterpriseAuditRepository: EnterpriseAuditRepository,
    private val enterpriseOperatorStore: EnterpriseOperatorStore,
) : ViewModel() {

    val staff = staffMemberDao.getActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _selectedOperator = MutableStateFlow<StaffMember?>(null)
    val selectedOperator: StateFlow<StaffMember?> = _selectedOperator.asStateFlow()

    init {
        refreshSelectedOperator()
    }

    fun addStaff(name: String, role: String) {
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isBlank()) {
                _events.emit(Event.InvalidName)
                return@launch
            }
            val normalizedRole = EnterpriseRolePermissions.normalizeRole(role)
            val id = staffMemberDao.insert(
                StaffMember(
                    name = cleanName,
                    role = normalizedRole,
                ),
            )
            enterpriseAuditRepository.record(
                action = "STAFF_CREATED",
                entityType = "STAFF_MEMBER",
                entityId = id.toString(),
                summary = "Staff member added: $cleanName",
                metadata = "role=$normalizedRole",
            )
            _events.emit(Event.StaffSaved)
        }
    }

    fun setStaffPin(member: StaffMember, pin: String, selectAfterSave: Boolean) {
        viewModelScope.launch {
            if (!EnterprisePinHasher.isValidPin(pin)) {
                _events.emit(Event.InvalidPinFormat)
                return@launch
            }
            val fresh = staffMemberDao.getById(member.id)?.takeIf { it.isActive } ?: return@launch
            val salt = EnterprisePinHasher.newSalt()
            val hash = withContext(Dispatchers.Default) { EnterprisePinHasher.hash(pin, salt) }
            val updated = fresh.copy(
                pinHash = hash,
                pinSalt = salt,
                pinUpdatedAt = System.currentTimeMillis(),
            )
            staffMemberDao.update(updated)
            enterpriseAuditRepository.record(
                action = "STAFF_PIN_SET",
                entityType = "STAFF_MEMBER",
                entityId = fresh.id.toString(),
                summary = "Staff PIN updated for ${fresh.name}",
                metadata = "role=${fresh.role}",
            )
            _events.emit(Event.PinSaved)
            if (selectAfterSave) {
                selectDeviceOperatorInternal(updated)
            }
        }
    }

    fun selectDeviceOperator(member: StaffMember, pin: String) {
        viewModelScope.launch {
            val fresh = staffMemberDao.getById(member.id)?.takeIf { it.isActive } ?: return@launch
            if (fresh.pinHash.isNullOrBlank() || fresh.pinSalt.isNullOrBlank()) {
                _events.emit(Event.PinRequired)
                return@launch
            }
            val valid = withContext(Dispatchers.Default) {
                EnterprisePinHasher.verify(pin, fresh.pinSalt, fresh.pinHash)
            }
            if (!valid) {
                enterpriseAuditRepository.record(
                    action = "OPERATOR_PIN_FAILED",
                    entityType = "STAFF_MEMBER",
                    entityId = fresh.id.toString(),
                    summary = "Invalid operator PIN for ${fresh.name}",
                    metadata = "role=${fresh.role}",
                    actorStaffId = fresh.id,
                    actorRole = fresh.role,
                )
                _events.emit(Event.InvalidPin)
                return@launch
            }
            selectDeviceOperatorInternal(fresh)
        }
    }

    fun clearDeviceOperator() {
        viewModelScope.launch {
            val previous = _selectedOperator.value
            enterpriseOperatorStore.clear()
            _selectedOperator.value = null
            enterpriseAuditRepository.record(
                action = "DEVICE_OPERATOR_CLEARED",
                entityType = "STAFF_MEMBER",
                entityId = previous?.id?.toString(),
                summary = "Device operator cleared",
                metadata = previous?.let { "previous=${it.name}; role=${it.role}" },
                actorStaffId = previous?.id,
                actorRole = previous?.role,
            )
            _events.emit(Event.OperatorCleared)
        }
    }

    private suspend fun selectDeviceOperatorInternal(member: StaffMember) {
        enterpriseOperatorStore.selectStaff(member.id)
        _selectedOperator.value = member
        enterpriseAuditRepository.record(
            action = "DEVICE_OPERATOR_SELECTED",
            entityType = "STAFF_MEMBER",
            entityId = member.id.toString(),
            summary = "Device operator selected: ${member.name}",
            metadata = "role=${member.role}; pinVerified=${member.pinHash?.isNotBlank() == true}",
            actorStaffId = member.id,
            actorRole = member.role,
        )
        _events.emit(Event.OperatorSelected)
    }

    fun updateRole(member: StaffMember, role: String) {
        viewModelScope.launch {
            val normalizedRole = EnterpriseRolePermissions.normalizeRole(role)
            if (member.role == normalizedRole) return@launch
            staffMemberDao.update(member.copy(role = normalizedRole))
            enterpriseAuditRepository.record(
                action = "STAFF_ROLE_CHANGED",
                entityType = "STAFF_MEMBER",
                entityId = member.id.toString(),
                summary = "Staff role changed for ${member.name}",
                metadata = "from=${member.role}; to=$normalizedRole",
            )
            _events.emit(Event.RoleUpdated)
        }
    }

    fun deactivate(member: StaffMember) {
        viewModelScope.launch {
            staffMemberDao.update(member.copy(isActive = false))
            if (_selectedOperator.value?.id == member.id) {
                enterpriseOperatorStore.clear()
                _selectedOperator.value = null
            }
            enterpriseAuditRepository.record(
                action = "STAFF_DEACTIVATED",
                entityType = "STAFF_MEMBER",
                entityId = member.id.toString(),
                summary = "Staff member deactivated: ${member.name}",
                metadata = "role=${member.role}",
            )
            _events.emit(Event.StaffDeactivated)
        }
    }

    private fun refreshSelectedOperator() {
        viewModelScope.launch {
            val selectedId = enterpriseOperatorStore.selectedStaffId()
            val member = selectedId?.let { staffMemberDao.getById(it) }?.takeIf { it.isActive }
            if (selectedId != null && member == null) {
                enterpriseOperatorStore.clear()
            }
            _selectedOperator.value = member
        }
    }

    sealed class Event {
        data object StaffSaved : Event()
        data object RoleUpdated : Event()
        data object StaffDeactivated : Event()
        data object OperatorSelected : Event()
        data object OperatorCleared : Event()
        data object PinSaved : Event()
        data object PinRequired : Event()
        data object InvalidPin : Event()
        data object InvalidPinFormat : Event()
        data object InvalidName : Event()
    }
}
