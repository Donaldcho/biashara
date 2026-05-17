package com.biasharaai.ui.pos

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.databinding.ItemReturnLineBinding

class ReturnLineAdapter(
    private val onCheckedChange: (lineId: Long, checked: Boolean) -> Unit,
    private val onQtyChange: (lineId: Long, qty: Int) -> Unit,
) : ListAdapter<ReturnLineUiRow, ReturnLineAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReturnLineBinding.inflate(
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
        private val binding: ItemReturnLineBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var watcher: TextWatcher? = null
        private var boundLineId: Long = -1

        fun bind(row: ReturnLineUiRow) {
            boundLineId = row.line.id
            binding.textProductName.text = row.line.productName
            binding.textLineHint.text = binding.root.context.getString(
                R.string.return_line_hint,
                row.line.quantity,
                row.maxReturnable,
            )

            binding.checkboxSelect.setOnCheckedChangeListener(null)
            binding.checkboxSelect.isChecked = row.checked
            binding.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(row.line.id, isChecked)
            }

            watcher?.let { binding.editQty.removeTextChangedListener(it) }
            val qtyStr = row.returnQty.toString()
            if (binding.editQty.text?.toString() != qtyStr) {
                binding.editQty.setText(qtyStr)
            }
            watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val id = boundLineId
                    if (id < 0L) return
                    val n = s?.toString()?.toIntOrNull() ?: return
                    onQtyChange(id, n)
                }
            }
            binding.editQty.addTextChangedListener(watcher)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReturnLineUiRow>() {
            override fun areItemsTheSame(a: ReturnLineUiRow, b: ReturnLineUiRow): Boolean =
                a.line.id == b.line.id

            override fun areContentsTheSame(a: ReturnLineUiRow, b: ReturnLineUiRow): Boolean =
                a == b
        }
    }
}
