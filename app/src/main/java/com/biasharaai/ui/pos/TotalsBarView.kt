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
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    fun bind(owner: LifecycleOwner, cartRepository: CartRepository, moneyFormatter: MoneyFormatter) {
        if (observing) return
        observing = true
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        cartRepository.subtotal,
                        cartRepository.activeSettings,
                    ) { sub, _ -> sub }.collect { v ->
                        binding.textSubtotalValue.text = moneyFormatter.format(v)
                    }
                }
                launch {
                    combine(
                        cartRepository.taxAmount,
                        cartRepository.activeSettings,
                    ) { tax, _ -> tax }.collect { tax ->
                        binding.textTaxValue.text = moneyFormatter.format(tax)
                    }
                }
                launch {
                    combine(
                        cartRepository.grandTotal,
                        cartRepository.activeSettings,
                    ) { g, _ -> g }.collect { g ->
                        binding.textGrandValue.text = moneyFormatter.format(g)
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
