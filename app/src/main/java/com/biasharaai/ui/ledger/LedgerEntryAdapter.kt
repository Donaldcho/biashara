package com.biasharaai.ui.ledger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntry
import com.biasharaai.databinding.ItemLedgerEntryBinding
import com.biasharaai.money.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LedgerEntryAdapter(
    private val moneyFormatter: MoneyFormatter,
    private val onEntryClick: ((LedgerEntry) -> Unit)? = null,
) : ListAdapter<LedgerEntry, LedgerEntryAdapter.Holder>(Diff) {

    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
    private var evidenceEntryIds: Set<Long> = emptySet()

    fun setEvidenceEntryIds(ids: Set<Long>) {
        evidenceEntryIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemLedgerEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemLedgerEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onEntryClick?.invoke(getItem(pos))
            }
        }

        fun bind(entry: LedgerEntry) {
            binding.textDescription.text = entry.description
            binding.textMeta.text = buildString {
                append(dateFormat.format(Date(entry.occurredAt)))
                append(" · ")
                append(entry.type.name.replace('_', ' '))
            }
            binding.textBalance.text = moneyFormatter.format(entry.runningBalance)
            binding.textEvidenceBadge.visibility =
                if (entry.id in evidenceEntryIds) View.VISIBLE else View.GONE
            val ctx = binding.root.context
            val amountColor = when (entry.direction) {
                LedgerDirection.MONEY_IN ->
                    ContextCompat.getColor(ctx, R.color.biashara_success_green)
                LedgerDirection.MONEY_OUT ->
                    ContextCompat.getColor(ctx, R.color.biashara_red)
                LedgerDirection.NEUTRAL ->
                    ContextCompat.getColor(ctx, R.color.biashara_muted)
            }
            val prefix = when (entry.direction) {
                LedgerDirection.MONEY_IN -> "+"
                LedgerDirection.MONEY_OUT -> "−"
                LedgerDirection.NEUTRAL -> ""
            }
            binding.textAmount.setTextColor(amountColor)
            binding.textAmount.text = if (entry.direction == LedgerDirection.NEUTRAL) {
                "—"
            } else {
                prefix + moneyFormatter.format(entry.amount)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<LedgerEntry>() {
        override fun areItemsTheSame(a: LedgerEntry, b: LedgerEntry) = a.id == b.id
        override fun areContentsTheSame(a: LedgerEntry, b: LedgerEntry) = a == b
    }
}
