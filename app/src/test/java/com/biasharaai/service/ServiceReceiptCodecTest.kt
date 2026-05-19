package com.biasharaai.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceReceiptCodecTest {

    @Test
    fun encodeDecode_roundTrip() {
        val key = "test-signing-key"
        val payload = ServiceReceiptCodec.Payload(
            deliveryId = 42L,
            transactionId = 99L,
            serviceItemId = 7L,
            warrantyExpiresAt = System.currentTimeMillis() + 86_400_000L,
            businessId = "biz-1",
        )
        val token = ServiceReceiptCodec.encode(payload, key)
        val result = ServiceReceiptCodec.decode(token, key)
        assertTrue(result is ServiceReceiptCodec.DecodeResult.Valid)
        val valid = result as ServiceReceiptCodec.DecodeResult.Valid
        assertEquals(42L, valid.payload.deliveryId)
        assertEquals(99L, valid.payload.transactionId)
    }

    @Test
    fun tamperedToken_invalidSignature() {
        val key = "test-signing-key"
        val payload = ServiceReceiptCodec.Payload(
            deliveryId = 1L,
            transactionId = null,
            serviceItemId = 2L,
            warrantyExpiresAt = null,
            businessId = "biz",
        )
        val token = ServiceReceiptCodec.encode(payload, key)
        val tampered = token.dropLast(1) + if (token.last() == 'a') "b" else "a"
        val result = ServiceReceiptCodec.decode(tampered, key)
        assertEquals(ServiceReceiptCodec.DecodeResult.InvalidSignature, result)
    }

    @Test
    fun warrantyStatus_activeAndExpired() {
        val tomorrow = System.currentTimeMillis() + 86_400_000L
        val yesterday = System.currentTimeMillis() - 86_400_000L
        assertEquals(ServiceReceiptCodec.WarrantyStatus.ACTIVE, ServiceReceiptCodec.warrantyStatus(tomorrow))
        assertEquals(ServiceReceiptCodec.WarrantyStatus.EXPIRED, ServiceReceiptCodec.warrantyStatus(yesterday))
        assertEquals(ServiceReceiptCodec.WarrantyStatus.NONE, ServiceReceiptCodec.warrantyStatus(null))
    }
}
