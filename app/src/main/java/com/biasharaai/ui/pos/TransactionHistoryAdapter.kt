package com.biasharaai.ui.pos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.databinding.ItemTransactionHistoryRowBinding
import com.biasharaai.money.MoneyFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class TransactionHistoryAdapter(
    private val moneyFormatter: MoneyFormatter,
    private val dateFormat: DateFormat,
    private val onClick: (Transaction) -> Unit,
) : ListAdapter<Transaction, TransactionHistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTransactionHistoryRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemTransactionHistoryRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = this@VH.layoutPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(tx: Transaction) {
            val ctx = binding.root.context
            val receipt = tx.receiptNumber?.takeIf { it.isNotBlank() }
            binding.textReceipt.text = if (receipt != null) {
                ctx.getString(R.string.transactions_receipt_label, receipt)
            } else {
                ctx.getString(R.string.transactions_receipt_fallback, tx.id)
            }
            binding.textDate.text = dateFormat.format(Date(tx.date))
            binding.textTotal.text = moneyFormatter.format(tx.amount)
            binding.textPaymentMethod.text = PaymentMethodLabels.label(ctx, tx.paymentMethod)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(a: Transaction, b: Transaction): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Transaction, b: Transaction): Boolean = a == b
        }
    }
}
