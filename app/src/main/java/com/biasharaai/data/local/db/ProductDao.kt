package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Row type for [ProductDao.getCategoryAverages] — Prompt U3. */
data class CategoryAverages(
    @ColumnInfo(name = "avgPrice") val avgPrice: Double?,
    @ColumnInfo(name = "avgCost") val avgCost: Double?,
)

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product): Long

    /** Bulk insert for receipt OCR flow — Prompt U4. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(products: List<Product>): List<Long>

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    suspend fun getProductsList(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun getProductById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdOnce(id: Long): Product?

    /**
     * Prompt U8 — fuzzy name match for parsed order lines (shortest name first among LIKE hits).
     */
    @Query(
        """
        SELECT * FROM products
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%'
        ORDER BY LENGTH(name) ASC
        LIMIT 1
        """,
    )
    suspend fun findProductByNameFuzzy(query: String): Product?

    @Query("SELECT * FROM products WHERE barcode_value = :barcodeValue LIMIT 1")
    fun getProductByBarcode(barcodeValue: String): Flow<Product?>

    /**
     * POS search — name or barcode contains query (case-insensitive). Max 50 rows.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE LOWER(name) LIKE '%' || LOWER(:q) || '%'
           OR LOWER(IFNULL(barcode_value, '')) LIKE '%' || LOWER(:q) || '%'
        ORDER BY name COLLATE NOCASE ASC
        LIMIT 50
        """,
    )
    fun searchProductsByNameOrBarcode(q: String): Flow<List<Product>>

    /**
     * POS grid — products that appear in recent [sale_line_items] / [transactions] first,
     * then alphabetical for items with no recorded line items yet.
     */
    @Query(
        """
        SELECT p.* FROM products p
        LEFT JOIN (
            SELECT sl.product_id AS pid, MAX(t.date) AS last_sale
            FROM sale_line_items sl
            INNER JOIN transactions t ON t.id = sl.transaction_id
            GROUP BY sl.product_id
        ) x ON x.pid = p.id
        ORDER BY
            CASE WHEN x.last_sale IS NULL THEN 1 ELSE 0 END ASC,
            x.last_sale DESC,
            p.name COLLATE NOCASE ASC
        """,
    )
    fun getProductsOrderedForPos(): Flow<List<Product>>

    @Query("UPDATE products SET stock_quantity = stock_quantity + :qty WHERE id = :id")
    suspend fun incrementStock(id: Long, qty: Int)

    /**
     * Average list price and cost among products in the same category, excluding [excludeId]
     * (use `0L` when the row is not yet inserted). Prompt U3.
     */
    @Query(
        """
        SELECT AVG(price) AS avgPrice, AVG(cost) AS avgCost FROM products
        WHERE category = :category AND id != :excludeId
        """,
    )
    fun getCategoryAverages(category: String, excludeId: Long): Flow<CategoryAverages>

    /**
     * Total units sold (positive sale lines only) for [productId] in [startMillis, endMillisExclusive).
     */
    @Query(
        """
        SELECT IFNULL(SUM(sl.quantity), 0) FROM sale_line_items sl
        INNER JOIN transactions t ON t.id = sl.transaction_id
        WHERE sl.product_id = :productId
          AND t.type = 'INCOME'
          AND sl.quantity > 0
          AND t.date >= :startMillis AND t.date < :endMillisExclusive
        """,
    )
    suspend fun sumUnitsSoldForProductInPeriod(
        productId: Long,
        startMillis: Long,
        endMillisExclusive: Long,
    ): Long
}
