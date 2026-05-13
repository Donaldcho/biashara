package com.biasharaai.ui.pos

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
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
import kotlin.math.abs

class CartAdapter(
    private val cartManager: CartManager,
    private val formatMoney: (Double) -> String,
    private var allowPriceOverride: Boolean,
    private val onRequestPriceOverride: (CartItem) -> Unit,
) : ListAdapter<CartItem, CartAdapter.CartViewHolder>(DIFF) {

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

        fun bind(item: CartItem) {
            val p = item.product
            binding.textProductName.text = p.name
            binding.textQuantity.text = item.quantity.toString()
            binding.textLineTotal.text = formatMoney(item.lineTotal)

            val override = item.overridePrice
            val catalog = p.price
            val effective = item.effectivePrice

            if (override != null) {
                binding.textOriginalUnitPrice.visibility = android.view.View.VISIBLE
                binding.textOriginalUnitPrice.text = formatMoney(catalog)
                binding.textOriginalUnitPrice.paintFlags =
                    binding.textOriginalUnitPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.textEffectiveUnitPrice.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.pos_price_override),
                )
            } else {
                binding.textOriginalUnitPrice.visibility = android.view.View.GONE
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

            binding.textEffectiveUnitPrice.setOnClickListener(null)
            if (allowPriceOverride) {
                binding.textEffectiveUnitPrice.isClickable = true
                binding.textEffectiveUnitPrice.setOnClickListener {
                    onRequestPriceOverride(item)
                }
            } else {
                binding.textEffectiveUnitPrice.isClickable = false
            }

            binding.btnQtyMinus.setOnClickListener {
                cartManager.updateQuantity(p.id, item.quantity - 1)
            }
            binding.btnQtyPlus.setOnClickListener {
                cartManager.updateQuantity(p.id, item.quantity + 1)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
                oldItem.product.id == newItem.product.id

            override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
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
                            val item = adapter.currentList.getOrNull(pos) ?: return
                            cartManager.removeItem(item.product.id)
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
