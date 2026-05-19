package com.biasharaai.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.LedgerRepository
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.service.ServiceReceiptCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServiceReceiptScanUiState {
    data object Loading : ServiceReceiptScanUiState
    data class Invalid(val message: String) : ServiceReceiptScanUiState
    data class Verified(
        val businessName: String,
        val transactionId: Long?,
        val serviceName: String,
        val deliveredAt: Long?,
        val warrantyStatus: ServiceReceiptCodec.WarrantyStatus,
        val deliveryId: Long,
    ) : ServiceReceiptScanUiState
}

@HiltViewModel
class ServiceReceiptScanViewModel @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val serviceItemDao: ServiceItemDao,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ServiceReceiptScanUiState>(ServiceReceiptScanUiState.Loading)
    val uiState: StateFlow<ServiceReceiptScanUiState> = _uiState.asStateFlow()

    private var verifiedDeliveryId: Long? = null
    private var verifiedServiceName: String = ""

    fun verify(rawToken: String) {
        viewModelScope.launch {
            val signingKey = appSettingsDao.getSettingsSync()?.voucherSigningKey
            if (signingKey.isNullOrBlank()) {
                _uiState.value = ServiceReceiptScanUiState.Invalid("Signing key not configured")
                return@launch
            }
            when (val result = ServiceReceiptCodec.decode(rawToken.trim(), signingKey)) {
                is ServiceReceiptCodec.DecodeResult.MalformedToken ->
                    _uiState.value = ServiceReceiptScanUiState.Invalid("Unrecognised token format")
                is ServiceReceiptCodec.DecodeResult.InvalidSignature ->
                    _uiState.value = ServiceReceiptScanUiState.Invalid("Signature invalid")
                is ServiceReceiptCodec.DecodeResult.Valid -> {
                    val delivery = serviceDeliveryDao.getById(result.payload.deliveryId)
                    val service = serviceItemDao.getById(result.payload.serviceItemId)
                    val settings = appSettingsDao.getSettingsSync()
                    verifiedDeliveryId = result.payload.deliveryId
                    verifiedServiceName = service?.name ?: "Service"
                    _uiState.value = ServiceReceiptScanUiState.Verified(
                        businessName = settings?.businessName ?: "My Business",
                        transactionId = result.payload.transactionId,
                        serviceName = verifiedServiceName,
                        deliveredAt = delivery?.deliveredAt,
                        warrantyStatus = ServiceReceiptCodec.warrantyStatus(result.payload.warrantyExpiresAt),
                        deliveryId = result.payload.deliveryId,
                    )
                }
            }
        }
    }

    suspend fun recordWarrantyClaim(): WarrantyClaimResult {
        val deliveryId = verifiedDeliveryId ?: return WarrantyClaimResult.Failed
        return try {
            ledgerRepository.recordWarrantyClaim(
                serviceDeliveryId = deliveryId,
                serviceName = verifiedServiceName,
                labourCost = 0.0,
            )
            WarrantyClaimResult.Success
        } catch (_: Exception) {
            WarrantyClaimResult.Failed
        }
    }

    enum class WarrantyClaimResult { Success, Failed }
}
