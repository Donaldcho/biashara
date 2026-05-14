package com.biasharaai.ui.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleRepository
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.data.local.db.DebtRepository
import com.biasharaai.pos.payment.PaymentDraft
import com.biasharaai.pos.payment.PrimaryPaymentTab
import com.biasharaai.pos.payment.SplitLineMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

sealed interface SaleCommitResult {
    data object EmptyCart : SaleCommitResult
    data class Success(val transactionId: Long) : SaleCommitResult
    data class Failure(val message: String) : SaleCommitResult
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val cartManager: CartManager,
    @Suppress("unused") private val productDao: ProductDao,
    @Suppress("unused") private val transactionRepository: TransactionRepository,
    private val debtRepository: DebtRepository,
    private val saleRepository: SaleRepository,
    private val capabilityTier: CapabilityTier,
    private val gemmaService: GemmaService,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val grandTotal: StateFlow<Double> = cartRepository.grandTotal

    private val _primaryTab = MutableStateFlow(PrimaryPaymentTab.CASH)
    val primaryTab: StateFlow<PrimaryPaymentTab> = _primaryTab.asStateFlow()

    private val _splitMode = MutableStateFlow(false)
    val splitMode: StateFlow<Boolean> = _splitMode.asStateFlow()

    private val _amountTenderedText = MutableStateFlow("")
    val amountTenderedText: StateFlow<String> = _amountTenderedText.asStateFlow()

    private val _mobileMoneyNetwork = MutableStateFlow("MPESA")
    val mobileMoneyNetwork: StateFlow<String> = _mobileMoneyNetwork.asStateFlow()

    private val _mobileMoneyRef = MutableStateFlow("")
    val mobileMoneyRef: StateFlow<String> = _mobileMoneyRef.asStateFlow()

    private val _creditDueDateMillis = MutableStateFlow<Long?>(null)
    val creditDueDateMillis: StateFlow<Long?> = _creditDueDateMillis.asStateFlow()

    private val _splitLine1Method = MutableStateFlow(SplitLineMethod.CASH)
    val splitLine1Method: StateFlow<SplitLineMethod> = _splitLine1Method.asStateFlow()

    private val _splitLine2Method = MutableStateFlow(SplitLineMethod.MOBILE_MONEY)
    val splitLine2Method: StateFlow<SplitLineMethod> = _splitLine2Method.asStateFlow()

    private val _splitLine1AmountText = MutableStateFlow("")
    val splitLine1AmountText: StateFlow<String> = _splitLine1AmountText.asStateFlow()

    val selectedCustomer = cartRepository.selectedCustomer

