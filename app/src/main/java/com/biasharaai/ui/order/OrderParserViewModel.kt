package com.biasharaai.ui.order

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleRepository
import com.biasharaai.data.local.db.TransactionNoteTypes
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.payment.PaymentDraft
import com.biasharaai.pos.payment.PrimaryPaymentTab
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

data class OrderLineItem(
    val rowKey: String,
    val parsedName: String,
    val matchedProduct: Product?,
    val quantity: Int,
    val unit: String?,
    /** Set when user picks from search dialog (Prompt U8). */
    val manualProduct: Product? = null,
) {
    val resolvedProduct: Product? get() = manualProduct ?: matchedProduct
    val isMatched: Boolean get() = resolvedProduct != null
}

private data class ParsedOrderJsonLine(
    @SerializedName("productName") val productName: String = "",
    @SerializedName("quantity") val quantity: Number = 1,
    @SerializedName("unit") val unit: String? = null,
)

sealed interface OrderParserUiState {
    data object Loading : OrderParserUiState
    data class Ready(val lines: List<OrderLineItem>) : OrderParserUiState
    data class Error(val message: String) : OrderParserUiState
}

@HiltViewModel
class OrderParserViewModel @Inject constructor(
    private val gemmaService: GemmaService,
    private val productDao: ProductDao,
    private val saleRepository: SaleRepository,
    private val appSettingsDao: AppSettingsDao,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow<OrderParserUiState>(OrderParserUiState.Loading)
    val uiState: StateFlow<OrderParserUiState> = _uiState.asStateFlow()

    private val _orderLines = MutableStateFlow<List<OrderLineItem>>(emptyList())
    val orderLines: StateFlow<List<OrderLineItem>> = _orderLines.asStateFlow()

    private val _events = MutableSharedFlow<OrderParserEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<OrderParserEvent> = _events.asSharedFlow()

    fun startParse(sharedText: String) {
        viewModelScope.launch {
            _uiState.value = OrderParserUiState.Loading
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm?.activeNetwork == null) {
                _uiState.value = OrderParserUiState.Error("OFFLINE")
                return@launch
            }
            val trimmed = sharedText.trim()
            if (trimmed.isEmpty()) {
                _uiState.value = OrderParserUiState.Error("EMPTY")
                return@launch
            }
            if (trimmed.length > MAX_SHARED_TEXT_CHARS) {
                _uiState.value = OrderParserUiState.Error("TOO_LONG")
                return@launch
            }
            try {
                if (!gemmaService.isAvailable) {
                    _uiState.value = OrderParserUiState.Error("NO_MODEL")
                    return@launch
                }
                val raw = withContext(Dispatchers.IO) {
                    val prompt = """
Parse this order message from a customer into a JSON array.
Return ONLY valid JSON. No explanation.
Each object must have: productName (string), quantity (number), unit (string or null).
Message: $trimmed
                    """.trimIndent()
                    gemmaService.generateResponse(prompt)
                }
                val json = extractJsonArray(raw)
                val type = object : TypeToken<List<ParsedOrderJsonLine>>() {}.type
                val parsed: List<ParsedOrderJsonLine> = try {
                    gson.fromJson(json, type) ?: emptyList()
                } catch (_: JsonSyntaxException) {
                    emptyList()
                }
                if (parsed.isEmpty()) {
                    _uiState.value = OrderParserUiState.Error("PARSE_FAILED")
                    return@launch
                }
                val lines = withContext(Dispatchers.IO) {
                    parsed.mapIndexed { index, row ->
                        val name = row.productName.trim().ifEmpty { "Item ${index + 1}" }
                        val qty = row.quantity.toDouble().takeIf { it.isFinite() && it > 0 }?.roundToInt()?.coerceIn(1, 99_999) ?: 1
                        val fuzzy = if (name.isNotBlank()) {
                            productDao.findProductByNameFuzzy(name)
                        } else {
                            null
                        }
                        OrderLineItem(
                            rowKey = "${index}_${name.hashCode()}",
                            parsedName = name,
                            matchedProduct = fuzzy,
                            quantity = qty,
                            unit = row.unit?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    }
                }
                _orderLines.value = lines
                _uiState.value = OrderParserUiState.Ready(lines)
            } catch (e: Exception) {
                _uiState.value = OrderParserUiState.Error(e.message ?: "FAILED")
            }
        }
    }

    fun setManualProduct(rowKey: String, product: Product) {
        val updated = _orderLines.value.map { line ->
            if (line.rowKey == rowKey) line.copy(manualProduct = product) else line
        }
        _orderLines.value = updated
        val s = _uiState.value
        if (s is OrderParserUiState.Ready) {
            _uiState.value = OrderParserUiState.Ready(updated)
        }
    }

    suspend fun searchProducts(query: String): List<Product> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        productDao.searchProductsByNameOrBarcode(query.trim()).first()
    }

    fun recordSale() {
        viewModelScope.launch {
            val lines = _orderLines.value
            val matched = lines.filter { it.isMatched }
            val skipped = lines.size - matched.size
            if (skipped > 0) {
                _events.emit(
                    OrderParserEvent.Toast(
                        appContext.getString(R.string.order_parser_skipped_toast, skipped),
                    ),
                )
            }
            if (matched.isEmpty()) {
                _events.emit(OrderParserEvent.Toast(appContext.getString(R.string.order_parser_no_matched_lines)))
                return@launch
            }
            try {
                val cartItems = matched.map { line ->
                    val p = line.resolvedProduct!!
                    CartItem(product = p, quantity = line.quantity)
                }
                val settings = withContext(Dispatchers.IO) { appSettingsDao.getSettingsSync() }
                val taxRate = settings?.taxRate ?: 0.0
                val subtotal = cartItems.sumOf { it.lineTotal }
                val taxAmount = subtotal * (taxRate / 100.0)
                val grand = subtotal + taxAmount
                val draft = PaymentDraft(
                    grandTotal = grand,
                    splitMode = false,
                    primaryTab = PrimaryPaymentTab.CASH,
                    cashAmountTendered = grand,
                    cashChangeDue = 0.0,
                    mobileMoneyNetwork = null,
                    mobileMoneyRef = null,
                    creditCustomerId = null,
                    creditDueDateMillis = null,
                    creditNote = null,
                    splitLine1Method = null,
                    splitLine1Amount = null,
                    splitLine2Method = null,
                    splitLine2Amount = null,
                )
                val commit = withContext(Dispatchers.IO) {
                    saleRepository.commitPosSale(
                        lines = cartItems,
                        taxAmount = taxAmount,
                        grandTotal = grand,
                        taxRatePercent = taxRate,
                        draft = draft,
                        cartCustomerId = null,
                        transactionDescription = "WhatsApp / text order",
                        transactionNoteTypeOverride = TransactionNoteTypes.WHATSAPP_ORDER,
                    )
                }
                _events.emit(OrderParserEvent.SaleRecorded(commit.transactionId))
            } catch (e: Exception) {
                _events.emit(OrderParserEvent.Toast(e.message ?: appContext.getString(R.string.order_parser_record_failed)))
            }
        }
    }

    companion object {
        /** Cap shared SEND text before prompting on-device Gemma. */
        const val MAX_SHARED_TEXT_CHARS = 8_000

        fun extractJsonArray(raw: String): String {
            val t = raw.trim()
            val start = t.indexOf('[')
            val end = t.lastIndexOf(']')
            return if (start >= 0 && end > start) t.substring(start, end + 1) else t
        }
    }
}

sealed interface OrderParserEvent {
    data class Toast(val message: String) : OrderParserEvent
    data class SaleRecorded(val transactionId: Long) : OrderParserEvent
}
