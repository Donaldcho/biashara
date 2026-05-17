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
class AlertDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var alertDao: AlertDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        alertDao = database.alertDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertActive_getActiveAlerts_includesRow() = runTest {
        val id = alertDao.insert(
            Alert(
                title = "Low stock",
                message = "Check shelf",
                severity = "WARN",
                createdAt = 5L,
                read = false,
                alertType = LossAlertTypes.SHRINKAGE,
            ),
        )
        assertTrue(id > 0)
        val active = alertDao.getActiveAlerts().first()
        assertEquals(1, active.size)
        assertEquals("Low stock", active[0].title)
    }

    @Test
    fun dismissAlert_excludesFromActiveAlerts() = runTest {
        val id = alertDao.insert(
            Alert(
                title = "T",
                message = "M",
                createdAt = 1L,
                read = false,
            ),
        )
        assertEquals(1, alertDao.getActiveAlerts().first().size)

        alertDao.dismissAlert(id)

        assertEquals(0, alertDao.getActiveAlerts().first().size)
    }
}
