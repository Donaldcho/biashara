package com.biasharaai.cash

import com.biasharaai.data.local.db.CashMovementEvidence
import com.biasharaai.data.local.db.CashMovementEvidenceDao
import com.biasharaai.data.local.db.CaptureMethod
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.ProofType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashEvidenceAnomalyDetectorTest {

    private val evidenceDao: CashMovementEvidenceDao = mockk()
    private val ledgerEntryDao: LedgerEntryDao = mockk()
    private val detector = CashEvidenceAnomalyDetector(evidenceDao, ledgerEntryDao)

    @Test
    fun noAnomalies_whenNoSignals() = runBlocking {
        coEvery { evidenceDao.getLargeUnverifiedOutflows(any(), any()) } returns emptyList()
        coEvery { evidenceDao.countUnverifiedOutflowsSince(any()) } returns 0

        val signals = detector.detectAll()
        assertTrue(signals.isEmpty())
    }

    @Test
    fun largeUnverifiedOutflow_generatesHighSeveritySignal() = runBlocking {
        val evidence = CashMovementEvidence(
            id = 1L,
            ledgerEntryId = 10L,
            captureMethod = CaptureMethod.MANUAL,
            proofType = ProofType.UNKNOWN,
            parsedAmount = 8000.0,
            parsedReference = null,
        )
        coEvery { evidenceDao.getLargeUnverifiedOutflows(any(), any()) } returns listOf(evidence)
        coEvery { evidenceDao.countUnverifiedOutflowsSince(any()) } returns 1

        val signals = detector.detectAll()
        val highSeverity = signals.filter { it.severity == CashAnomalySignal.Severity.HIGH }
        assertEquals(1, highSeverity.size)
        assertEquals("LARGE_UNVERIFIED_OUTFLOW", highSeverity.first().rule)
    }

    @Test
    fun repeatedUnverifiedOutflows_generatesMediumSignal() = runBlocking {
        coEvery { evidenceDao.getLargeUnverifiedOutflows(any(), any()) } returns emptyList()
        coEvery { evidenceDao.countUnverifiedOutflowsSince(any()) } returns 5

        val signals = detector.detectAll()
        val medium = signals.filter { it.severity == CashAnomalySignal.Severity.MEDIUM }
        assertEquals(1, medium.size)
        assertEquals("REPEATED_UNVERIFIED_OUTFLOWS", medium.first().rule)
    }

    @Test
    fun belowThreshold_noMediumSignal() = runBlocking {
        coEvery { evidenceDao.getLargeUnverifiedOutflows(any(), any()) } returns emptyList()
        coEvery { evidenceDao.countUnverifiedOutflowsSince(any()) } returns 2

        val signals = detector.detectAll()
        assertTrue(signals.none { it.severity == CashAnomalySignal.Severity.MEDIUM })
    }
}
