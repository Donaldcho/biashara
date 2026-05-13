package com.biasharaai.ui.pos

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.databinding.ViewTotalsBarBinding
import com.biasharaai.pos.cart.CartRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Live subtotal / tax / grand total strip bound to [CartRepository] monetary flows.
 */
class TotalsBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewTotalsBarBinding

    private var observing = false

    init {
        orientation = VERTICAL
        binding = ViewTotalsBarBinding.inflate(LayoutInflater.from(context), this)
    }

    fun bind(owner: LifecycleOwner, cartRepository: CartRepository, moneyFormat: NumberFormat) {
        if (observing) return
        observing = true
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    cartRepository.subtotal.collect { v ->
                        binding.textSubtotalValue.text = moneyFormat.format(v)
                    }
                }
                launch {
                    cartRepository.taxAmount.collect { tax ->
                        binding.textTaxValue.text = moneyFormat.format(tax)
                    }
                }
                launch {
                    cartRepository.grandTotal.collect { g ->
                        binding.textGrandValue.text = moneyFormat.format(g)
                    }
                }
                launch {
                    cartRepository.activeSettings.collect { settings ->
                        val rate = settings?.taxRate ?: 0.0
                        val showTax = rate > 0.0
                        binding.rowTax.visibility = if (showTax) View.VISIBLE else View.GONE
                        if (showTax) {
                            val label = settings?.taxLabel?.takeIf { it.isNotBlank() } ?: context.getString(
                                com.biasharaai.R.string.pos_totals_tax_default,
                            )
                            binding.textTaxLabel.text =
                                context.getString(com.biasharaai.R.string.pos_totals_tax_label, label, rate)
                        }
                    }
                }
            }
        }
    }
}
