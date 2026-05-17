package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.FeatureMasteryDao
import com.biasharaai.data.local.db.MasteryLevel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureMasteryRepositoryTest {

    private lateinit var dao: FeatureMasteryDao
    private lateinit var repo: FeatureMasteryRepository

    private val now = System.currentTimeMillis()

    private fun mastery(featureId: String, level: MasteryLevel, practiceCount: Int = 0) =
        FeatureMastery(
            featureId = featureId,
            masteryLevel = level.name,
            firstSeenAt = now,
            lastPracticedAt = now,
            practiceCount = practiceCount,
        )

    @Before
    fun setUp() {
        dao = mockk()
        repo = FeatureMasteryRepository(dao)
    }

    @Test
    fun getMasteryLevel_noRecord_returnsUndiscovered() = runTest {
        coEvery { dao.getByFeature("unknown_feature") } returns null
        val level = repo.getMasteryLevel("unknown_feature")
        assertEquals(MasteryLevel.UNDISCOVERED, level)
    }

    @Test
    fun getMasteryLevel_withRecord_returnsCorrectLevel() = runTest {
        coEvery { dao.getByFeature("pos_sale") } returns mastery("pos_sale", MasteryLevel.LEARNING)
        val level = repo.getMasteryLevel("pos_sale")
        assertEquals(MasteryLevel.LEARNING, level)
    }

    @Test
    fun getMastery_noRecord_returnsNull() = runTest {
        coEvery { dao.getByFeature("unknown") } returns null
        assertNull(repo.getMastery("unknown"))
    }

    @Test
    fun getUndiscoveredFeatures_returnsOnlyAbsent() = runTest {
        coEvery { dao.getAll() } returns listOf(
            mastery("add_product", MasteryLevel.LEARNING),
            mastery("pos_sale", MasteryLevel.DISCOVERED),
        )
        val known = listOf("add_product", "pos_sale", "customers", "debts")
        val undiscovered = repo.getUndiscoveredFeatures(known)
        assertTrue("customers" in undiscovered)
        assertTrue("debts" in undiscovered)
        assertTrue("add_product" !in undiscovered)
    }

    @Test
    fun overallProgress_allUndiscovered_returnsZero() = runTest {
        coEvery { dao.getAll() } returns emptyList()
        assertEquals(0f, repo.overallProgress(), 0.001f)
    }

    @Test
    fun overallProgress_allMastered_returnsOne() = runTest {
        coEvery { dao.getAll() } returns listOf(
            mastery("f1", MasteryLevel.MASTERED),
            mastery("f2", MasteryLevel.MASTERED),
        )
        assertEquals(1f, repo.overallProgress(), 0.001f)
    }

    @Test
    fun overallProgress_mixed_isBetweenZeroAndOne() = runTest {
        coEvery { dao.getAll() } returns listOf(
            mastery("f1", MasteryLevel.MASTERED),
            mastery("f2", MasteryLevel.LEARNING),
            mastery("f3", MasteryLevel.DISCOVERED),
        )
        val progress = repo.overallProgress()
        assertTrue(progress > 0f && progress < 1f)
    }

    @Test
    fun masteredCount_onlyCountsMastered() = runTest {
        coEvery { dao.getAll() } returns listOf(
            mastery("f1", MasteryLevel.MASTERED),
            mastery("f2", MasteryLevel.PROFICIENT),
            mastery("f3", MasteryLevel.MASTERED),
        )
        assertEquals(2, repo.masteredCount())
    }
}
