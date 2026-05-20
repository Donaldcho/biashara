package com.biasharaai.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServicePriceMode
import com.biasharaai.enterprise.EnterpriseCatalogRepository
import com.biasharaai.enterprise.EnterprisePermissionRepository
import com.biasharaai.enterprise.EnterpriseRolePermissions
import com.biasharaai.service.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditServiceViewModel @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val enterpriseCatalogRepository: EnterpriseCatalogRepository,
    private val enterprisePermissionRepository: EnterprisePermissionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serviceId: Long = savedStateHandle.get<Long>(AddEditServiceFragment.ARG_SERVICE_ID) ?: 0L

    val isNewService: Boolean get() = serviceId == 0L

    private val _service = MutableStateFlow<ServiceItem?>(null)
    val service: StateFlow<ServiceItem?> = _service.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        if (serviceId > 0L) {
            viewModelScope.launch {
                _service.value = serviceRepository.getService(serviceId)
            }
        }
    }

    fun save(
        name: String,
        price: Double,
        durationMinutes: Int,
        warrantyDays: Int,
    ) {
        viewModelScope.launch {
            try {
                val existing = _service.value
                val cleanName = name.trim()
                if (requiresCatalogPermission(existing, cleanName, durationMinutes, warrantyDays)) {
                    val permissionCheck = enterprisePermissionRepository.requirePermission(
                        permission = EnterpriseRolePermissions.PERMISSION_MANAGE_CATALOG,
                        action = "SERVICE_CATALOG_SAVE",
                        entityType = "SERVICE_ITEM",
                        entityId = serviceId.takeIf { it > 0L }?.toString(),
                        summary = "Service catalog save blocked for $cleanName",
                        metadata = "durationMinutes=$durationMinutes; warrantyDays=$warrantyDays",
                    )
                    if (!permissionCheck.allowed) {
                        emitPermissionDenied(permissionCheck)
                        return@launch
                    }
                }
                if (requiresPricePermission(existing, price)) {
                    val permissionCheck = enterprisePermissionRepository.requirePermission(
                        permission = EnterpriseRolePermissions.PERMISSION_CHANGE_PRICES,
                        action = "SERVICE_PRICE_SAVE",
                        entityType = "SERVICE_ITEM",
                        entityId = serviceId.takeIf { it > 0L }?.toString(),
                        summary = "Service price save blocked for $cleanName",
                        metadata = "newPrice=$price",
                    )
                    if (!permissionCheck.allowed) {
                        emitPermissionDenied(permissionCheck)
                        return@launch
                    }
                }

                val id = serviceRepository.upsertService(
                    id = serviceId,
                    name = cleanName,
                    description = null,
                    basePrice = price,
                    priceMode = ServicePriceMode.FIXED,
                    durationMinutes = durationMinutes,
                    category = null,
                    warrantyDays = warrantyDays,
                )
                val saved = serviceRepository.getService(id)
                if (saved == null) {
                    _events.emit(Event.Error("Service not found after save"))
                    return@launch
                }
                val enterpriseSaved = enterpriseCatalogRepository.onServiceSaved(
                    service = saved,
                    changeType = if (isNewService) "CREATE" else "UPDATE",
                )
                _service.value = enterpriseSaved
                _events.emit(
                    Event.Saved(
                        catalogueToken = enterpriseSaved.catalogueToken,
                        offerPrint = isNewService,
                    ),
                )
            } catch (e: Exception) {
                _events.emit(Event.Error(e.message ?: "Save failed"))
            }
        }
    }

    private suspend fun emitPermissionDenied(check: com.biasharaai.enterprise.EnterprisePermissionCheck) {
        val operator = check.operator
        _events.emit(
            Event.PermissionDenied(
                operatorName = operator?.name.orEmpty(),
                operatorRole = operator?.role.orEmpty(),
            ),
        )
    }

    private fun requiresCatalogPermission(
        existing: ServiceItem?,
        newName: String,
        newDurationMinutes: Int,
        newWarrantyDays: Int,
    ): Boolean {
        if (existing == null) return true
        return existing.name != newName ||
            existing.durationMinutes != newDurationMinutes.coerceAtLeast(0) ||
            existing.warrantyDays != newWarrantyDays.coerceAtLeast(0)
    }

    private fun requiresPricePermission(existing: ServiceItem?, newPrice: Double): Boolean =
        existing == null || existing.basePrice != newPrice

    sealed class Event {
        data class Saved(val catalogueToken: String, val offerPrint: Boolean) : Event()
        data class PermissionDenied(val operatorName: String, val operatorRole: String) : Event()
        data class Error(val message: String) : Event()
    }
}
