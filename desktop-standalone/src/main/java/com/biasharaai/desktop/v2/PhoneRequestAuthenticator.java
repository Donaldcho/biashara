package com.biasharaai.desktop.v2;

import com.biasharaai.sync.RequestSigner;
import com.biasharaai.sync.SyncProtocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PhoneRequestAuthenticator {
    private static final int MAX_REPLAY_ENTRIES = 10_000;

    private final Clock clock;
    private final long maximumClockSkewMillis;
    private final Map<String, Long> acceptedRequests = new ConcurrentHashMap<>();

    PhoneRequestAuthenticator() {
        this(Clock.systemUTC(), SyncProtocol.MAX_CLOCK_SKEW_MILLIS);
    }

    PhoneRequestAuthenticator(Clock clock, long maximumClockSkewMillis) {
        this.clock = clock;
        this.maximumClockSkewMillis = maximumClockSkewMillis;
    }

    Result authenticate(
        String method,
        String path,
        byte[] body,
        String suppliedSession,
        String protocolVersion,
        String requestId,
        String timestamp,
        String nonce,
        String signature,
        String expectedSession,
        boolean allowLegacy
    ) {
        if (!secureEquals(expectedSession, suppliedSession)) {
            return Result.rejected(401, "Phone session is invalid. Pair the phone again.");
        }
        if (isBlank(protocolVersion)) {
            return allowLegacy
                ? Result.acceptedLegacy()
                : Result.rejected(426, "This phone session requires signed sync. Update or pair the mobile app again.");
        }
        if (!SyncProtocol.SUPPORTED_VERSIONS.contains(protocolVersion.trim())) {
            return Result.rejected(426, "Phone sync protocol is not supported. Update the mobile or desktop app.");
        }
        if (isBlank(requestId) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return Result.rejected(401, "Signed phone request headers are incomplete.");
        }

        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            return Result.rejected(400, "Phone request timestamp is invalid.");
        }
        long now = clock.millis();
        if (requestTime < now - maximumClockSkewMillis || requestTime > now + maximumClockSkewMillis) {
            return Result.rejected(401, "Phone request expired. Check the phone and laptop clocks.");
        }

        boolean verified = RequestSigner.verify(
            signature,
            method,
            path,
            protocolVersion,
            requestId,
            timestamp,
            nonce,
            body,
            expectedSession
        );
        if (!verified) {
            return Result.rejected(401, "Phone request signature is invalid.");
        }

        pruneExpired(now);
        if (acceptedRequests.size() >= MAX_REPLAY_ENTRIES) {
            return Result.rejected(503, "Phone request replay protection is temporarily at capacity. Retry shortly.");
        }
        String replayKey = requestId.trim() + ":" + nonce.trim();
        Long prior = acceptedRequests.putIfAbsent(replayKey, now + maximumClockSkewMillis);
        if (prior != null) {
            return Result.rejected(409, "Duplicate phone request rejected.");
        }
        return Result.acceptedSigned();
    }

    private void pruneExpired(long now) {
        acceptedRequests.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private boolean secureEquals(String expected, String supplied) {
        if (isBlank(expected) || isBlank(supplied)) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    record Result(boolean accepted, boolean legacy, int status, String message) {
        static Result acceptedSigned() {
            return new Result(true, false, 200, "Signed request accepted.");
        }

        static Result acceptedLegacy() {
            return new Result(true, true, 200, "Legacy session request accepted.");
        }

        static Result rejected(int status, String message) {
            return new Result(false, false, status, message);
        }
    }
}
