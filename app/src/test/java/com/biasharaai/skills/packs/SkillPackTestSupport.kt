package com.biasharaai.skills.packs

import java.security.KeyPairGenerator
import java.util.Base64
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
object SkillPackTestSupport {

    fun generateEcKeyPair(): Pair<PublicKey, PrivateKey> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(256)
        val pair = gen.generateKeyPair()
        return pair.public to pair.private
    }

    fun publicKeyPemBody(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun sign(manifest: SkillPackManifest, privateKey: PrivateKey): SkillPackManifest {
        val verifier = SkillPackVerifier()
        val unsigned = manifest.copy(signatureAlgorithm = null, signatureBase64 = null)
        val payload = verifier.signingPayloadBytes(unsigned)
        val signer = Signature.getInstance(SkillPackVerifier.SUPPORTED_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(payload)
        return manifest.copy(
            signatureAlgorithm = SkillPackVerifier.SUPPORTED_ALGORITHM,
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
        )
    }

    fun sampleManifest(packId: String = "test.pack") = SkillPackManifest(
        packId = packId,
        packName = "Test Pack",
        version = "1.0.0",
        skills = listOf(
            SkillPackManifest.SkillPackSkillEntry(
                skillId = "pack_ping",
                displayName = "Pack ping",
                schemaJson = """{"type":"object","properties":{}}""",
                delegateTo = "ping",
            ),
        ),
    )
}
