package com.biasharaai.skills.packs

import android.content.Context
import android.util.Log
import com.biasharaai.BuildConfig
import com.biasharaai.data.local.db.SkillDescriptor
import com.biasharaai.data.local.db.SkillDescriptorDao
import com.biasharaai.data.local.db.SkillPackRecord
import com.biasharaai.data.local.db.SkillPackRecordDao
import com.biasharaai.skills.SkillRegistry
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X11 — installs signed skill packs from bytes or OTA URL, syncs Room, registers skills.
 */
@Singleton
class SkillPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillPackRecordDao: SkillPackRecordDao,
    private val skillDescriptorDao: SkillDescriptorDao,
    private val skillRegistry: SkillRegistry,
    private val skillPackVerifier: SkillPackVerifier,
) {
    private val gson = Gson()

    val packsDirectory: File
        get() = File(context.filesDir, PACKS_DIR).also { it.mkdirs() }

    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        val active = skillPackRecordDao.getAll().filter { it.isActive && it.signatureValid }
        for (record in active) {
            runCatching { activatePack(record.packId) }
                .onFailure { Log.w(TAG, "Failed to activate pack ${record.packId}", it) }
        }
    }

    suspend fun installFromBytes(
        rawJson: ByteArray,
        requireValidSignature: Boolean = !BuildConfig.DEBUG,
    ): Result<SkillPackRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = parseManifest(rawJson)
            if (requireValidSignature) {
                val trustKey = loadTrustPublicKeyPem()
                when (val verdict = skillPackVerifier.verify(manifest, trustKey)) {
                    is SkillPackVerifier.VerifyResult.Valid -> Unit
                    is SkillPackVerifier.VerifyResult.Invalid ->
                        error("Invalid pack signature: ${verdict.reason}")
                }
            }
            persistAndActivate(manifest, signatureValid = true)
        }
    }

    suspend fun installFromUrl(
        url: String,
        requireValidSignature: Boolean = !BuildConfig.DEBUG,
    ): Result<SkillPackRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val body = downloadUtf8(url)
            installFromBytes(body, requireValidSignature).getOrThrow()
        }
    }

    suspend fun uninstall(packId: String) = withContext(Dispatchers.IO) {
        requireValidPackId(packId)
        skillRegistry.unregisterPack(packId)
        skillDescriptorDao.deleteByPackId(packId)
        skillPackRecordDao.delete(packId)
        packFile(packId).delete()
    }

    suspend fun listInstalled(): List<SkillPackRecord> = withContext(Dispatchers.IO) {
        skillPackRecordDao.getAll()
    }

    private suspend fun persistAndActivate(manifest: SkillPackManifest, signatureValid: Boolean): SkillPackRecord {
        val now = System.currentTimeMillis()
        val record = SkillPackRecord(
            packId = manifest.packId,
            packName = manifest.packName,
            version = manifest.version,
            installedAt = now,
            isActive = true,
            signatureValid = signatureValid,
        )
        requireValidPackId(manifest.packId)
        packFile(manifest.packId).writeBytes(gson.toJson(manifest).toByteArray())
        skillPackRecordDao.upsert(record)
        activatePack(manifest.packId)
        return record
    }

    private suspend fun activatePack(packId: String) {
        requireValidPackId(packId)
        val file = packFile(packId)
        if (!file.isFile) error("Pack file missing for $packId")
        val manifest = parseManifest(file.readBytes())
        registerPackSkills(manifest)
    }

    private suspend fun registerPackSkills(manifest: SkillPackManifest) {
        val bridged = manifest.skills.map { entry ->
            val delegate = entry.delegateTo?.let { skillRegistry.get(it) }
            PackBridgedSkill(manifest.packId, entry, delegate)
        }
        skillRegistry.registerPackSkills(manifest.packId, bridged)

        for (entry in manifest.skills) {
            val existing = skillDescriptorDao.getById(entry.skillId)
            skillDescriptorDao.upsert(
                SkillDescriptor(
                    skillId = entry.skillId,
                    displayName = entry.displayName,
                    schemaJson = entry.schemaJson,
                    isEnabled = existing?.isEnabled ?: entry.defaultEnabled,
                    packId = manifest.packId,
                    lastExecutedAt = existing?.lastExecutedAt,
                    executionCount = existing?.executionCount ?: 0L,
                ),
            )
        }
    }

    private fun parseManifest(bytes: ByteArray): SkillPackManifest {
        val text = bytes.toString(Charsets.UTF_8).trim()
        return gson.fromJson(text, SkillPackManifest::class.java)
            ?: error("Invalid skill pack JSON")
    }

    private fun loadTrustPublicKeyPem(): String? = runCatching {
        context.assets.open(SkillPackVerifier.TRUST_ASSET_PATH).bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun downloadUtf8(url: String): ByteArray {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Skill pack URL must use HTTPS"
        }
        val connection = URL(trimmed).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "BiasharaAI/${BuildConfig.VERSION_NAME}")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            val maxBytes = MAX_DOWNLOAD_BYTES
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) error("Skill pack download exceeds ${maxBytes / 1024}KB limit")
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requireValidPackId(packId: String) {
        require(PACK_ID_REGEX.matches(packId)) { "Invalid skill pack id: $packId" }
    }

    private fun packFile(packId: String): File {
        requireValidPackId(packId)
        val dir = packsDirectory.canonicalFile
        val file = File(dir, "$packId.json").canonicalFile
        require(file.parentFile == dir) { "Invalid pack path for $packId" }
        return file
    }

    companion object {
        private const val TAG = "SkillPackManager"
        const val PACKS_DIR = "skill_packs"
        private val PACK_ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
        private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    }
}
