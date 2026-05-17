package com.biasharaai.skills.packs

import android.content.Context
import android.content.res.AssetManager
import com.biasharaai.data.local.db.SkillDescriptorDao
import com.biasharaai.data.local.db.SkillPackRecordDao
import com.biasharaai.skills.SkillCatalogueLoader
import com.biasharaai.skills.SkillRegistry
import com.biasharaai.skills.builtin.PingSkill
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

/**
 * X12 — Phase 6 pack-security acceptance tests.
 *
 * Verifies that [SkillPackManager.installFromBytes] with [requireValidSignature] = true enforces
 * the ECDSA trust chain: unsigned packs are rejected, tampered payloads are rejected, and a
 * correctly signed pack (signed with the key exposed via context assets) is accepted.
 */
class PackSecurityAcceptanceTest {

    private lateinit var packDao: SkillPackRecordDao
    private lateinit var descriptorDao: SkillDescriptorDao
    private lateinit var registry: SkillRegistry
    private lateinit var manager: SkillPackManager
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    @Before
    fun setUp() {
        filesDir = File.createTempFile("skillpack_sec", "").apply { delete(); mkdirs() }
        context = mockk()
        assetManager = mockk()
        every { context.filesDir } returns filesDir
        every { context.assets } returns assetManager
        // Default: no trust key in assets → loadTrustPublicKeyPem() returns null
        every { assetManager.open(any()) } throws FileNotFoundException("no trust key in test assets")
        packDao = mockk(relaxed = true)
        descriptorDao = mockk(relaxed = true)
        val catalogueLoader = mockk<SkillCatalogueLoader>(relaxed = true)
        registry = SkillRegistry(descriptorDao, catalogueLoader, setOf(PingSkill()))
        manager = SkillPackManager(context, packDao, descriptorDao, registry, SkillPackVerifier())
        coEvery { packDao.getAll() } returns emptyList()
        coEvery { descriptorDao.getById(any()) } returns null
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    // ── No trust anchor (null key) ─────────────────────────────────────────────

    @Test
    fun installFromBytes_requireSignature_noTrustKey_rejectsValidlySignedPack() = runTest {
        // Even a correctly signed pack is rejected when there is no trust anchor in assets.
        val (_, privateKey) = SkillPackTestSupport.generateEcKeyPair()
        val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
        val json = Gson().toJson(signed).toByteArray()

        val result = manager.installFromBytes(json, requireValidSignature = true)

        assertFalse("Pack must be rejected when no trust anchor is available", result.isSuccess)
    }

    // ── Unsigned pack ──────────────────────────────────────────────────────────

    @Test
    fun installFromBytes_requireSignature_rejectsUnsignedPack() = runTest {
        val (publicKey, _) = SkillPackTestSupport.generateEcKeyPair()
        installTrustKey(SkillPackTestSupport.publicKeyPemBody(publicKey))

        val unsigned = SkillPackTestSupport.sampleManifest() // no signature fields
        val json = Gson().toJson(unsigned).toByteArray()

        val result = manager.installFromBytes(json, requireValidSignature = true)

        assertFalse("Unsigned pack must be rejected", result.isSuccess)
    }

    // ── Tampered payload ───────────────────────────────────────────────────────

    @Test
    fun installFromBytes_requireSignature_rejectsTamperedVersion() = runTest {
        val (publicKey, privateKey) = SkillPackTestSupport.generateEcKeyPair()
        installTrustKey(SkillPackTestSupport.publicKeyPemBody(publicKey))

        val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
        val tampered = signed.copy(version = "99.0.0") // version changed after signing
        val json = Gson().toJson(tampered).toByteArray()

        val result = manager.installFromBytes(json, requireValidSignature = true)

        assertFalse("Tampered pack must be rejected", result.isSuccess)
    }

    @Test
    fun installFromBytes_requireSignature_rejectsTamperedPackId() = runTest {
        val (publicKey, privateKey) = SkillPackTestSupport.generateEcKeyPair()
        installTrustKey(SkillPackTestSupport.publicKeyPemBody(publicKey))

        val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
        val tampered = signed.copy(packId = "evil.pack")
        val json = Gson().toJson(tampered).toByteArray()

        val result = manager.installFromBytes(json, requireValidSignature = true)

        assertFalse("Pack with tampered packId must be rejected", result.isSuccess)
    }

    @Test
    fun installFromBytes_requireSignature_rejectsWrongKey() = runTest {
        val (_, wrongPrivateKey) = SkillPackTestSupport.generateEcKeyPair()
        val (rightPublicKey, _) = SkillPackTestSupport.generateEcKeyPair()
        installTrustKey(SkillPackTestSupport.publicKeyPemBody(rightPublicKey))

        val signedWithWrongKey = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), wrongPrivateKey)
        val json = Gson().toJson(signedWithWrongKey).toByteArray()

        val result = manager.installFromBytes(json, requireValidSignature = true)

        assertFalse("Pack signed with a different key must be rejected", result.isSuccess)
    }

    // ── Valid signed pack ──────────────────────────────────────────────────────

    @Test
    fun installFromBytes_requireSignature_acceptsValidSignedPack() = runTest {
        val (publicKey, privateKey) = SkillPackTestSupport.generateEcKeyPair()
        installTrustKey(SkillPackTestSupport.publicKeyPemBody(publicKey))

        val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
        val json = Gson().toJson(signed).toByteArray()

        val result = manager.installFromBytes(json, requireValidSignature = true)

        assertTrue("Valid signed pack must be accepted", result.isSuccess)
        assertEquals("test.pack", result.getOrNull()?.packId)
        assertTrue("Pack skills must be registered after install", registry.isImplemented("pack_ping"))
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /** Configures the asset mock to serve [pemBody] when the verifier requests the trust key. */
    private fun installTrustKey(pemBody: String) {
        every { assetManager.open(SkillPackVerifier.TRUST_ASSET_PATH) } answers {
            pemBody.byteInputStream()
        }
    }
}
