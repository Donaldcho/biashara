package com.biasharaai.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.databinding.ItemOrderLineBinding
import com.biasharaai.money.MoneyFormatter

class OrderReviewAdapter(
    private val moneyFormatter: MoneyFormatter,
    private val onFindProduct: (OrderLineItem) -> Unit,
) : ListAdapter<OrderLineItem, OrderReviewAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOrderLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemOrderLineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: OrderLineItem) {
            val ctx = binding.root.context
            val matched = line.isMatched
            val p = line.resolvedProduct
            val strokePx = (2 * ctx.resources.displayMetrics.density).toInt()
            binding.card.strokeWidth = strokePx
            if (matched && p != null) {
                binding.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.biashara_success_light))
                binding.card.strokeColor = ContextCompat.getColor(ctx, R.color.biashara_success_green)
                binding.textPrimaryLabel.text = p.name
                val unitPart = line.unit?.let { " · $it" }.orEmpty()
                binding.textSecondary.text = ctx.getString(
                    R.string.order_parser_line_qty_price,
                    line.quantity,
                    unitPart,
                    moneyFormatter.format(p.price),
                )
                binding.btnFindProduct.isVisible = false
            } else {
                binding.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.biashara_amber_light))
                binding.card.strokeColor = ContextCompat.getColor(ctx, R.color.biashara_amber)
                binding.textPrimaryLabel.text = line.parsedName
                val unitPart = line.unit?.let { " · $it" }.orEmpty()
                binding.textSecondary.text = ctx.getString(
                    R.string.order_parser_line_unmatched_qty,
                    line.quantity,
                    unitPart,
                )
                binding.btnFindProduct.isVisible = true
                binding.btnFindProduct.setOnClickListener { onFindProduct(line) }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OrderLineItem>() {
            override fun areItemsTheSame(a: OrderLineItem, b: OrderLineItem) = a.rowKey == b.rowKey
            override fun areContentsTheSame(a: OrderLineItem, b: OrderLineItem) = a == b
        }
    }
}