    val creditOutstanding: StateFlow<Double> = cartRepository.selectedCustomer
        .flatMapLatest { c ->
            if (c == null) flowOf(0.0)
            else debtRepository.observeTotalOutstandingForCustomer(c.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val tenderedAmountParsed: StateFlow<Double> = _amountTenderedText
        .map { parseAmount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val changeDue: StateFlow<Double?> = combine(grandTotal, tenderedAmountParsed) { grand, tender ->
        if (tender <= 0.0) null else tender - grand
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val cashConfirmEnabled: StateFlow<Boolean> = combine(grandTotal, tenderedAmountParsed) { grand, tender ->
        tender + EPSILON >= grand
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val mobileConfirmEnabled: StateFlow<Boolean> = grandTotal.map { it > 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val splitLine1Parsed: StateFlow<Double> = _splitLine1AmountText
        .map { parseAmount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val splitLine2DisplayAmount: StateFlow<Double?> = combine(grandTotal, splitLine1Parsed) { grand, a1 ->
        if (grand <= 0.0) null
        else if (a1 <= 0.0) null
        else if (a1 > grand + EPSILON) null
        else grand - a1
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val splitConfirmEnabled: StateFlow<Boolean> = combine(
        grandTotal,
        splitLine1Parsed,
        splitLine2DisplayAmount,
    ) { grand, a1, a2 ->
        if (grand <= 0.0 || a2 == null) return@combine false
        if (a1 <= EPSILON || a2 <= EPSILON) return@combine false
        abs(a1 + a2 - grand) <= EPSILON * max(grand, 1.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Cash / mobile / split only — credit sales use **On credit** (Prompt U6). */
    val paidSaleConfirmEnabled: StateFlow<Boolean> = combine(
        combine(_splitMode, _primaryTab) { split, tab -> split to tab },
        cashConfirmEnabled,
        mobileConfirmEnabled,
        splitConfirmEnabled,
    ) { splitTab, cashOk, mobileOk, splitOk ->
        val (split, tab) = splitTab
        when {
            split -> splitOk
            tab == PrimaryPaymentTab.CREDIT -> false
            tab == PrimaryPaymentTab.CASH -> cashOk
            else -> mobileOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Informal credit: requires customer and non-split cart (Prompt U6). */
    val onCreditEnabled: StateFlow<Boolean> = combine(_splitMode, selectedCustomer, grandTotal) { split, c, g ->
        !split && c != null && g > EPSILON
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Prompt P9: paste-SMS ref extraction is FULL_AI + downloaded model only. */
    val showMobileMoneyPasteAi: Boolean
        get() = capabilityTier == CapabilityTier.FULL_AI && gemmaService.isAvailable

    private val _smsParseInProgress = MutableStateFlow(false)
    val smsParseInProgress: StateFlow<Boolean> = _smsParseInProgress.asStateFlow()

    private val _smsParseError = MutableStateFlow<String?>(null)
    val smsParseError: StateFlow<String?> = _smsParseError.asStateFlow()

    fun selectTab(tab: PrimaryPaymentTab) {
        _primaryTab.value = tab
    }

    fun setSplitMode(enabled: Boolean) {
        _splitMode.value = enabled
    }

    fun setAmountTenderedText(text: String) {
        _amountTenderedText.value = text
    }

    fun setMobileMoneyNetwork(code: String) {
        _mobileMoneyNetwork.value = code
    }

    fun setMobileMoneyRef(text: String) {
        _mobileMoneyRef.value = text
    }

    fun setCreditDueDate(epochMillis: Long?) {
        _creditDueDateMillis.value = epochMillis
    }

    fun setSplitLine1Method(method: SplitLineMethod) {
        _splitLine1Method.value = method
    }

    fun setSplitLine2Method(method: SplitLineMethod) {
        _splitLine2Method.value = method
    }

    fun setSplitLine1AmountText(text: String) {
        _splitLine1AmountText.value = text
    }

    fun clearSmsParseError() {
        _smsParseError.value = null
    }

    /**
     * Prompt P9: FULL_AI uses Gemma on pasted confirmation text; otherwise regex-only.
     */
    fun parseMobileMoneyConfirmationSms(pasted: String) {
        val text = pasted.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _smsParseInProgress.value = true
            _smsParseError.value = null
            try {
                val ref = withContext(Dispatchers.IO) {
                    if (showMobileMoneyPasteAi) {
                        runCatching {
                            val prompt = """
                                Extract the transaction reference code from this mobile money SMS.
                                Return ONLY the reference code, nothing else.
                                If no reference is found, return 'NOT_FOUND'.
                                SMS: $text
                            """.trimIndent()
                            val raw = gemmaService.generateResponse(prompt).trim()
                            extractRefFromAiOutput(raw) ?: extractRefByRegex(text)
                        }.getOrElse { extractRefByRegex(text) }
                    } else {
                        extractRefByRegex(text)
                    }
                }
                if (ref != null) {
                    _mobileMoneyRef.value = ref
                } else {
                    _smsParseError.value = "PARSE_FAILED"
                }
            } finally {
                _smsParseInProgress.value = false
            }
        }
    }

    /**
     * Persists the current cart as a POS sale, then clears the cart.
     * Call from a coroutine on [Dispatchers.IO].
     */
    suspend fun commitCurrentSale(): SaleCommitResult {
        if (!_splitMode.value && _primaryTab.value == PrimaryPaymentTab.CREDIT) {
            return SaleCommitResult.Failure(appContext.getString(R.string.payment_use_on_credit_button))
        }
        val lines = cartRepository.items.value
        if (lines.isEmpty()) return SaleCommitResult.EmptyCart
        val money = cartRepository.monetary.value
        if (money.grandTotal <= 0.0) return SaleCommitResult.Failure("Invalid total")
        val draft = buildPaymentDraft()
        val taxRate = cartRepository.activeSettings.value?.taxRate ?: 0.0
        val cartCustomerId = cartRepository.selectedCustomer.value?.id
        return try {
            val txId = saleRepository.commitPosSale(
                lines = lines,
                taxAmount = money.taxAmount,
                grandTotal = money.grandTotal,
                taxRatePercent = taxRate,
                draft = draft,
                cartCustomerId = cartCustomerId,
            )
            cartManager.clear()
            SaleCommitResult.Success(txId)
        } catch (e: Exception) {
            SaleCommitResult.Failure(e.message ?: "Sale failed")
        }
    }

    /** Builds draft for sale commit. */
    fun buildPaymentDraft(): PaymentDraft {
        val grand = grandTotal.value
        val split = _splitMode.value
        val tab = _primaryTab.value
        val tender = tenderedAmountParsed.value
        val ch = changeDue.value
        val a1 = splitLine1Parsed.value
        val a2 = splitLine2DisplayAmount.value
        return PaymentDraft(
            grandTotal = grand,
            splitMode = split,
            primaryTab = tab,
            cashAmountTendered = if (!split && tab == PrimaryPaymentTab.CASH) tender else null,
            cashChangeDue = if (!split && tab == PrimaryPaymentTab.CASH) ch else null,
            mobileMoneyNetwork = if (!split && tab == PrimaryPaymentTab.MOBILE_MONEY) {
                _mobileMoneyNetwork.value
            } else {
                null
            },
            mobileMoneyRef = if (!split && tab == PrimaryPaymentTab.MOBILE_MONEY) {
                _mobileMoneyRef.value.trim().ifEmpty { null }
            } else {
                null
            },
            creditCustomerId = if (!split && tab == PrimaryPaymentTab.CREDIT) {
                selectedCustomer.value?.id
            } else {
                null
            },
            creditDueDateMillis = if (!split && tab == PrimaryPaymentTab.CREDIT) {
                _creditDueDateMillis.value
            } else {
                null
            },
            creditNote = null,
            splitLine1Method = if (split) _splitLine1Method.value else null,
            splitLine1Amount = if (split) a1 else null,
            splitLine2Method = if (split) _splitLine2Method.value else null,
            splitLine2Amount = if (split) a2 else null,
        )
    }

    /**
     * Completes the cart as an informal credit sale (Prompt U6): [TransactionNoteTypes.CREDIT_EXTENDED]
     * plus a [Debt] row for the selected customer.
     */
    suspend fun commitOnCreditSale(dueMillis: Long?, note: String): SaleCommitResult {
        if (_splitMode.value) {
            return SaleCommitResult.Failure(appContext.getString(R.string.payment_credit_no_split))
        }
        val customer = selectedCustomer.value
            ?: return SaleCommitResult.Failure(appContext.getString(R.string.payment_credit_customer_required))
        val lines = cartRepository.items.value
        if (lines.isEmpty()) return SaleCommitResult.EmptyCart
        val money = cartRepository.monetary.value
        if (money.grandTotal <= 0.0) return SaleCommitResult.Failure("Invalid total")
        val draft = PaymentDraft(
            grandTotal = money.grandTotal,
            splitMode = false,
            primaryTab = PrimaryPaymentTab.CREDIT,
            cashAmountTendered = null,
            cashChangeDue = null,
            mobileMoneyNetwork = null,
            mobileMoneyRef = null,
            creditCustomerId = customer.id,
            creditDueDateMillis = dueMillis,
            creditNote = note.trim().ifEmpty { null },
            splitLine1Method = null,
            splitLine1Amount = null,
            splitLine2Method = null,
            splitLine2Amount = null,
        )
        val taxRate = cartRepository.activeSettings.value?.taxRate ?: 0.0
        return try {
            val txId = saleRepository.commitPosSale(
                lines = lines,
                taxAmount = money.taxAmount,
                grandTotal = money.grandTotal,
                taxRatePercent = taxRate,
                draft = draft,
                cartCustomerId = customer.id,
            )
            cartManager.clear()
            SaleCommitResult.Success(txId)
        } catch (e: Exception) {
            SaleCommitResult.Failure(e.message ?: "Sale failed")
        }
    }

    companion object {
        private const val EPSILON = 0.005

        fun parseAmount(raw: String): Double =
            raw.trim().replace(",", "").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

        /** Tolerate float noise when comparing sale total to dialog amount (Prompt U6). */
        fun amountsMatch(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= EPSILON

        private val REF_PATTERNS = listOf(
            Pattern.compile("(?i)(?:ref|reference|code|confirmation|txn|transaction)[#:\\s]*([A-Z0-9]{6,32})"),
            Pattern.compile("\\b([A-Z]{1,3}[0-9]{6,14})\\b"),
            Pattern.compile("\\b([0-9]{10,12})\\b"),
        )

        fun extractRefByRegex(text: String): String? {
            for (p in REF_PATTERNS) {
                val m = p.matcher(text)
                if (m.find()) return m.group(1)
            }
            return null
        }

        fun extractRefFromAiOutput(output: String): String? {
            val cleaned = output.trim().lines().firstOrNull()?.trim() ?: return null
            if (cleaned.equals("NONE", ignoreCase = true)) return null
            if (cleaned.equals("NOT_FOUND", ignoreCase = true)) return null
            val token = cleaned.removePrefix("REF:").removePrefix("ref:").trim()
            return token.takeIf { it.length in 4..32 && it.all { c -> c.isLetterOrDigit() } }
        }
    }
}
