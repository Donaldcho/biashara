package com.biasharaai.service

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signed BSRC receipt tokens — additive to [ServiceTokenCodec] (do not change parse()).
 */
object ServiceReceiptCodec {
    private val gson = Gson()
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val PREFIX = "BSRC:"

    data class Payload(
        val deliveryId: Long,
        val transactionId: Long?,
        val serviceItemId: Long,
        val warrantyExpiresAt: Long?,
        val businessId: String,
    )

    sealed class DecodeResult {
        data class Valid(val payload: Payload) : DecodeResult()
        data object InvalidSignature : DecodeResult()
        data object MalformedToken : DecodeResult()
    }

    enum class WarrantyStatus { NONE, ACTIVE, EXPIRED }

    fun encode(payload: Payload, signingKey: String): String {
        val json = gson.toJson(payload)
        val payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val sig = hmac(signingKey, json)
        val sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig)
        return "$PREFIX$payloadB64.$sigB64"
    }

    fun decode(rawToken: String, signingKey: String): DecodeResult {
        val trimmed = rawToken.trim()
        if (!trimmed.startsWith(PREFIX)) return DecodeResult.MalformedToken
        val body = trimmed.removePrefix(PREFIX)
        val dot = body.lastIndexOf('.')
        if (dot <= 0) return DecodeResult.MalformedToken
        val payloadB64 = body.substring(0, dot)
        val sigB64 = body.substring(dot + 1)
        return try {
            val json = String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8)
            val expected = hmac(signingKey, json)
            val actual = Base64.getUrlDecoder().decode(sigB64)
            if (!expected.contentEquals(actual)) return DecodeResult.InvalidSignature
            val payload = gson.fromJson(json, Payload::class.java) ?: return DecodeResult.MalformedToken
            DecodeResult.Valid(payload)
        } catch (_: Exception) {
            DecodeResult.MalformedToken
        }
    }

    fun warrantyStatus(warrantyExpiresAt: Long?): WarrantyStatus {
        if (warrantyExpiresAt == null || warrantyExpiresAt <= 0L) return WarrantyStatus.NONE
        return if (System.currentTimeMillis() < warrantyExpiresAt) {
            WarrantyStatus.ACTIVE
        } else {
            WarrantyStatus.EXPIRED
        }
    }

    private fun hmac(signingKey: String, message: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
    }
}
