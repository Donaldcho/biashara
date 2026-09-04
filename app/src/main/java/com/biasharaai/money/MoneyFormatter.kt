package com.biasharaai.money

import com.biasharaai.pos.cart.CartRepository
import java.text.DecimalFormat
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
    private val zeroDecimalCurrencies = setOf("XAF", "XOF", "BIF", "DJF", "GNF", "KMF", "MGA", "RWF", "UGX")

    private fun resolvedCurrencyCode(): String =
        cartRepository.activeSettings.value?.currencyCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: RegionalDefaults.CURRENCY_CODE

    /** Currency-aware formatter (reflects latest [activeSettings] each call). */
    fun numberFormat(): NumberFormat {
        val code = resolvedCurrencyCode().uppercase(Locale.ROOT)
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
        if (currency != null) {
            return NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                this.currency = currency
            }
        }
        val pattern = if (zeroDecimalCurrencies.contains(code)) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern).apply {
            positivePrefix = "$code "
            negativePrefix = "-$code "
        }
    }

    fun format(amount: Double): String = numberFormat().format(amount)
}
