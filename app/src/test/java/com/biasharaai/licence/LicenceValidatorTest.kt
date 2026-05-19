package com.biasharaai.licence

import android.content.Context
import android.content.SharedPreferences
import com.biasharaai.productline.ProductLineManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LicenceValidatorTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var context: Context
    private lateinit var validator: LicenceValidator

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
        every { prefs.getString(any(), any()) } returns null

        context = mockk()
        every { context.getSharedPreferences(LicenceValidator.PREFS_NAME, Context.MODE_PRIVATE) } returns prefs

        validator = LicenceValidator(context)
    }

    @Test
    fun devShopKey_parsesAsShop() {
        val key = validator.parseAndVerify(LicenceValidator.DEV_SHOP_PRIVATE)
        assertEquals(ProductLine.SHOP, key.productLine)
        assertEquals(Edition.PRIVATE, key.edition)
        assertEquals(1, key.maxDevices)
    }

    @Test
    fun devProKey_parsesAsPro() {
        val key = validator.parseAndVerify(LicenceValidator.DEV_PRO_ENTERPRISE)
        assertEquals(ProductLine.PRO, key.productLine)
        assertEquals(Edition.ENTERPRISE, key.edition)
    }

    @Test
    fun storeAndReload_roundTrips() {
        var savedJson: String? = null
        every { editor.putString(LicenceValidator.KEY_LICENCE_JSON, any()) } answers {
            savedJson = secondArg()
            editor
        }
        every { prefs.getString(LicenceValidator.KEY_LICENCE_JSON, null) } answers { savedJson }

        validator.storeLicenceKey(LicenceValidator.DEV_SHOP_PRIVATE).getOrThrow()
        val stored = validator.getStoredKey()
        requireNotNull(stored)
        assertEquals(ProductLine.SHOP, stored.productLine)
        verify { editor.putString(LicenceValidator.KEY_LICENCE_JSON, any()) }
    }

    @Test
    fun productLineManager_reflectsStoredKey() {
        var savedJson: String? = null
        every { editor.putString(LicenceValidator.KEY_LICENCE_JSON, any()) } answers {
            savedJson = secondArg()
            editor
        }
        every { prefs.getString(LicenceValidator.KEY_LICENCE_JSON, null) } answers { savedJson }

        validator.storeLicenceKey(LicenceValidator.DEV_SHOP_PRIVATE).getOrThrow()
        val manager = ProductLineManager(validator, EditionManager(validator))
        assertFalse(manager.isProEnabled())

        validator.storeLicenceKey(LicenceValidator.DEV_PRO_ENTERPRISE).getOrThrow()
        assertTrue(manager.isProEnabled())
    }

    @Test
    fun tamperedSignature_rejected() {
        val tampered = LicenceValidator.DEV_SHOP_PRIVATE.dropLast(2) + "xx"
        val result = runCatching { validator.parseAndVerify(tampered) }
        assertTrue(result.isFailure)
    }
}
