package com.biasharaai.enterprise

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object EnterprisePinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun isValidPin(pin: String): Boolean {
        val clean = pin.trim()
        return clean.length in 4..8 && clean.all { it.isDigit() }
    }

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.trim().toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    fun verify(pin: String, saltBase64: String?, expectedHashBase64: String?): Boolean {
        if (saltBase64.isNullOrBlank() || expectedHashBase64.isNullOrBlank()) return false
        return runCatching {
            val actual = hash(pin, saltBase64)
            MessageDigest.isEqual(
                Base64.getDecoder().decode(actual),
                Base64.getDecoder().decode(expectedHashBase64),
            )
        }.getOrDefault(false)
    }
}
