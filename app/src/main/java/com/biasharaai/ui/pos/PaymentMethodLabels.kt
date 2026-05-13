package com.biasharaai.ui.pos

import android.content.Context
import com.biasharaai.R

object PaymentMethodLabels {
    fun label(context: Context, paymentMethod: String): String {
        return when (paymentMethod.uppercase()) {
            "CASH" -> context.getString(R.string.tx_payment_cash)
            "CREDIT" -> context.getString(R.string.tx_payment_credit)
            "MOBILE_MONEY", "MOBILE MONEY" -> context.getString(R.string.tx_payment_mobile)
            "SPLIT" -> context.getString(R.string.tx_payment_split)
            "RETURN" -> context.getString(R.string.tx_payment_return)
            else -> context.getString(R.string.tx_payment_other)
        }
    }
}
