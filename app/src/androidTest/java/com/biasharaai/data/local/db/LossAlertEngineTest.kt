package com.biasharaai.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class LossAlertEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: LossAlertEngine

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        engine = LossAlertEngine(db.lossAlertDao(), db.productDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun stockShrinkage_firesWhenStockBelowThresholdAndNoRecentPosSale() = runTest {
        val pid = db.productDao().insertProduct(
            Product(name = "Chips pack", price = 1.0, cost = 0.5, stockQuantity = 3),
        )
        val now = System.currentTimeMillis()
        val alerts = engine.getStockShrinkageAlerts(now)
        assertTrue(alerts.any { it.alertType == LossAlertTypes.SHRINKAGE && it.productId == pid })
    }

    @Test
    fun salesGap_firesAfterSevenConsecutiveSaleDaysThenThreeQuietDays() = runTest {
        val pid = db.productDao().insertProduct(
            Product(name = "Soda 500ml", price = 2.0, cost = 1.0, stockQuantity = 50),
        )
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 5, 11)
        val nowMillis = today.atTime(12, 0).toInstant(zone).toEpochMilli()

        var d = LocalDate.of(2026, 4, 29)
        val end = LocalDate.of(2026, 5, 5)
        while (!d.isAfter(end)) {
            val ms = d.atTime(12, 0).toInstant(zone).toEpochMilli()
            val txId = db.transactionDao().insertTransaction(
                Transaction(
                    type = TransactionType.INCOME,
                    amount = 10.0,
                    description = "POS",
                    date = ms,
                ),
            )
            db.saleLineItemDao().insertLineItem(
                SaleLineItem(
                    transactionId = txId,
                    productId = pid,
                    productName = "Soda 500ml",
                    unitPrice = 2.0,
                    quantity = 1,
                    lineTotal = 2.0,
                ),
            )
            d = d.plusDays(1)
        }

        val alerts = engine.getSalesGapAlerts(nowMillis, zone)
        assertTrue(alerts.any { it.alertType == LossAlertTypes.SALES_GAP && it.productId == pid })
    }
}
