package com.biasharaai.ui.pos

import android.widget.EditText
import androidx.fragment.app.Fragment
import com.biasharaai.R
import com.biasharaai.pos.cart.CartItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CartLinePriceOverrideDialog {

    fun show(
        fragment: Fragment,
        item: CartItem,
        formatMoney: (Double) -> String,
        onApplied: (CartItem, Double) -> Unit,
    ) {
        val ctx = fragment.requireContext()
        val input = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = fragment.getString(R.string.pos_price_override_hint)
            setText(item.effectivePrice.toString())
        }
        val padding = ctx.resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val container = android.widget.FrameLayout(ctx).apply {
            setPadding(padding, padding, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(fragment.getString(R.string.pos_price_override_title, item.product.name))
            .setMessage(
                fragment.getString(
                    R.string.pos_price_override_message,
                    formatMoney(item.product.price),
                ),
            )
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pos_price_override_confirm) { _, _ ->
                val raw = input.text?.toString()?.trim().orEmpty()
                val newPrice = raw.toDoubleOrNull() ?: return@setPositiveButton
                if (newPrice <= 0) return@setPositiveButton
                onApplied(item, newPrice)
            }
            .show()
    }
}
