package com.biasharaai.ui.pos

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.databinding.ItemCartLineBinding
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.pos.cart.PosCartLine
import com.biasharaai.service.ServiceCartLine
import kotlin.math.abs

class CartAdapter(
    private val cartManager: CartManager,
    private val formatMoney: (Double) -> String,
    private var allowPriceOverride: Boolean,
    private val onRequestProductPriceOverride: (CartItem) -> Unit,
    private val onRequestServicePriceOverride: (ServiceCartLine) -> Unit,
) : ListAdapter<PosCartLine, CartAdapter.CartViewHolder>(DIFF) {

    fun setAllowPriceOverride(allow: Boolean) {
        allowPriceOverride = allow
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CartViewHolder(
        private val binding: ItemCartLineBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: PosCartLine) {
            binding.textProductName.text = line.displayName
            binding.textQuantity.text = line.quantity.toString()
            binding.textLineTotal.text = formatMoney(line.lineTotal)

            if (line.subtitle.isNullOrBlank()) {
                binding.textCartSubtitle.visibility = View.GONE
            } else {
                binding.textCartSubtitle.visibility = View.VISIBLE
                binding.textCartSubtitle.text = line.subtitle
            }

            val catalog: Double
            val effective: Double
            val override: Double?
            when (line) {
                is PosCartLine.Product -> {
                    catalog = line.item.product.price
                    effective = line.item.effectivePrice
                    override = line.item.overridePrice
                }
                is PosCartLine.Service -> {
                    catalog = line.line.service.basePrice
                    effective = line.line.effectivePrice
                    override = line.line.overridePrice
                }
                is PosCartLine.Voucher -> {
                    catalog = line.item.pricePerUse
                    effective = line.item.pricePerUse
                    override = null
                }
            }

            if (override != null) {
                binding.textOriginalUnitPrice.visibility = View.VISIBLE
                binding.textOriginalUnitPrice.text = formatMoney(catalog)
                binding.textOriginalUnitPrice.paintFlags =
                    binding.textOriginalUnitPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.textEffectiveUnitPrice.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.pos_price_override),
                )
            } else {
                binding.textOriginalUnitPrice.visibility = View.GONE
                binding.textOriginalUnitPrice.paintFlags =
                    binding.textOriginalUnitPrice.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.textEffectiveUnitPrice.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        binding.textEffectiveUnitPrice,
                        com.google.android.material.R.attr.colorOnSurface,
                        android.graphics.Color.BLACK,
                    ),
                )
            }
            binding.textEffectiveUnitPrice.text = formatMoney(effective)

            val canOverride = allowPriceOverride && line.allowsPriceOverride
            binding.textEffectiveUnitPrice.setOnClickListener(null)
            if (canOverride) {
                binding.textEffectiveUnitPrice.isClickable = true
                binding.textEffectiveUnitPrice.setOnClickListener {
                    when (line) {
                        is PosCartLine.Product -> onRequestProductPriceOverride(line.item)
                        is PosCartLine.Service -> onRequestServicePriceOverride(line.line)
                        is PosCartLine.Voucher -> Unit
                    }
                }
            } else {
                binding.textEffectiveUnitPrice.isClickable = false
            }

            val qtyControlsVisible = line !is PosCartLine.Voucher
            binding.btnQtyMinus.visibility = if (qtyControlsVisible) View.VISIBLE else View.INVISIBLE
            binding.btnQtyPlus.visibility = if (qtyControlsVisible) View.VISIBLE else View.INVISIBLE
            binding.textQuantity.visibility = if (qtyControlsVisible) View.VISIBLE else View.INVISIBLE

            binding.btnQtyMinus.setOnClickListener {
                when (line) {
                    is PosCartLine.Product -> cartManager.updateQuantity(line.item.product.id, line.quantity - 1)
                    is PosCartLine.Service -> cartManager.updateServiceQuantity(line.line.service.id, line.quantity - 1)
                    is PosCartLine.Voucher -> Unit
                }
            }
            binding.btnQtyPlus.setOnClickListener {
                when (line) {
                    is PosCartLine.Product -> cartManager.updateQuantity(line.item.product.id, line.quantity + 1)
                    is PosCartLine.Service -> cartManager.updateServiceQuantity(line.line.service.id, line.quantity + 1)
                    is PosCartLine.Voucher -> Unit
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PosCartLine>() {
            override fun areItemsTheSame(oldItem: PosCartLine, newItem: PosCartLine): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: PosCartLine, newItem: PosCartLine): Boolean =
                oldItem == newItem
        }

        fun attachSwipeToRemove(
            recyclerView: RecyclerView,
            adapter: CartAdapter,
            cartManager: CartManager,
        ) {
            val deleteColor = ContextCompat.getColor(recyclerView.context, R.color.pos_swipe_delete_bg)
            val background = ColorDrawable(deleteColor)
            val helper = ItemTouchHelper(
                object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ): Boolean = false

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val pos = viewHolder.layoutPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            val line = adapter.currentList.getOrNull(pos) ?: return
                            cartManager.removeLine(line)
                        }
                    }

                    override fun onChildDraw(
                        c: Canvas,
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        dX: Float,
                        dY: Float,
                        actionState: Int,
                        isCurrentlyActive: Boolean,
                    ) {
                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                            val itemView = viewHolder.itemView
                            background.setBounds(
                                itemView.right + dX.toInt(),
                                itemView.top,
                                itemView.right,
                                itemView.bottom,
                            )
                            background.draw(c)
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }

                    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.35f

                    override fun getSwipeEscapeVelocity(defaultValue: Float): Float =
                        abs(defaultValue) * 4f
                },
            )
            helper.attachToRecyclerView(recyclerView)
        }
    }
}
