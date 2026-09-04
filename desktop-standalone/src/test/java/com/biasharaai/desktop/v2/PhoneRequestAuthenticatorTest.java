package com.biasharaai.desktop.v2;

import com.biasharaai.sync.RequestSigner;
import com.biasharaai.sync.SyncProtocol;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneRequestAuthenticatorTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String SESSION = "0123456789abcdef0123456789abcdef";
    private static final String PATH = "/api/phone/reconcile";
    private static final byte[] BODY = "{\"stockChanges\":[]}".getBytes(StandardCharsets.UTF_8);

    private final PhoneRequestAuthenticator authenticator = new PhoneRequestAuthenticator(
        Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
        SyncProtocol.MAX_CLOCK_SKEW_MILLIS
    );

    @Test
    void acceptsSignedRequestOnceAndRejectsReplay() {
        String requestId = "request-1";
        String nonce = "nonce-1";
        String timestamp = Long.toString(NOW);
        String signature = signature(requestId, timestamp, nonce, BODY);

        PhoneRequestAuthenticator.Result accepted = authenticate(requestId, timestamp, nonce, signature, BODY);
        PhoneRequestAuthenticator.Result replayed = authenticate(requestId, timestamp, nonce, signature, BODY);

        assertTrue(accepted.accepted());
        assertFalse(accepted.legacy());
        assertFalse(replayed.accepted());
        assertEquals(409, replayed.status());
    }

    @Test
    void rejectsBodyTamperingAndExpiredRequests() {
        String timestamp = Long.toString(NOW);
        String signature = signature("request-2", timestamp, "nonce-2", BODY);
        byte[] tamperedBody = "{\"stockChanges\":[1]}".getBytes(StandardCharsets.UTF_8);

        PhoneRequestAuthenticator.Result tampered = authenticate(
            "request-2",
            timestamp,
            "nonce-2",
            signature,
            tamperedBody
        );
        PhoneRequestAuthenticator.Result expired = authenticate(
            "request-3",
            Long.toString(NOW - SyncProtocol.MAX_CLOCK_SKEW_MILLIS - 1),
            "nonce-3",
            "not-used",
            BODY
        );

        assertEquals(401, tampered.status());
        assertEquals(401, expired.status());
    }

    @Test
    void permitsLegacySessionDuringCompatibilityWindow() {
        PhoneRequestAuthenticator.Result result = authenticator.authenticate(
            "POST",
            PATH,
            BODY,
            SESSION,
            null,
            null,
            null,
            null,
            null,
            SESSION,
            true
        );

        assertTrue(result.accepted());
        assertTrue(result.legacy());
    }

    @Test
    void rejectsUnknownProtocolVersion() {
        PhoneRequestAuthenticator.Result result = authenticator.authenticate(
            "POST",
            PATH,
            BODY,
            SESSION,
            "99.0",
            "request-4",
            Long.toString(NOW),
            "nonce-4",
            "not-used",
            SESSION,
            true
        );

        assertEquals(426, result.status());
    }

    @Test
    void signedOnlySessionRejectsLegacyDowngrade() {
        PhoneRequestAuthenticator.Result result = authenticator.authenticate(
            "POST",
            PATH,
            BODY,
            SESSION,
            null,
            null,
            null,
            null,
            null,
            SESSION,
            false
        );

        assertEquals(426, result.status());
    }

    private PhoneRequestAuthenticator.Result authenticate(
        String requestId,
        String timestamp,
        String nonce,
        String signature,
        byte[] body
    ) {
        return authenticator.authenticate(
            "POST",
            PATH,
            body,
            SESSION,
            SyncProtocol.CURRENT_VERSION,
            requestId,
            timestamp,
            nonce,
            signature,
            SESSION,
            true
        );
    }

    private String signature(String requestId, String timestamp, String nonce, byte[] body) {
        return RequestSigner.sign(
            "POST",
            PATH,
            SyncProtocol.CURRENT_VERSION,
            requestId,
            timestamp,
            nonce,
            body,
            SESSION
        );
    }
}
