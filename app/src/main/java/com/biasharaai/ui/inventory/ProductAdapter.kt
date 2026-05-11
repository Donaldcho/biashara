package com.biasharaai.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.ItemProductBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * RecyclerView adapter for the inventory product list.
 *
 * Uses [ListAdapter] with [DiffUtil] for efficient, animated list updates
 * when the underlying [Product] list changes.
 *
 * @param onItemClick callback invoked when a product item is tapped (for edit navigation).
 */
class ProductAdapter(
    private val onItemClick: (Product) -> Unit,
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
                R.string.inventory_stock_format,
                product.stockQuantity,
            )

            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
            binding.textPrice.text = currencyFormat.format(product.price)

            binding.iconBarcode.visibility = if (product.barcodeValue != null) {
                View.VISIBLE
            } else {
                View.GONE
            }

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
        }
    }

    private object ProductDiffCallback : DiffUtil.ItemCallback<Product>() {

        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem == newItem
    }
}
