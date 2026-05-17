package com.biasharaai.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.databinding.ItemAgentSettingsRowBinding

class AgentSettingsAdapter(
    private val onRowClick: (String) -> Unit,
) : ListAdapter<AgentSettingsRow, AgentSettingsAdapter.Vh>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemAgentSettingsRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val row = getItem(position)
        holder.binding.textTitle.text = row.title
        holder.binding.textSummary.text = row.summary
        val showHist = row.expanded && row.historyText.isNotEmpty()
        holder.binding.textHistory.visibility = if (showHist) View.VISIBLE else View.GONE
        holder.binding.textHistory.text = row.historyText
        holder.binding.root.setOnClickListener { onRowClick(row.agentType) }
    }

    class Vh(val binding: ItemAgentSettingsRowBinding) : RecyclerView.ViewHolder(binding.root)

    private object Diff : DiffUtil.ItemCallback<AgentSettingsRow>() {
        override fun areItemsTheSame(oldItem: AgentSettingsRow, newItem: AgentSettingsRow): Boolean =
            oldItem.agentType == newItem.agentType

        override fun areContentsTheSame(oldItem: AgentSettingsRow, newItem: AgentSettingsRow): Boolean =
            oldItem == newItem
    }
}
