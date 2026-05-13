package com.biasharaai.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.biasharaai.databinding.ItemChatMessageBinding
import java.io.File

/**
 * RecyclerView adapter for chat messages.
 */
class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) =
                old.stableId == new.stableId

            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) =
                old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(
        private val binding: ItemChatMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            if (message.isUser) {
                binding.cardUser.visibility = View.VISIBLE
                binding.cardAi.visibility = View.GONE
                binding.textUserMessage.text = message.text
                val path = message.imageUri
                if (!path.isNullOrBlank()) {
                    binding.imageUserAttachment.visibility = View.VISIBLE
                    binding.imageUserAttachment.load(File(path)) {
                        crossfade(true)
                    }
                } else {
                    binding.imageUserAttachment.visibility = View.GONE
                    binding.imageUserAttachment.setImageDrawable(null)
                }
            } else {
                binding.cardUser.visibility = View.GONE
                binding.cardAi.visibility = View.VISIBLE
                binding.textAiMessage.text = message.text
            }
        }
    }
}
