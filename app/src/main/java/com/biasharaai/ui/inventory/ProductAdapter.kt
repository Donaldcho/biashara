package com.biasharaai.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.biasharaai.R
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.ItemProductBinding
import com.biasharaai.money.MoneyFormatter
import java.io.File

/**
 * RecyclerView adapter for the inventory product list.
 *
 * Uses [ListAdapter] with [DiffUtil] for efficient, animated list updates
 * when the underlying [Product] list changes.
 *
 * @param onItemClick callback invoked when a product item is tapped (for edit navigation).
 * @param onItemLongClick callback for overflow actions (remove stock, delete); return true to consume.
 */
class ProductAdapter(
    private val moneyFormatter: MoneyFormatter,
    private val onItemClick: (Product) -> Unit,
    private val onItemLongClick: (Product, View) -> Boolean,
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback) {

    /** Map of product ID → forecast string. Updated externally by the fragment. */
    private var forecasts: Map<Long, String> = emptyMap()

    /**
     * Submit forecast data. Call this after (or alongside) [submitList].
     */
    fun submitForecasts(newForecasts: Map<Long, String>) {
        forecasts = newForecasts
        notifyDataSetChanged() // Forecasts are supplementary; full rebind is acceptable
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position), forecasts[getItem(position).id])
    }

    inner class ProductViewHolder(
        private val binding: ItemProductBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, forecast: String?) {
            binding.textProductName.text = product.name

            val context = binding.root.context
            binding.textStockQuantity.text = context.getString(
                R.string.inventory_catalog_stock,
                product.stockQuantity,
            )

            val cat = product.category?.trim().orEmpty()
            if (cat.isNotEmpty()) {
                binding.textCategory.visibility = View.VISIBLE
                binding.textCategory.text = cat
            } else {
                binding.textCategory.visibility = View.GONE
            }

            binding.textPrice.text = moneyFormatter.format(product.price)

            binding.iconBarcode.visibility = if (product.barcodeValue != null) {
                View.VISIBLE
            } else {
                View.GONE
            }

            bindProductThumb(product)

            // Forecast badge
            if (!forecast.isNullOrBlank()) {
                binding.textForecast.text = context.getString(
                    R.string.inventory_forecast_badge,
                    forecast,
                )
                binding.textForecast.visibility = View.VISIBLE
            } else {
                binding.textForecast.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(product) }
            binding.root.setOnLongClickListener { onItemLongClick(product, it) }
        }

        private fun bindProductThumb(product: Product) {
            val url = product.imageUrl
            val hasLocal = !url.isNullOrBlank() &&
                !url.startsWith("http", ignoreCase = true) &&
                File(url).isFile
            val hasRemote = !url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)
            if (hasLocal) {
                binding.imageThumb.background = null
                binding.imageThumb.load(File(url!!)) {
                    crossfade(true)
                    placeholder(R.drawable.bg_product_thumb)
                    error(R.drawable.bg_product_thumb)
                }
            } else if (hasRemote) {
                binding.imageThumb.background = null
                binding.imageThumb.load(url!!) {
                    crossfade(true)
                    placeholder(R.drawable.bg_product_thumb)
                    error(R.drawable.bg_product_thumb)
                }
            } else {
                binding.imageThumb.setImageDrawable(null)
                binding.imageThumb.setBackgroundResource(R.drawable.bg_product_thumb)
            }
        }
    }

    private object ProductDiffCallback : DiffUtil.ItemCallback<Product>() {

        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem == newItem
    }
}
