package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerContextDao
import com.biasharaai.data.local.db.LedgerContextSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerContextRepositoryTest {

    @Test
    fun recordAgentInference_skipsWhenOwnerConfirmedExists() = runTest {
        val dao = mockk<LedgerContextDao>()
        coEvery { dao.getActiveForAnomaly("a1") } returns listOf(
            com.biasharaai.data.local.db.LedgerContext(
                relatedAnomalyId = "a1",
                contextType = "EXPENSE_SPIKE",
                prompt = "Why?",
                ownerAnswer = "Planned purchase",
                source = LedgerContextSource.OWNER_CONFIRMED.name,
            ),
        )

        val id = LedgerContextRepository(dao).recordAgentInference(
            relatedAnomalyId = "a1",
            contextType = "EXPENSE_SPIKE",
            prompt = "Why?",
            inference = "Maybe stock",
        )

        assertNull(id)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun recordOwnerAnswer_supersedesPriorOwnerRows() = runTest {
        val dao = mockk<LedgerContextDao>()
        coEvery { dao.supersedeOwnerConfirmedForAnomaly(any(), any()) } returns Unit
        coEvery { dao.insert(any()) } returns 42L

        val id = LedgerContextRepository(dao).recordOwnerAnswer(
            relatedAnomalyId = "a1",
            contextType = "EXPENSE_SPIKE",
            prompt = "Was this planned?",
            ownerAnswer = "Planned purchase",
        )

        assertEquals(42L, id)
        coVerify { dao.supersedeOwnerConfirmedForAnomaly("a1", any()) }
    }
}
