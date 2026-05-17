package com.biasharaai.skills.packs

import com.google.gson.Gson
import java.util.Base64
import com.google.gson.GsonBuilder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X11 — verifies ECDSA signatures on [SkillPackManifest] payloads.
 *
 * Production packs are signed offline; the app ships the trust anchor as
 * `assets/skill_pack_trust/signing_public.pem` (X.509 SubjectPublicKeyInfo, base64 body without headers).
 */
@Singleton
class SkillPackVerifier @Inject constructor() {

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    sealed class VerifyResult {
        data object Valid : VerifyResult()
        data class Invalid(val reason: String) : VerifyResult()
    }

    fun verify(manifest: SkillPackManifest, publicKeyPemBody: String?): VerifyResult {
        val sigB64 = manifest.signatureBase64?.trim().orEmpty()
        val algorithm = manifest.signatureAlgorithm?.trim().orEmpty()
        if (sigB64.isEmpty() || algorithm.isEmpty()) {
            return VerifyResult.Invalid("Pack is missing signatureAlgorithm or signatureBase64")
        }
        if (publicKeyPemBody.isNullOrBlank()) {
            return VerifyResult.Invalid("No trust anchor public key configured")
        }
        if (algorithm != SUPPORTED_ALGORITHM) {
            return VerifyResult.Invalid("Unsupported signature algorithm: $algorithm")
        }

        val payload = signingPayloadBytes(manifest)
        val signatureBytes = runCatching { Base64.getDecoder().decode(sigB64) }.getOrElse {
            return VerifyResult.Invalid("signatureBase64 is not valid base64")
        }

        return runCatching {
            val publicKey = decodePublicKey(publicKeyPemBody)
            val verifier = Signature.getInstance(SUPPORTED_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(payload)
            if (verifier.verify(signatureBytes)) {
                VerifyResult.Valid
            } else {
                VerifyResult.Invalid("Signature mismatch")
            }
        }.getOrElse { VerifyResult.Invalid(it.message ?: "Verification failed") }
    }

    /** Deterministic JSON bytes used when signing (signature fields stripped). */
    fun signingPayloadBytes(manifest: SkillPackManifest): ByteArray {
        val unsigned = manifest.copy(signatureAlgorithm = null, signatureBase64 = null)
        return gson.toJson(unsigned).toByteArray(Charsets.UTF_8)
    }

    fun decodePublicKey(pemBody: String): PublicKey {
        val cleaned = pemBody
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val encoded = Base64.getDecoder().decode(cleaned)
        val spec = X509EncodedKeySpec(encoded)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    companion object {
        const val SUPPORTED_ALGORITHM = "SHA256withECDSA"
        const val TRUST_ASSET_PATH = "skill_pack_trust/signing_public.pem"
    }
}
