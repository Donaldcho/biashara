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

@RunWith(AndroidJUnit4::class)
class DebtDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var customerDao: CustomerDao
    private lateinit var debtDao: DebtDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        customerDao = database.customerDao()
        debtDao = database.debtDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertDebt_getDebtsByCustomer_andOutstanding() = runTest {
        val cid = customerDao.insertCustomer(Customer(name = "Buyer", createdAt = 1L))
        debtDao.insertDebt(
            Debt(customerId = cid, amount = 100.0, description = "Credit sale", createdAt = 2L),
        )
        debtDao.insertDebt(
            Debt(customerId = cid, amount = 50.0, description = "Top-up", createdAt = 3L),
        )

        val list = debtDao.getDebtsByCustomer(cid).first()
        assertEquals(2, list.size)

        val total = debtDao.getTotalOutstanding().first()
        assertEquals(150.0, total, 0.001)
    }

    @Test
    fun markPaid_zeroesAmount_excludedFromOutstanding() = runTest {
        val cid = customerDao.insertCustomer(Customer(name = "Buyer", createdAt = 1L))
        val did = debtDao.insertDebt(
            Debt(customerId = cid, amount = 40.0, description = "Tab", createdAt = 2L),
        )
        assertEquals(40.0, debtDao.getTotalOutstanding().first(), 0.001)

        val updated = debtDao.markPaid(did)
        assertEquals(1, updated)
        assertEquals(0.0, debtDao.getTotalOutstanding().first(), 0.001)
    }

    @Test
    fun getDebtsByCustomer_ordersByCreatedDesc() = runTest {
        val cid = customerDao.insertCustomer(Customer(name = "C", createdAt = 1L))
        debtDao.insertDebt(Debt(customerId = cid, amount = 1.0, description = "Old", createdAt = 10L))
        val idNew = debtDao.insertDebt(Debt(customerId = cid, amount = 2.0, description = "New", createdAt = 99L))

        val list = debtDao.getDebtsByCustomer(cid).first()
        assertTrue(list.first().id == idNew)
    }
}
