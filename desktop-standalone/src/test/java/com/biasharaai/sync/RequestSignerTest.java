package com.biasharaai.sync;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSignerTest {
    @Test
    void signatureIsDeterministicAndBoundToRequestBody() {
        byte[] body = "{\"quantity\":3}".getBytes(StandardCharsets.UTF_8);
        String signature = RequestSigner.sign(
            "POST",
            "/api/phone/stock-intake",
            SyncProtocol.CURRENT_VERSION,
            "request-id",
            "1800000000000",
            "nonce",
            body,
            "session-secret"
        );

        assertEquals(64, signature.length());
        assertTrue(RequestSigner.verify(
            signature,
            "POST",
            "/api/phone/stock-intake",
            SyncProtocol.CURRENT_VERSION,
            "request-id",
            "1800000000000",
            "nonce",
            body,
            "session-secret"
        ));
        assertFalse(RequestSigner.verify(
            signature,
            "POST",
            "/api/phone/stock-intake",
            SyncProtocol.CURRENT_VERSION,
            "request-id",
            "1800000000000",
            "nonce",
            "{\"quantity\":4}".getBytes(StandardCharsets.UTF_8),
            "session-secret"
        ));
    }
}
