package com.biasharaai.ai

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceTokenStoreTest {

    @Test
    fun setToken_writesToPrefs() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val context = mockk<Context>()
        every { context.getSharedPreferences("huggingface_auth", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getString("access_token", null) } returns "hf_abc"

        val store = HuggingFaceTokenStore(context)
        store.setToken("hf_abc")

        verify { editor.putString("access_token", "hf_abc") }
        assertTrue(store.hasToken())
        assertEquals("hf_abc", store.getToken())
    }

    @Test
    fun setToken_null_clearsPrefs() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val context = mockk<Context>()
        every { context.getSharedPreferences("huggingface_auth", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getString("access_token", null) } returns null

        val store = HuggingFaceTokenStore(context)
        store.setToken(null)

        verify { editor.remove("access_token") }
        assertFalse(store.hasToken())
    }
}
