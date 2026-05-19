package com.biasharaai.ui.pos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.databinding.ItemServiceGridBinding
import com.biasharaai.service.isVoucherable

class ServiceGridAdapter(
    private val formatMoney: (Double) -> String,
    private val onServiceClick: (ServiceItem) -> Unit,
    private val onServiceLongClick: ((ServiceItem) -> Unit)? = null,
) : ListAdapter<ServiceItem, ServiceGridAdapter.Vh>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemServiceGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Vh(private val binding: ItemServiceGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ServiceItem) {
            binding.textInitial.text = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textName.text = item.name
            binding.textPrice.text = formatMoney(item.basePrice)
            binding.badgeVoucher.isVisible = item.isVoucherable
            binding.root.setOnClickListener { onServiceClick(item) }
            binding.root.setOnLongClickListener {
                if (item.isVoucherable && onServiceLongClick != null) {
                    onServiceLongClick.invoke(item)
                    true
                } else {
                    false
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ServiceItem>() {
        override fun areItemsTheSame(a: ServiceItem, b: ServiceItem) = a.id == b.id
        override fun areContentsTheSame(a: ServiceItem, b: ServiceItem) = a == b
    }
}
