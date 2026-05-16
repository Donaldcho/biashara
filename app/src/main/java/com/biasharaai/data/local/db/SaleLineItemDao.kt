package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Row for analytics: POS line + parent transaction fields (Prompt: conversational query layer). */
data class PosSaleLineFact(
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    val quantity: Int,
    @ColumnInfo(name = "line_total") val lineTotal: Double,
    @ColumnInfo(name = "tx_date") val transactionDate: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "customer_id")     val customerId: Long?,
)

/** Row for A7 co-purchase mining ([SaleLineItemDao.getTopCoPurchasePairs]). */
data class CoPurchasePair(
    @ColumnInfo(name = "product1") val product1: String,
    @ColumnInfo(name = "product2") val product2: String,
    @ColumnInfo(name = "coCount") val coCount: Int,
)

@Dao
interface SaleLineItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineItem(item: SaleLineItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineItems(items: List<SaleLineItem>): List<Long>

    @Query("SELECT * FROM sale_line_items WHERE transaction_id = :transactionId ORDER BY id ASC")
    fun getLineItemsByTransaction(transactionId: Long): Flow<List<SaleLineItem>>

    @Query("SELECT * FROM sale_line_items WHERE transaction_id = :transactionId ORDER BY id ASC")
    suspend fun getLineItemsForTransactionOnce(transactionId: Long): List<SaleLineItem>

    /**
     * Products this customer bought on **more than one** distinct sale (INCOME receipt),
     * ordered by total units, top [limit]. Prompt U2.
     */
    @Query(
        """
        SELECT sl.product_id FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        WHERE t.customer_id = :customerId AND t.type = 'INCOME' AND sl.quantity > 0
        GROUP BY sl.product_id
        HAVING COUNT(DISTINCT sl.transaction_id) > 1
        ORDER BY SUM(sl.quantity) DESC
        LIMIT :limit
        """,
    )
    suspend fun topProductIdsForCustomer(customerId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT * FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0 AND t.date >= :startMillis AND t.date <= :endMillis
        """,
    )
    suspend fun saleLinesInPeriod(startMillis: Long, endMillis: Long): List<SaleLineItem>

    /**
     * Positive POS lines since [sinceMillis] with transaction date and payment method for analytics.
     */
    @Query(
        """
        SELECT sl.transaction_id AS transaction_id, sl.product_id AS product_id,
            sl.product_name AS product_name, sl.unit_price AS unit_price, sl.quantity AS quantity,
            sl.line_total AS line_total, t.date AS tx_date, t.payment_method AS payment_method,
            t.customer_id AS customer_id
        FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0 AND t.date >= :sinceMillis
        """,
    )
    suspend fun posSaleLineFactsSince(sinceMillis: Long): List<PosSaleLineFact>

    /**
     * Return rows use negative [SaleLineItem.quantity] and set [SaleLineItem.sourceSaleLineItemId].
     */
    @Query(
        """
        SELECT IFNULL(ABS(SUM(quantity)), 0) FROM sale_line_items
        WHERE source_sale_line_item_id = :originalLineItemId
        """,
    )
    suspend fun sumReturnedQuantityForOriginalLine(originalLineItemId: Long): Int

    @Query("DELETE FROM sale_line_items WHERE transaction_id = :transactionId")
    suspend fun deleteByTransaction(transactionId: Long)

    /** Full export for optional cloud analytics (user-initiated). */
    @Query("SELECT * FROM sale_line_items ORDER BY id ASC")
    suspend fun getAllLineItems(): List<SaleLineItem>

    // ── Opportunity spotter (A7) — co-purchase pairs on completed POS sales ─────────────

    /**
     * Product pairs sold together on the same INCOME receipt at least [minCoCount] times since [sinceMillis].
     */
    @Query(
        """
        SELECT a.product_name AS product1, b.product_name AS product2, COUNT(*) AS coCount
        FROM sale_line_items a
        INNER JOIN sale_line_items b
            ON a.transaction_id = b.transaction_id AND a.product_id < b.product_id
        INNER JOIN transactions t ON t.id = a.transaction_id
        WHERE t.type = 'INCOME' AND a.quantity > 0 AND b.quantity > 0 AND t.date >= :sinceMillis
        GROUP BY a.product_id, b.product_id
        HAVING COUNT(*) >= :minCoCount
        ORDER BY COUNT(*) DESC
        LIMIT 5
        """,
    )
    suspend fun getTopCoPurchasePairs(sinceMillis: Long, minCoCount: Int = 3): List<CoPurchasePair>

    /** Phase 6 X5 — gross margin on POS lines in a date window (cost from [products]). */
    @Query(
        """
        SELECT IFNULL(SUM((sl.unit_price - IFNULL(p.cost, 0)) * sl.quantity), 0)
        FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        LEFT JOIN products p ON p.id = sl.product_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0
          AND t.date >= :startMillis AND t.date < :endExclusiveMillis
        """,
    )
    suspend fun sumGrossProfitBetween(startMillis: Long, endExclusiveMillis: Long): Double

    @Query(
        """
        SELECT IFNULL(SUM(sl.line_total), 0)
        FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0
          AND t.date >= :startMillis AND t.date < :endExclusiveMillis
        """,
    )
    suspend fun sumPosRevenueBetween(startMillis: Long, endExclusiveMillis: Long): Double
}
