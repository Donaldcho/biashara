package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

data class ProductSaleDayRow(
    @ColumnInfo(name = "productId") val productId: Long,
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "totalQty") val totalQty: Long,
)

/** Room reads for [LossAlertEngine] — no side effects. */
@Dao
interface LossAlertDao {

    /**
     * Low stock with no POS sale line recorded since [sinceMillis] (heuristic for unexplained shrinkage).
     */
    @Query(
        """
        SELECT p.* FROM products p
        WHERE p.stock_quantity < :threshold
        AND p.id NOT IN (
            SELECT DISTINCT sl.product_id FROM sale_line_items sl
            INNER JOIN transactions t ON t.id = sl.transaction_id
            WHERE t.type = 'INCOME' AND sl.quantity > 0 AND t.date > :sinceMillis
        )
        """,
    )
    suspend fun getProductsWithUnexplainedStockDrop(threshold: Int, sinceMillis: Long): List<Product>

    /**
     * POS sale lines grouped by product and calendar day (UTC) for sales-gap analysis.
     */
    @Query(
        """
        SELECT sl.product_id AS productId,
            strftime('%Y-%m-%d', t.date / 1000, 'unixepoch') AS day,
            SUM(sl.quantity) AS totalQty
        FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0 AND t.date >= :sinceMillis
        GROUP BY sl.product_id, day
        """,
    )
    suspend fun getProductSaleQuantitiesByDay(sinceMillis: Long): List<ProductSaleDayRow>

    /**
     * Completed POS sales where a line's unit price is below 70% of the product's current list price.
     */
    @Query(
        """
        SELECT DISTINCT t.* FROM transactions t
        INNER JOIN sale_line_items sl ON sl.transaction_id = t.id
        INNER JOIN products p ON p.id = sl.product_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0
        AND sl.unit_price < (p.price * 0.7)
        AND t.date > :sinceMillis
        ORDER BY t.date DESC
        """,
    )
    suspend fun getSalesWithLowLinePrice(sinceMillis: Long): List<Transaction>

    /** All expense rows since [sinceMillis] (for high-expense-day analysis in Kotlin). */
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = 'EXPENSE' AND date >= :sinceMillis
        ORDER BY date ASC
        """,
    )
    suspend fun getExpensesSince(sinceMillis: Long): List<Transaction>
}
