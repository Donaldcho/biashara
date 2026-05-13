package com.biasharaai.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.data.local.db.Alert
import com.biasharaai.databinding.ItemAlertCardBinding

class AlertCardAdapter(
    private val onReview: (Alert) -> Unit,
    private val onDismiss: (Alert) -> Unit,
) : ListAdapter<Alert, AlertCardAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlertCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemAlertCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: Alert) {
            val localized = alert.localizedMessage?.trim()?.takeIf { it.isNotEmpty() }
            if (localized != null) {
                binding.textAlertTitle.visibility = View.GONE
                binding.textAlertMessage.text = localized
            } else {
                binding.textAlertTitle.visibility = View.VISIBLE
                binding.textAlertTitle.text = alert.title
                binding.textAlertMessage.text = alert.message
            }
            binding.buttonReview.setOnClickListener { onReview(alert) }
            binding.buttonDismiss.setOnClickListener { onDismiss(alert) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alert, newItem: Alert): Boolean = oldItem == newItem
    }
}
