package com.biasharaai.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServiceVoucher
import com.biasharaai.data.local.db.ServiceVoucherDao
import com.biasharaai.service.ServiceTokenCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoucherReceiptUiState(
    val businessName: String = "",
    val serviceName: String = "",
    val totalUses: Int = 0,
    val expiresAt: Long? = null,
    val customerName: String? = null,
    val qrToken: String = "",
    val voucherId: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class VoucherReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serviceVoucherDao: ServiceVoucherDao,
    private val serviceItemDao: ServiceItemDao,
    private val customerDao: CustomerDao,
    private val appSettingsDao: AppSettingsDao,
) : ViewModel() {

    private val voucherId: String = checkNotNull(savedStateHandle.get<String>(ARG_VOUCHER_ID))

    private val _uiState = MutableStateFlow(VoucherReceiptUiState())
    val uiState: StateFlow<VoucherReceiptUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val voucher = serviceVoucherDao.getByVoucherId(voucherId) ?: return@launch
            val service = serviceItemDao.getById(voucher.serviceItemId) ?: return@launch
            val settings = appSettingsDao.getSettingsSync()
            val customerName = voucher.customerId?.let { customerDao.getCustomerById(it)?.name }
            _uiState.value = VoucherReceiptUiState(
                businessName = settings?.businessName ?: "My Business",
                serviceName = service.name,
                totalUses = voucher.totalUses,
                expiresAt = voucher.expiresAt,
                customerName = customerName,
                qrToken = ServiceTokenCodec.voucherToken(voucher.voucherId),
                voucherId = voucher.voucherId,
                loading = false,
            )
        }
    }

    suspend fun voucherForPrint(): ServiceVoucher? = serviceVoucherDao.getByVoucherId(voucherId)

    companion object {
        const val ARG_VOUCHER_ID = "voucher_id"
    }
}
