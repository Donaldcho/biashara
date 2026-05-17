package com.biasharaai.skills

import com.biasharaai.data.local.db.SkillDescriptor
import com.biasharaai.data.local.db.SkillDescriptorDao
import com.biasharaai.skills.packs.PackBridgedSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X4 — in-memory registry of [BiasharaSkill] implementations plus Room metadata sync
 * from [skills_catalogue.json].
 */
@Singleton
class SkillRegistry @Inject constructor(
    private val skillDescriptorDao: SkillDescriptorDao,
    private val catalogueLoader: SkillCatalogueLoader,
    builtInSkills: Set<@JvmSuppressWildcards BiasharaSkill>,
) {
    private val skills = mutableMapOf<String, BiasharaSkill>()

    @Volatile
    private var cachedCatalogue: SkillCatalogue? = null

    init {
        builtInSkills.forEach { register(it) }
    }

    fun catalogue(): SkillCatalogue =
        cachedCatalogue ?: catalogueLoader.load().also { cachedCatalogue = it }

    fun register(skill: BiasharaSkill) {
        skills[skill.id] = skill
    }

    /** X11 — registers skills from an installed pack (replaces prior pack skills with same [packId]). */
    fun registerPackSkills(packId: String, packSkills: List<BiasharaSkill>) {
        unregisterPack(packId)
        for (skill in packSkills) {
            skills[skill.id] = skill
        }
    }

    fun unregisterPack(packId: String) {
        val toRemove = skills.filterValues { skill ->
            skill is PackBridgedSkill && skill.packId == packId
        }.keys
        toRemove.forEach { skills.remove(it) }
    }

    fun get(skillId: String): BiasharaSkill? = skills[skillId]

    fun allRegistered(): List<BiasharaSkill> = skills.values.sortedBy { it.displayName }

    fun isImplemented(skillId: String): Boolean = skills.containsKey(skillId)

    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        syncFromCatalogue()
    }

    suspend fun syncFromCatalogue() = withContext(Dispatchers.IO) {
        val cat = catalogueLoader.load().also { cachedCatalogue = it }
        for (entry in cat.skills) {
            val existing = skillDescriptorDao.getById(entry.skillId)
            skillDescriptorDao.upsert(entry.toDescriptor(existing))
        }
    }

    suspend fun getDescriptor(skillId: String): SkillDescriptor? =
        withContext(Dispatchers.IO) { skillDescriptorDao.getById(skillId) }

    suspend fun getAllDescriptors(): List<SkillDescriptor> =
        withContext(Dispatchers.IO) { skillDescriptorDao.getAll() }

    suspend fun setEnabled(skillId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        skillDescriptorDao.setEnabled(skillId, enabled)
    }

    private fun SkillCatalogueEntry.toDescriptor(existing: SkillDescriptor?): SkillDescriptor =
        SkillDescriptor(
            skillId = skillId,
            displayName = displayName,
            schemaJson = schemaJson,
            isEnabled = existing?.isEnabled ?: defaultEnabled,
            packId = packId ?: existing?.packId,
            lastExecutedAt = existing?.lastExecutedAt,
            executionCount = existing?.executionCount ?: 0L,
        )
}
