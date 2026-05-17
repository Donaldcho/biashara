package com.biasharaai.ui.cash

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.databinding.ViewCashQuickActionBarBinding

class CashQuickActionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewCashQuickActionBarBinding.inflate(LayoutInflater.from(context), this)

    var onScanIn: (() -> Unit)? = null
    var onScanOut: (() -> Unit)? = null
    var onAddIn: (() -> Unit)? = null
    var onAddOut: (() -> Unit)? = null
    var onPrintQr: (() -> Unit)? = null

    init {
        binding.btnScanIn.setOnClickListener { onScanIn?.invoke() }
        binding.btnScanOut.setOnClickListener { onScanOut?.invoke() }
        binding.btnAddIn.setOnClickListener { onAddIn?.invoke() }
        binding.btnAddOut.setOnClickListener { onAddOut?.invoke() }
        binding.btnPrintQr.setOnClickListener { onPrintQr?.invoke() }
    }

    fun wireNavigation(navigate: (destination: CashDestination) -> Unit) {
        onScanIn = { navigate(CashDestination.Scan(LedgerDirection.MONEY_IN)) }
        onScanOut = { navigate(CashDestination.Scan(LedgerDirection.MONEY_OUT)) }
        onAddIn = { navigate(CashDestination.Manual(LedgerDirection.MONEY_IN)) }
        onAddOut = { navigate(CashDestination.Manual(LedgerDirection.MONEY_OUT)) }
        onPrintQr = { navigate(CashDestination.QrGenerator) }
    }

    sealed interface CashDestination {
        data class Scan(val direction: LedgerDirection) : CashDestination
        data class Manual(val direction: LedgerDirection) : CashDestination
        data object QrGenerator : CashDestination
    }
}
