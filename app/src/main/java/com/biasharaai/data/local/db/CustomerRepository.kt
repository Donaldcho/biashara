package com.biasharaai.data.local.db

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * POS / Phase 2 customer persistence — Prompt U2.
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
) {
    fun observeAllCustomers(): Flow<List<Customer>> = customerDao.getAllCustomers()

    fun searchCustomers(query: String): Flow<List<Customer>> = customerDao.searchByName(query)

    suspend fun insertCustomer(name: String, phone: String?): Long {
        val now = System.currentTimeMillis()
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Name required" }
        val p = phone?.trim()?.takeIf { it.isNotEmpty() }
        return customerDao.insertCustomer(
            Customer(
                name = trimmedName,
                phone = p,
                createdAt = now,
                lastVisit = now,
            ),
        )
    }

    suspend fun touchLastVisit(customerId: Long) {
        customerDao.updateLastVisit(customerId, System.currentTimeMillis())
    }

    suspend fun getCustomerById(id: Long): Customer? = customerDao.getCustomerById(id)
}
