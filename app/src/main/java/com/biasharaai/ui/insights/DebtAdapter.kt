package com.biasharaai.ui.insights

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.data.local.db.UnpaidDebtRow
import com.biasharaai.databinding.ItemDebtRowBinding
import com.biasharaai.money.MoneyFormatter

class DebtAdapter(
    private val moneyFormatter: MoneyFormatter,
    private val onPaid: (Long) -> Unit,
    private val onRemind: (Long) -> Unit,
) : ListAdapter<UnpaidDebtRow, DebtAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDebtRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemDebtRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: UnpaidDebtRow) {
            binding.textCustomerName.text = row.customerName
            val amt = moneyFormatter.format(row.debt.amount)
            binding.textAmountDays.text = binding.root.context.getString(
                com.biasharaai.R.string.credit_row_amount_days,
                amt,
                row.daysOutstanding,
            )
            binding.btnPaid.setOnClickListener { onPaid(row.debt.id) }
            binding.btnRemind.setOnClickListener { onRemind(row.debt.id) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UnpaidDebtRow>() {
            override fun areItemsTheSame(a: UnpaidDebtRow, b: UnpaidDebtRow) = a.debt.id == b.debt.id
            override fun areContentsTheSame(a: UnpaidDebtRow, b: UnpaidDebtRow) = a == b
        }
    }
}
