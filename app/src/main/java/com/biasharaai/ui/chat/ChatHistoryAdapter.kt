package com.biasharaai.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.data.local.db.ChatSessionEntity
import com.biasharaai.databinding.ItemChatSessionBinding
import java.text.DateFormat
import java.util.Date

class ChatHistoryAdapter(
    private val onOpen: (Long) -> Unit,
    private val onDelete: (Long) -> Unit,
) : ListAdapter<ChatSessionEntity, ChatHistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChatSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onOpen, onDelete)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemChatSessionBinding,
        private val onOpen: (Long) -> Unit,
        private val onDelete: (Long) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ChatSessionEntity) {
            binding.textTitle.text = row.title.ifBlank { binding.root.context.getString(com.biasharaai.R.string.chat_session_default_title) }
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            binding.textUpdated.text = fmt.format(Date(row.updatedAt))
            binding.btnOpen.setOnClickListener { onOpen(row.id) }
            binding.btnDelete.setOnClickListener { onDelete(row.id) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatSessionEntity>() {
            override fun areItemsTheSame(a: ChatSessionEntity, b: ChatSessionEntity) = a.id == b.id
            override fun areContentsTheSame(a: ChatSessionEntity, b: ChatSessionEntity) = a == b
        }
    }
}
