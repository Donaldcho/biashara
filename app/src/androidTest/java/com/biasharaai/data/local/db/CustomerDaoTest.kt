package com.biasharaai.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var customerDao: CustomerDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        customerDao = database.customerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertCustomer_getById_returnsRow() = runTest {
        val id = customerDao.insertCustomer(
            Customer(name = "Amina Hassan", phone = "+254700", createdAt = 1L),
        )
        assertTrue(id > 0)
        val row = customerDao.getCustomerById(id)
        assertNotNull(row)
        assertEquals("Amina Hassan", row!!.name)
        assertEquals("+254700", row.phone)
    }

    @Test
    fun searchByName_partialMatch_isCaseInsensitive() = runTest {
        customerDao.insertCustomer(Customer(name = "John Mwangi", createdAt = 1L))
        customerDao.insertCustomer(Customer(name = "Peter Otieno", createdAt = 2L))

        val mwangi = customerDao.searchByName("mwang").first()
        assertEquals(1, mwangi.size)
        assertEquals("John Mwangi", mwangi[0].name)

        val j = customerDao.searchByName("JOHN").first()
        assertEquals(1, j.size)
    }

    @Test
    fun getCustomerById_unknown_returnsNull() = runTest {
        assertNull(customerDao.getCustomerById(999L))
    }
}
