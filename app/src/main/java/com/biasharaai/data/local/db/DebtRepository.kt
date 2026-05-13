package com.biasharaai.data.local.db

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(
    private val debtDao: DebtDao,
) {
    fun observeTotalOutstandingForCustomer(customerId: Long): Flow<Double> =
        debtDao.observeTotalOutstandingForCustomer(customerId)
}
