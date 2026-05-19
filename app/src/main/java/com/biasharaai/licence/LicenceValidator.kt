package com.biasharaai.licence

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses, verifies, and persists licence keys (`base64url(json).base64url(hmac)`).
 */
@Singleton
class LicenceValidator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getStoredKey(): LicenceKey? {
        val raw = prefs.getString(KEY_LICENCE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, StoredLicenceDto::class.java).toLicenceKey() }.getOrNull()
    }

    /**
     * Validates [keyString], persists on success, and returns the parsed [LicenceKey].
     */
    fun storeLicenceKey(keyString: String): Result<LicenceKey> {
        val parsed = parseAndVerify(keyString.trim())
        prefs.edit()
            .putString(KEY_LICENCE_JSON, gson.toJson(StoredLicenceDto.from(parsed)))
            .apply()
        return Result.success(parsed)
    }

    fun clearLicenceKey() {
        prefs.edit().remove(KEY_LICENCE_JSON).apply()
    }

    /** First launch: Shop + Private unless a key is already stored. */
    fun ensureDefaultLicence() {
        if (getStoredKey() != null) return
        storeLicenceKey(DEV_SHOP_PRIVATE).getOrThrow()
    }

    fun parseAndVerify(keyString: String): LicenceKey {
        val trimmed = keyString.trim()
        val dot = trimmed.lastIndexOf('.')
        require(dot > 0) { "Invalid licence key format" }
        val payloadB64 = trimmed.substring(0, dot)
        val sigB64 = trimmed.substring(dot + 1)
        val payloadJson = String(decodeUrlSafe(payloadB64), StandardCharsets.UTF_8)
        val expectedSig = hmacSha256(payloadJson)
        val actualSig = decodeUrlSafe(sigB64)
        require(expectedSig.contentEquals(actualSig)) { "Licence signature invalid" }

        val dto = gson.fromJson(payloadJson, WireLicenceDto::class.java)
        val productLine = ProductLine.parse(dto.productLine)
            ?: throw IllegalArgumentException("Unknown productLine: ${dto.productLine}")
        val edition = Edition.parse(dto.edition)
            ?: throw IllegalArgumentException("Unknown edition: ${dto.edition}")
        val now = System.currentTimeMillis()
        dto.expiresAt?.let { expires ->
            require(expires > now) { "Licence has expired" }
        }

        return LicenceKey(
            businessId = dto.businessId,
            productLine = productLine,
            edition = edition,
            maxDevices = dto.maxDevices.coerceAtLeast(1),
            issuedAt = dto.issuedAt,
            expiresAt = dto.expiresAt,
        )
    }

    fun encodeSignedKey(
        businessId: String,
        productLine: ProductLine,
        edition: Edition,
        maxDevices: Int,
        issuedAt: Long = System.currentTimeMillis(),
        expiresAt: Long? = null,
    ): String {
        val dto = WireLicenceDto(
            businessId = businessId,
            productLine = productLine.name,
            edition = edition.name,
            maxDevices = maxDevices,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
        val json = gson.toJson(dto)
        val payloadB64 = encodeUrlSafe(json.toByteArray(StandardCharsets.UTF_8))
        val sigB64 = encodeUrlSafe(hmacSha256(json))
        return "$payloadB64.$sigB64"
    }

    private fun hmacSha256(message: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
    }

    private fun encodeUrlSafe(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeUrlSafe(encoded: String): ByteArray =
        Base64.getUrlDecoder().decode(encoded)

    private data class WireLicenceDto(
        @SerializedName("businessId") val businessId: String,
        @SerializedName("productLine") val productLine: String,
        @SerializedName("edition") val edition: String,
        @SerializedName("maxDevices") val maxDevices: Int,
        @SerializedName("issuedAt") val issuedAt: Long,
        @SerializedName("expiresAt") val expiresAt: Long?,
    )

    private data class StoredLicenceDto(
        @SerializedName("businessId") val businessId: String,
        @SerializedName("productLine") val productLine: String,
        @SerializedName("edition") val edition: String,
        @SerializedName("maxDevices") val maxDevices: Int,
        @SerializedName("issuedAt") val issuedAt: Long,
        @SerializedName("expiresAt") val expiresAt: Long?,
    ) {
        fun toLicenceKey(): LicenceKey = LicenceKey(
            businessId = businessId,
            productLine = ProductLine.parse(productLine) ?: ProductLine.SHOP,
            edition = Edition.parse(edition) ?: Edition.PRIVATE,
            maxDevices = maxDevices,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )

        companion object {
            fun from(key: LicenceKey) = StoredLicenceDto(
                businessId = key.businessId,
                productLine = key.productLine.name,
                edition = key.edition.name,
                maxDevices = key.maxDevices,
                issuedAt = key.issuedAt,
                expiresAt = key.expiresAt,
            )
        }
    }

    companion object {
        internal const val PREFS_NAME = "biashara_licence"
        internal const val KEY_LICENCE_JSON = "licence_json"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        /** Dev-only signing secret — replace with server-side signing for production keys. */
        private const val HMAC_SECRET = "biashara-dev-licence-v1"

        /** Shop v1.0 release testing — product retail only, single device. */
        val DEV_SHOP_PRIVATE: String = DevSigner.encode(
            businessId = "biz_dev_shop",
            productLine = ProductLine.SHOP,
            edition = Edition.PRIVATE,
            maxDevices = 1,
        )

        /** Unlocks all Pro + Enterprise features for local development. */
        val DEV_PRO_ENTERPRISE: String = DevSigner.encode(
            businessId = "biz_dev_pro",
            productLine = ProductLine.PRO,
            edition = Edition.ENTERPRISE,
            maxDevices = 99,
        )
    }

    /** Stateless signer for companion dev keys (no Context). */
    private object DevSigner {
        private val gson = Gson()

        fun encode(
            businessId: String,
            productLine: ProductLine,
            edition: Edition,
            maxDevices: Int,
        ): String {
            val dto = mapOf(
                "businessId" to businessId,
                "productLine" to productLine.name,
                "edition" to edition.name,
                "maxDevices" to maxDevices,
                "issuedAt" to System.currentTimeMillis(),
                "expiresAt" to null,
            )
            val json = gson.toJson(dto)
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
            val sig = mac.doFinal(json.toByteArray(StandardCharsets.UTF_8))
            val payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
            val sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig)
            return "$payloadB64.$sigB64"
        }
    }
}
