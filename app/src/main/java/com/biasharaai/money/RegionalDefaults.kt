package com.biasharaai.money

/**
 * Cameroon-first product defaults. Users can still change currency and payment
 * details in Settings/POS, but cold installs and fallbacks should match HQ.
 */
object RegionalDefaults {
    const val CURRENCY_CODE = "XAF"
    const val CURRENCY_SYMBOL = "FCFA"
    const val MOBILE_MONEY_NETWORK = "MTN"
}
