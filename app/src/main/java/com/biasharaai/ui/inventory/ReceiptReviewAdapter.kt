package com.biasharaai.ui.inventory

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.databinding.ItemReceiptReviewLineBinding
import com.google.android.material.card.MaterialCardView

class ReceiptReviewAdapter(
    private val onLineUpdated: (Int, ReceiptDraftLine) -> Unit,
    private val onDeleteLine: (Int) -> Unit,
) : RecyclerView.Adapter<ReceiptReviewAdapter.VH>() {

    private val items = mutableListOf<ReceiptDraftLine>()

    fun submitList(lines: List<ReceiptDraftLine>) {
        items.clear()
        items.addAll(lines)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReceiptReviewLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onLineUpdated, onDeleteLine)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.size == 1 && payloads[0] == PAYLOAD_STROKE) {
            holder.applyStroke(items[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class VH(
        private val binding: ItemReceiptReviewLineBinding,
        private val onLineUpdated: (Int, ReceiptDraftLine) -> Unit,
        private val onDeleteLine: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var nameWatcher: TextWatcher? = null
        private var qtyWatcher: TextWatcher? = null
        private var costWatcher: TextWatcher? = null

        fun bind(line: ReceiptDraftLine, position: Int) {
            binding.editName.removeTextChangedListener(nameWatcher)
            binding.editQuantity.removeTextChangedListener(qtyWatcher)
            binding.editCost.removeTextChangedListener(costWatcher)

            binding.editName.setText(line.name)
            binding.editQuantity.setText(line.quantityText)
            binding.editCost.setText(line.costText)

            nameWatcher = watcher(line, position) { s -> line.name = s?.toString().orEmpty() }
            qtyWatcher = watcher(line, position) { s -> line.quantityText = s?.toString().orEmpty() }
            costWatcher = watcher(line, position) { s -> line.costText = s?.toString().orEmpty() }
            binding.editName.addTextChangedListener(nameWatcher)
            binding.editQuantity.addTextChangedListener(qtyWatcher)
            binding.editCost.addTextChangedListener(costWatcher)

            binding.btnDeleteLine.setOnClickListener { onDeleteLine(position) }
            applyStroke(line)
        }

        fun applyStroke(line: ReceiptDraftLine) {
            val card = binding.root as MaterialCardView
            if (line.needsAmberHighlight()) {
                card.strokeWidth = dp(3)
                card.strokeColor = Color.parseColor("#FFA000")
            } else {
                card.strokeWidth = 0
            }
        }

        private fun watcher(
            line: ReceiptDraftLine,
            position: Int,
            apply: (Editable?) -> Unit,
        ): TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                apply(s)
                onLineUpdated(position, line)
            }
        }

        private fun dp(v: Int): Int = (v * binding.root.resources.displayMetrics.density).toInt()
    }

    companion object {
        const val PAYLOAD_STROKE = "stroke"
    }
}
