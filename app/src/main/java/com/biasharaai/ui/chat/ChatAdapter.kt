package com.biasharaai.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import coil.load
import com.biasharaai.databinding.ItemChatMessageBinding
import com.biasharaai.locale.LanguagePreferences
import java.io.File
import java.util.Locale

/**
 * RecyclerView adapter for chat messages.
 *
 * @param onAssistantFeedback when non-null, persisted assistant rows (`stableId` > 0) show
 * lightweight feedback controls; vote is `1` (helpful) or `-1` (not helpful).
 */
class ChatAdapter(
    private val onAssistantFeedback: ((Long, Int) -> Unit)? = null,
    private val onMessageMenu: ((ChatMessage) -> Unit)? = null,
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    var assistantTtsEnabled: Boolean = false

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
        return ChatViewHolder(binding, onAssistantFeedback, onMessageMenu)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position), assistantTtsEnabled)
    }

    class ChatViewHolder(
        private val binding: ItemChatMessageBinding,
        private val onAssistantFeedback: ((Long, Int) -> Unit)?,
        private val onMessageMenu: ((ChatMessage) -> Unit)?,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage, assistantTtsEnabled: Boolean) {
            binding.btnFeedbackUp.setOnClickListener(null)
            binding.btnFeedbackDown.setOnClickListener(null)
            binding.btnUserMessageMenu.setOnClickListener(null)
            binding.btnAiMessageMenu.setOnClickListener(null)
            binding.cardUser.setOnLongClickListener(null)
            binding.cardAi.setOnLongClickListener(null)
            if (message.isUser) {
                binding.cardUser.visibility = View.VISIBLE
                binding.cardAi.visibility = View.GONE
                binding.textUserMessage.text = message.text
                bindMessageMenu(binding.btnUserMessageMenu, binding.cardUser, message)
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
                bindMessageMenu(binding.btnAiMessageMenu, binding.cardAi, message)
                val meta = message.sourceTags.takeIf { it.isNotEmpty() }?.joinToString(", ")
                val ctx = binding.root.context
                if (meta.isNullOrBlank() && message.confidenceLabel.isNullOrBlank()) {
                    binding.textAiMeta.visibility = View.GONE
                } else {
                    binding.textAiMeta.visibility = View.VISIBLE
                    val basedOn = meta?.let { ctx.getString(R.string.chat_answer_based_on, it) }
                    binding.textAiMeta.text = when {
                        basedOn != null && message.confidenceLabel != null ->
                            ctx.getString(R.string.chat_answer_meta_join, basedOn, message.confidenceLabel)
                        basedOn != null -> basedOn
                        else -> message.confidenceLabel.orEmpty()
                    }
                }
                if (message.actionHint.isNullOrBlank()) {
                    binding.textAiActionHint.visibility = View.GONE
                } else {
                    binding.textAiActionHint.visibility = View.VISIBLE
                    binding.textAiActionHint.text = message.actionHint
                }
                val showSpeak = assistantTtsEnabled && message.text.isNotBlank()
                binding.buttonAssistantSpeak.visibility = if (showSpeak) View.VISIBLE else View.GONE
                if (showSpeak) {
                    binding.buttonAssistantSpeak.bindSpeakTarget(
                        message.text,
                        ttsLanguage(binding.root.context),
                    )
                } else {
                    binding.buttonAssistantSpeak.bindSpeakTarget("")
                }
                val showFeedback = message.stableId > 0L && onAssistantFeedback != null
                binding.rowAiFeedback.visibility = if (showFeedback) View.VISIBLE else View.GONE
                if (showFeedback) {
                    binding.btnFeedbackUp.isSelected = message.feedbackVote == 1
                    binding.btnFeedbackDown.isSelected = message.feedbackVote == -1
                    binding.btnFeedbackUp.setOnClickListener {
                        onAssistantFeedback.invoke(message.stableId, 1)
                    }
                    binding.btnFeedbackDown.setOnClickListener {
                        onAssistantFeedback.invoke(message.stableId, -1)
                    }
                }
            }
        }

        private fun bindMessageMenu(menu: View, bubble: View, message: ChatMessage) {
            val show = onMessageMenu != null && (message.text.isNotBlank() || message.stableId > 0L)
            menu.visibility = if (show) View.VISIBLE else View.GONE
            if (!show) return
            menu.setOnClickListener { onMessageMenu?.invoke(message) }
            bubble.setOnLongClickListener {
                onMessageMenu?.invoke(message)
                true
            }
        }

        private fun ttsLanguage(context: android.content.Context): String? {
            LanguagePreferences.getPersistedLocaleTag(context)?.let { tag ->
                val lang = tag.substringBefore('-', missingDelimiterValue = tag).lowercase(Locale.US)
                if (lang.isNotBlank()) return lang
            }
            return context.resources.configuration.locales[0]?.language?.lowercase(Locale.US)
        }
    }
}
