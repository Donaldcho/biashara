package com.biasharaai.ui.agent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.databinding.ItemAgentActionCardBinding
import com.biasharaai.databinding.ItemAgentWeeklyReviewCardBinding
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class AgentActionCardUiModel(
    val action: AgentAction,
    val isExecuting: Boolean,
)

private data class WeeklyChipJson(
    @SerializedName("label") val label: String = "",
    @SerializedName("value") val value: String = "",
)

private data class WeeklyPayloadJson(
    @SerializedName("chips") val chips: List<WeeklyChipJson> = emptyList(),
)

class AgentActionCardAdapter(
    private val onReview: (AgentAction) -> Unit,
    private val onApprove: (AgentAction) -> Unit,
    private val onReject: (AgentAction) -> Unit,
    private val onSnooze: (AgentAction) -> Unit,
    private val onDismiss: (AgentAction) -> Unit,
    private val onView: (AgentAction) -> Unit,
) : ListAdapter<AgentActionCardUiModel, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int {
        val a = getItem(position).action
        return if (a.agentType == AgentTypes.WEEKLY_REVIEW && a.actionVerb == "SHOW_REVIEW") {
            VT_WEEKLY_REVIEW
        } else {
            VT_DEFAULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VT_WEEKLY_REVIEW -> WeeklyVH(
                ItemAgentWeeklyReviewCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            else -> DefaultVH(
                ItemAgentActionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val model = getItem(position)
        when (holder) {
            is WeeklyVH -> holder.bind(model)
            is DefaultVH -> holder.bind(model)
        }
    }

    inner class DefaultVH(
        private val binding: ItemAgentActionCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(model: AgentActionCardUiModel) {
            val action = model.action
            val ctx = binding.root.context
            binding.textEmoji.text = emojiForAgentType(action.agentType)
            binding.textHeadline.text = action.headline
            binding.textDetail.text = action.detail

            val stripeColor = stripeColor(ctx, action)
            binding.viewUrgencyStripe.setBackgroundColor(stripeColor)

            binding.progressExecuting.isVisible = model.isExecuting
            binding.rowButtons.isEnabled = !model.isExecuting

            bindButtons(action, model.isExecuting)
        }

        private fun bindButtons(action: AgentAction, executing: Boolean) {
            val b = binding
            b.buttonPrimary.setOnClickListener(null)
            b.buttonSecondary.setOnClickListener(null)
            b.buttonTertiary.setOnClickListener(null)

            b.buttonPrimary.isEnabled = !executing
            b.buttonSecondary.isEnabled = !executing
            b.buttonTertiary.isEnabled = !executing

            if (action.executionType == "AUTO_EXECUTE") {
                b.buttonPrimary.visibility = View.VISIBLE
                b.buttonSecondary.visibility = View.GONE
                b.buttonTertiary.visibility = View.GONE
                b.buttonPrimary.text = b.root.context.getString(R.string.agent_action_view)
                b.buttonPrimary.setOnClickListener { onView(action) }
                return
            }

            when {
                action.urgency == "CRITICAL" && action.executionType == "REQUIRES_APPROVAL" -> {
                    b.buttonPrimary.visibility = View.VISIBLE
                    b.buttonSecondary.visibility = View.VISIBLE
                    b.buttonTertiary.visibility = View.VISIBLE
                    b.buttonPrimary.text = b.root.context.getString(R.string.agent_action_review)
                    b.buttonSecondary.text = b.root.context.getString(R.string.agent_action_approve)
                    b.buttonTertiary.text = b.root.context.getString(R.string.agent_action_reject)
                    b.buttonPrimary.setOnClickListener { onReview(action) }
                    b.buttonSecondary.setOnClickListener { onApprove(action) }
                    b.buttonTertiary.setOnClickListener { onReject(action) }
                }
                action.urgency == "HIGH" && action.executionType == "REQUIRES_APPROVAL" -> {
                    b.buttonPrimary.visibility = View.VISIBLE
                    b.buttonSecondary.visibility = View.VISIBLE
                    b.buttonTertiary.visibility = View.VISIBLE
                    b.buttonPrimary.text = b.root.context.getString(R.string.agent_action_review)
                    b.buttonSecondary.text = b.root.context.getString(R.string.agent_action_approve)
                    b.buttonTertiary.text = b.root.context.getString(R.string.agent_action_snooze)
                    b.buttonPrimary.setOnClickListener { onReview(action) }
                    b.buttonSecondary.setOnClickListener { onApprove(action) }
                    b.buttonTertiary.setOnClickListener { onSnooze(action) }
                }
                action.urgency == "MEDIUM" && action.executionType == "REQUIRES_APPROVAL" -> {
                    b.buttonPrimary.visibility = View.VISIBLE
                    b.buttonSecondary.visibility = View.VISIBLE
                    b.buttonTertiary.visibility = View.GONE
                    b.buttonPrimary.text = b.root.context.getString(R.string.agent_action_approve)
                    b.buttonSecondary.text = b.root.context.getString(R.string.agent_action_dismiss)
                    b.buttonPrimary.setOnClickListener { onApprove(action) }
                    b.buttonSecondary.setOnClickListener { onDismiss(action) }
                }
                action.urgency == "LOW" && action.executionType == "REQUIRES_APPROVAL" -> {
                    b.buttonPrimary.visibility = View.VISIBLE
                    b.buttonSecondary.visibility = View.GONE
                    b.buttonTertiary.visibility = View.GONE
                    b.buttonPrimary.text = b.root.context.getString(R.string.agent_action_dismiss)
                    b.buttonPrimary.setOnClickListener { onDismiss(action) }
                }
                else -> {
                    b.buttonPrimary.visibility = View.VISIBLE
                    b.buttonSecondary.visibility = View.VISIBLE
                    b.buttonTertiary.visibility = View.GONE
                    b.buttonPrimary.text = b.root.context.getString(R.string.agent_action_review)
                    b.buttonSecondary.text = b.root.context.getString(R.string.agent_action_dismiss)
                    b.buttonPrimary.setOnClickListener { onReview(action) }
                    b.buttonSecondary.setOnClickListener { onDismiss(action) }
                }
            }
        }

        private fun stripeColor(ctx: android.content.Context, action: AgentAction): Int {
            val res = ctx.resources
            return when (action.status) {
                "EXECUTED" -> res.getColor(R.color.biashara_success_green, ctx.theme)
                else -> when (action.urgency) {
                    "CRITICAL" -> res.getColor(R.color.biashara_red, ctx.theme)
                    "HIGH" -> res.getColor(R.color.biashara_amber, ctx.theme)
                    "MEDIUM" -> res.getColor(R.color.biashara_blue, ctx.theme)
                    "LOW" -> res.getColor(R.color.biashara_muted, ctx.theme)
                    else -> res.getColor(R.color.biashara_border, ctx.theme)
                }
            }
        }
    }

    inner class WeeklyVH(
        private val binding: ItemAgentWeeklyReviewCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(model: AgentActionCardUiModel) {
            val action = model.action
            val ctx = binding.root.context
            binding.textHeadline.text = action.headline
            binding.textDetail.text = action.detail

            val stripe = ContextCompat.getColor(
                ctx,
                if (action.status == "EXECUTED") R.color.biashara_success_green else R.color.biashara_blue,
            )
            binding.viewUrgencyStripe.setBackgroundColor(stripe)

            binding.progressExecuting.isVisible = model.isExecuting
            binding.rowButtons.isEnabled = !model.isExecuting

            binding.chipGroupStats.removeAllViews()
            val payload = action.actionPayload
            if (!payload.isNullOrBlank()) {
                runCatching {
                    val env = gson.fromJson(payload, WeeklyPayloadJson::class.java)
                    env.chips.forEach { c ->
                        val chip = Chip(ctx).apply {
                            text = ctx.getString(R.string.agent_weekly_review_chip_format, c.label, c.value)
                            isCheckable = false
                            isClickable = false
                        }
                        binding.chipGroupStats.addView(chip)
                    }
                }
            }
            binding.scrollChips.isVisible = binding.chipGroupStats.childCount > 0

            binding.buttonPrimary.visibility = View.VISIBLE
            binding.buttonPrimary.text = ctx.getString(R.string.agent_action_view)
            binding.buttonPrimary.isEnabled = !model.isExecuting
            binding.buttonPrimary.setOnClickListener { onView(action) }
        }
    }

    private fun emojiForAgentType(agentType: String): String = when (agentType) {
        AgentTypes.STOCK_GUARDIAN -> "📦"
        AgentTypes.FRAUD_SENTINEL -> "🛡️"
        AgentTypes.PRICING_AGENT -> "💡"
        AgentTypes.CASH_FLOW -> "💸"
        AgentTypes.CUSTOMER_RELATION -> "👤"
        AgentTypes.WEEKLY_REVIEW -> "📋"
        AgentTypes.OPPORTUNITY_SPOTTER -> "✨"
        else -> "🤖"
    }

    private object Diff : DiffUtil.ItemCallback<AgentActionCardUiModel>() {
        override fun areItemsTheSame(a: AgentActionCardUiModel, b: AgentActionCardUiModel): Boolean =
            a.action.id == b.action.id

        override fun areContentsTheSame(a: AgentActionCardUiModel, b: AgentActionCardUiModel): Boolean =
            a == b
    }

    companion object {
        private const val VT_DEFAULT = 0
        private const val VT_WEEKLY_REVIEW = 1
        private val gson = Gson()
    }
}
