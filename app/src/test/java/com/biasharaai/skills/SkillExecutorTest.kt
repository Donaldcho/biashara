package com.biasharaai.skills

import com.biasharaai.data.local.db.SkillDescriptor
import com.biasharaai.data.local.db.SkillDescriptorDao
import com.biasharaai.skills.builtin.PingSkill
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkillExecutorTest {

    private lateinit var dao: SkillDescriptorDao
    private lateinit var registry: SkillRegistry
    private lateinit var executor: SkillExecutor

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        val loader = mockk<SkillCatalogueLoader>()
        coEvery { loader.load() } returns SkillCatalogue(
            catalogueVersion = 1,
            skills = listOf(
                SkillCatalogueEntry("ping", "Health check", "{}", defaultEnabled = true),
                SkillCatalogueEntry("query_sales", "Query sales", "{}", defaultEnabled = true),
                SkillCatalogueEntry("future_skill", "Future", "{}", defaultEnabled = true),
            ),
        )
        registry = SkillRegistry(dao, loader, setOf(PingSkill()))
        executor = SkillExecutor(registry, dao)
    }

    @Test
    fun execute_ping_returnsSuccessAndRecordsExecution() = runTest {
        coEvery { dao.getById("ping") } returns SkillDescriptor(
            skillId = "ping",
            displayName = "Health check",
            schemaJson = "{}",
            isEnabled = true,
        )

        val result = executor.execute("ping", "{}")

        assertTrue(result is SkillResult.Success)
        coVerify { dao.recordExecution("ping", any()) }
    }

    @Test
    fun execute_disabledSkill_returnsFailure() = runTest {
        coEvery { dao.getById("ping") } returns SkillDescriptor(
            skillId = "ping",
            displayName = "Health check",
            schemaJson = "{}",
            isEnabled = false,
        )

        val result = executor.execute("ping")

        assertTrue(result is SkillResult.Failure)
        assertEquals(SkillExecutor.CODE_DISABLED, (result as SkillResult.Failure).code)
        coVerify(exactly = 0) { dao.recordExecution(any(), any()) }
    }

    @Test
    fun execute_unregisteredCatalogueSkill_returnsNotImplemented() = runTest {
        coEvery { dao.getById("future_skill") } returns SkillDescriptor(
            skillId = "future_skill",
            displayName = "Future",
            schemaJson = "{}",
            isEnabled = true,
        )

        val result = executor.execute("future_skill", "{}")

        assertTrue(result is SkillResult.Failure)
        assertEquals(SkillExecutor.CODE_NOT_IMPLEMENTED, (result as SkillResult.Failure).code)
    }

    @Test
    fun execute_unknownSkill_returnsNotFound() = runTest {
        coEvery { dao.getById("missing") } returns null

        val result = executor.execute("missing")

        assertTrue(result is SkillResult.Failure)
        assertEquals(SkillExecutor.CODE_NOT_FOUND, (result as SkillResult.Failure).code)
    }
}
