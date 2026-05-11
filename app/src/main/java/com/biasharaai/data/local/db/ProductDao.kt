package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product): Long

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

    @Query("SELECT * FROM products WHERE barcode_value = :barcodeValue LIMIT 1")
    fun getProductByBarcode(barcodeValue: String): Flow<Product?>
}
