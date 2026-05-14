package com.biasharaai.money

import com.biasharaai.pos.cart.CartRepository
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats monetary amounts using the shop currency from [CartRepository.activeSettings]
 * ([com.biasharaai.data.local.db.AppSettings.currencyCode]).
 */
@Singleton
class MoneyFormatter @Inject constructor(
    private val cartRepository: CartRepository,
) {

    private fun resolvedCurrencyCode(): String =
        cartRepository.activeSettings.value?.currencyCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: "KES"

    /** Currency-aware formatter (reflects latest [activeSettings] each call). */
    fun numberFormat(): NumberFormat {
        val code = resolvedCurrencyCode()
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            try {
                currency = Currency.getInstance(code)
            } catch (_: IllegalArgumentException) {
                currency = Currency.getInstance("KES")
            }
        }
    }

    fun format(amount: Double): String = numberFormat().format(amount)
}
