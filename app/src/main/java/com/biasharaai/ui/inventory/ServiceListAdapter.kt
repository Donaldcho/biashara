package com.biasharaai.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.databinding.ItemServiceListBinding

class ServiceListAdapter(
    private val formatMoney: (Double) -> String,
    private val onItemClick: (ServiceItem) -> Unit,
) : ListAdapter<ServiceItem, ServiceListAdapter.Vh>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemServiceListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Vh(private val binding: ItemServiceListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ServiceItem) {
            binding.textName.text = item.name
            binding.textPrice.text = formatMoney(item.basePrice)
            binding.textToken.text = item.catalogueToken
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ServiceItem>() {
        override fun areItemsTheSame(a: ServiceItem, b: ServiceItem) = a.id == b.id
        override fun areContentsTheSame(a: ServiceItem, b: ServiceItem) = a == b
    }
}
