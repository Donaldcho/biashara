package com.biasharaai.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServicePriceMode
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
                val id = serviceRepository.upsertService(
                    id = serviceId,
                    name = name,
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
                _service.value = saved
                _events.emit(
                    Event.Saved(
                        catalogueToken = saved.catalogueToken,
                        offerPrint = isNewService,
                    ),
                )
            } catch (e: Exception) {
                _events.emit(Event.Error(e.message ?: "Save failed"))
            }
        }
    }

    sealed class Event {
        data class Saved(val catalogueToken: String, val offerPrint: Boolean) : Event()
        data class Error(val message: String) : Event()
    }
}
