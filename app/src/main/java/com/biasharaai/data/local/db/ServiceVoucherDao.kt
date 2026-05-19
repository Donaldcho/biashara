package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ServiceVoucherDao {
    @Query("SELECT * FROM service_vouchers WHERE voucher_id = :voucherId LIMIT 1")
    suspend fun getByVoucherId(voucherId: String): ServiceVoucher?

    @Query(
        """
        SELECT voucher_id FROM service_vouchers
        WHERE source_transaction_id = :transactionId
        ORDER BY id ASC
        """,
    )
    suspend fun getVoucherIdsBySourceTransaction(transactionId: Long): List<String>

    @Query(
        """
        SELECT * FROM service_vouchers
        WHERE remaining_uses > 0
          AND (expires_at IS NULL OR expires_at > :nowMillis)
        ORDER BY expires_at ASC
        """,
    )
    suspend fun getActiveVouchers(nowMillis: Long = System.currentTimeMillis()): List<ServiceVoucher>

    @Query(
        """
        SELECT * FROM service_vouchers
        WHERE remaining_uses > 0
          AND expires_at IS NOT NULL
          AND expires_at > :nowMillis
          AND expires_at <= :withinMillis
        """,
    )
    suspend fun getExpiringSoon(nowMillis: Long, withinMillis: Long): List<ServiceVoucher>

    @Query("SELECT COUNT(*) FROM service_vouchers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(voucher: ServiceVoucher): Long

    @Update
    suspend fun update(voucher: ServiceVoucher)
}
