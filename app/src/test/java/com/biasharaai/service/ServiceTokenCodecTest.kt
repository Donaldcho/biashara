package com.biasharaai.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTokenCodecTest {

    @Test
    fun parse_catalogueToken() {
        val parsed = ServiceTokenCodec.parse("BSVC:42")
        assertTrue(parsed is ServiceTokenCodec.Parsed.Catalogue)
        assertEquals(42L, (parsed as ServiceTokenCodec.Parsed.Catalogue).serviceItemId)
    }

    @Test
    fun parse_voucherToken() {
        val parsed = ServiceTokenCodec.parse("BSVOU:abc-123")
        assertTrue(parsed is ServiceTokenCodec.Parsed.Voucher)
        assertEquals("abc-123", (parsed as ServiceTokenCodec.Parsed.Voucher).voucherId)
    }

    @Test
    fun catalogueRoundTrip() {
        val token = ServiceTokenCodec.catalogueToken(7L)
        assertNotNull(ServiceTokenCodec.parse(token))
    }

    @Test
    fun resolveVoucherId_fromToken() {
        assertEquals("abc-123", ServiceTokenCodec.resolveVoucherId("BSVOU:abc-123"))
    }

    @Test
    fun resolveVoucherId_fromRawId() {
        assertEquals("abc-123", ServiceTokenCodec.resolveVoucherId("abc-123"))
    }
}
