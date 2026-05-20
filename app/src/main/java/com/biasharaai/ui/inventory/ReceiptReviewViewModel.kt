package com.biasharaai.ui.inventory

import androidx.lifecycle.ViewModel
import com.biasharaai.data.local.db.EnterpriseStockMovement
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.enterprise.EnterpriseCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.roundToInt
import javax.inject.Inject

@HiltViewModel
class ReceiptReviewViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val enterpriseCatalogRepository: EnterpriseCatalogRepository,
) : ViewModel() {

    /**
     * Inserts one [Product] per complete line. Skips blank-name rows.
     * @return number of products inserted, or failure message.
     */
    suspend fun insertParsedLines(lines: List<ReceiptDraftLine>): Result<Int> {
        val toInsert = mutableListOf<Product>()
        for (line in lines) {
            if (!line.isCompleteForSave()) continue
            val qty = line.quantityText.toDoubleOrNull() ?: continue
            val cost = line.costText.toDoubleOrNull() ?: continue
            val stock = qty.roundToInt().coerceAtLeast(0)
            val price = if (cost > 0.0) cost * 1.35 else 0.0
            val draft = Product(
                id = 0L,
                name = line.name.trim(),
                description = null,
                price = price,
                cost = cost,
                stockQuantity = stock,
                category = null,
                barcodeValue = null,
            )
            toInsert.add(
                enterpriseCatalogRepository.prepareProductForLocalSave(
                    existing = null,
                    draft = draft,
                ),
            )
        }
        if (toInsert.isEmpty()) {
            return Result.failure(IllegalStateException("no_valid_lines"))
        }
        val ids = productDao.insertAll(toInsert)
        toInsert.zip(ids).forEach { (product, id) ->
            val saved = product.copy(id = id)
            enterpriseCatalogRepository.onProductSaved(
                product = saved,
                changeType = "CREATE",
            )
            enterpriseCatalogRepository.recordProductStockMovement(
                product = saved,
                quantityDelta = saved.stockQuantity,
                movementType = EnterpriseStockMovement.TYPE_STOCK_RECEIPT,
                sourceType = EnterpriseStockMovement.SOURCE_RECEIPT_IMPORT,
                sourceId = id.toString(),
                note = "Receipt import",
            )
        }
        return Result.success(toInsert.size)
    }
}
