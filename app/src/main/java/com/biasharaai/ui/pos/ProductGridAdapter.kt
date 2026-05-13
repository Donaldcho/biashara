package com.biasharaai.ui.pos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.biasharaai.R
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.ItemProductGridBinding
import java.io.File
import kotlin.random.Random

class ProductGridAdapter(
    private val onProductClick: (Product) -> Unit,
    private val onProductLongClick: (Product) -> Unit,
    private val formatMoney: (Double) -> String,
) : ListAdapter<Product, ProductGridAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemProductGridBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = this@VH.layoutPosition
                if (pos != RecyclerView.NO_POSITION) onProductClick(getItem(pos))
            }
            binding.root.setOnLongClickListener {
                val pos = this@VH.layoutPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onProductLongClick(getItem(pos))
                    true
                } else {
                    false
                }
            }
        }

        fun bind(product: Product) {
            binding.textName.text = product.name
            binding.textPrice.text = formatMoney(product.price)
            val stock = product.stockQuantity
            binding.textStock.text = binding.root.context.getString(R.string.pos_stock_badge, stock)
            val low = stock < 5
            binding.textStock.setBackgroundResource(
                if (low) R.drawable.bg_stock_low else R.drawable.bg_stock_ok,
            )
            binding.textInitial.text = product.name.trim().take(1).uppercase().ifEmpty { "?" }
            val hue = stableHue(product.id)
            binding.textInitial.setBackgroundColor(
                Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.92f)),
            )
            binding.textInitial.setTextColor(Color.HSVToColor(floatArrayOf(hue, 0.75f, 0.25f)))
            bindProductThumb(product)
        }

        private fun bindProductThumb(product: Product) {
            val url = product.imageUrl
            val hasLocal = !url.isNullOrBlank() &&
                !url.startsWith("http", ignoreCase = true) &&
                File(url).isFile
            val hasRemote = !url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)
            if (hasLocal) {
                binding.imageThumb.visibility = View.VISIBLE
                binding.textInitial.visibility = View.GONE
                binding.imageThumb.load(File(url!!)) {
                    crossfade(true)
                }
            } else if (hasRemote) {
                binding.imageThumb.visibility = View.VISIBLE
                binding.textInitial.visibility = View.GONE
                binding.imageThumb.load(url!!) {
                    crossfade(true)
                }
            } else {
                binding.imageThumb.setImageDrawable(null)
                binding.imageThumb.visibility = View.GONE
                binding.textInitial.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem == newItem
        }

        private fun stableHue(seed: Long): Float {
            val r = Random(seed)
            return r.nextFloat() * 360f
        }
    }
}
