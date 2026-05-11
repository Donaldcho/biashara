package com.biasharaai.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [TransactionDao] using an in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        transactionDao = database.transactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Insert & Retrieve All ───────────────────────────────────────────

    @Test
    fun insertTransaction_andGetAll_returnsInserted() = runTest {
        val txn = Transaction(
            type = TransactionType.INCOME,
            amount = 5000.0,
            description = "Sales",
            date = 1_700_000_000_000L,
        )
        val id = transactionDao.insertTransaction(txn)
        assertTrue("Insert should return positive ID", id > 0)

        val all = transactionDao.getAllTransactions().first()
        assertEquals(1, all.size)
        assertEquals(TransactionType.INCOME, all[0].type)
        assertEquals(5000.0, all[0].amount, 0.01)
        assertEquals("Sales", all[0].description)
    }

    // ── Date Ordering ───────────────────────────────────────────────────

    @Test
    fun getAllTransactions_orderedByDateDesc() = runTest {
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.INCOME, amount = 100.0, description = "A", date = 1_000L),
        )
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.EXPENSE, amount = 200.0, description = "B", date = 3_000L),
        )
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.INCOME, amount = 300.0, description = "C", date = 2_000L),
        )

        val all = transactionDao.getAllTransactions().first()
        assertEquals(listOf("B", "C", "A"), all.map { it.description })
    }

    // ── Period Filtering ────────────────────────────────────────────────

    @Test
    fun getTransactionsByPeriod_filtersCorrectly() = runTest {
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.INCOME, amount = 100.0, description = "Before", date = 500L),
        )
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.INCOME, amount = 200.0, description = "In range 1", date = 1_000L),
        )
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.EXPENSE, amount = 150.0, description = "In range 2", date = 1_500L),
        )
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.EXPENSE, amount = 300.0, description = "After", date = 3_000L),
        )

        val inRange = transactionDao.getTransactionsByPeriod(1_000L, 2_000L).first()
        assertEquals(2, inRange.size)
        assertTrue(inRange.all { it.date in 1_000L..2_000L })
    }

    @Test
    fun getTransactionsByPeriod_emptyRange_returnsEmpty() = runTest {
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.INCOME, amount = 100.0, description = "Outside", date = 500L),
        )

        val result = transactionDao.getTransactionsByPeriod(1_000L, 2_000L).first()
        assertTrue(result.isEmpty())
    }

    // ── Type Filtering (manual) ─────────────────────────────────────────

    @Test
    fun transactions_correctlyStoreType() = runTest {
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.INCOME, amount = 500.0, description = "Sales", date = 1_000L),
        )
        transactionDao.insertTransaction(
            Transaction(type = TransactionType.EXPENSE, amount = 200.0, description = "Rent", date = 1_000L),
        )

        val all = transactionDao.getAllTransactions().first()
        val income = all.filter { it.type == TransactionType.INCOME }
        val expense = all.filter { it.type == TransactionType.EXPENSE }

        assertEquals(1, income.size)
        assertEquals(1, expense.size)
        assertEquals(500.0, income[0].amount, 0.01)
        assertEquals(200.0, expense[0].amount, 0.01)
    }
}
