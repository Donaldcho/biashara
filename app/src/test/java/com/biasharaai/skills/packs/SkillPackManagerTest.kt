package com.biasharaai.skills.packs

import android.content.Context
import com.biasharaai.data.local.db.SkillDescriptorDao
import com.biasharaai.data.local.db.SkillPackRecordDao
import com.biasharaai.skills.SkillCatalogueLoader
import com.biasharaai.skills.SkillRegistry
import com.biasharaai.skills.builtin.PingSkill
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SkillPackManagerTest {

  private lateinit var packDao: SkillPackRecordDao
  private lateinit var descriptorDao: SkillDescriptorDao
  private lateinit var registry: SkillRegistry
  private lateinit var manager: SkillPackManager
  private lateinit var filesDir: File

  @Before
  fun setUp() {
    filesDir = File.createTempFile("skillpack", "").apply { delete(); mkdirs() }
    val context = mockk<Context>()
    every { context.filesDir } returns filesDir
    packDao = mockk(relaxed = true)
    descriptorDao = mockk(relaxed = true)
    val catalogueLoader = mockk<SkillCatalogueLoader>(relaxed = true)
    registry = SkillRegistry(descriptorDao, catalogueLoader, setOf(PingSkill()))
    manager = SkillPackManager(context, packDao, descriptorDao, registry, SkillPackVerifier())
    coEvery { packDao.getAll() } returns emptyList()
    coEvery { descriptorDao.getById(any()) } returns null
  }

  @Test
  fun installFromBytes_registersPackSkillAndPersistsRecord() = runTest {
    val (_, privateKey) = SkillPackTestSupport.generateEcKeyPair()
    val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
    val json = Gson().toJson(signed).toByteArray()

    val result = manager.installFromBytes(json, requireValidSignature = false)

    assertTrue(result.isSuccess)
    assertEquals("test.pack", result.getOrNull()?.packId)
    assertTrue(registry.isImplemented("pack_ping"))
    coVerify { packDao.upsert(any()) }
    coVerify { descriptorDao.upsert(match { it.skillId == "pack_ping" && it.packId == "test.pack" }) }
  }

  @Test
  fun uninstall_removesPackSkills() = runTest {
    val (_, privateKey) = SkillPackTestSupport.generateEcKeyPair()
    val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
    manager.installFromBytes(Gson().toJson(signed).toByteArray(), requireValidSignature = false)
      .getOrThrow()

    manager.uninstall("test.pack")

    assertTrue(!registry.isImplemented("pack_ping"))
    coVerify { descriptorDao.deleteByPackId("test.pack") }
    coVerify { packDao.delete("test.pack") }
  }
}
