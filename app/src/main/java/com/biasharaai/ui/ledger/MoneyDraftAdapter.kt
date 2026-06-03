package com.biasharaai.ui.ledger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.MoneyDraft
import com.biasharaai.databinding.ItemMoneyDraftBinding
import com.biasharaai.money.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoneyDraftAdapter(
    private val moneyFormatter: MoneyFormatter,
    private val onApprove: (MoneyDraft) -> Unit,
    private val onReject: (MoneyDraft) -> Unit,
) : ListAdapter<MoneyDraft, MoneyDraftAdapter.Holder>(Diff) {

    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMoneyDraftBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemMoneyDraftBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(draft: MoneyDraft) {
            val ctx = binding.root.context
            val isMoneyIn = draft.direction == LedgerDirection.MONEY_IN
            val amountColor = ContextCompat.getColor(
                ctx,
                if (isMoneyIn) R.color.biashara_success_green else R.color.biashara_red,
            )
            val sign = if (isMoneyIn) "+" else "-"

            binding.textDraftTitle.text = draft.description
            binding.textDraftAmount.setTextColor(amountColor)
            binding.textDraftAmount.text = sign + moneyFormatter.format(draft.amount)
            binding.textDraftMeta.text = buildString {
                append(dateFormat.format(Date(draft.createdAt)))
                append(" | ")
                append(draft.captureMethod.name.replace('_', ' '))
                draft.parsedReference?.takeIf { it.isNotBlank() }?.let {
                    append(" | Ref ")
                    append(it)
                }
            }
            binding.textDraftParty.text = draft.parsedCounterparty
                ?: draft.rawText?.lineSequence()?.firstOrNull()?.take(80)
                ?: ctx.getString(R.string.money_draft_no_counterparty)
            binding.textDraftConfidence.text = ctx.getString(
                R.string.money_draft_confidence,
                (draft.parserConfidence * 100).toInt().coerceIn(0, 100),
            )
            binding.btnApprove.setOnClickListener { onApprove(draft) }
            binding.btnReject.setOnClickListener { onReject(draft) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MoneyDraft>() {
        override fun areItemsTheSame(a: MoneyDraft, b: MoneyDraft) = a.id == b.id
        override fun areContentsTheSame(a: MoneyDraft, b: MoneyDraft) = a == b
    }
}
