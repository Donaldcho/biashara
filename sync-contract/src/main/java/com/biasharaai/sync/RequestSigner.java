package com.biasharaai.sync;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RequestSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private RequestSigner() {
    }

    public static String sign(
        String method,
        String path,
        String protocolVersion,
        String requestId,
        String timestamp,
        String nonce,
        byte[] body,
        String sessionSecret
    ) {
        if (sessionSecret == null || sessionSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Session secret is required.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sessionSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(canonicalRequest(
                method,
                path,
                protocolVersion,
                requestId,
                timestamp,
                nonce,
                body
            ).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", ex);
        }
    }

    public static boolean verify(
        String signature,
        String method,
        String path,
        String protocolVersion,
        String requestId,
        String timestamp,
        String nonce,
        byte[] body,
        String sessionSecret
    ) {
        if (signature == null || signature.trim().isEmpty()) {
            return false;
        }
        String expected = sign(method, path, protocolVersion, requestId, timestamp, nonce, body, sessionSecret);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
    }

    public static String sha256(byte[] body) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(safeBody(body)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    static String canonicalRequest(
        String method,
        String path,
        String protocolVersion,
        String requestId,
        String timestamp,
        String nonce,
        byte[] body
    ) {
        return safe(method).toUpperCase(Locale.ROOT) + "\n"
            + safe(path) + "\n"
            + safe(protocolVersion) + "\n"
            + safe(requestId) + "\n"
            + safe(timestamp) + "\n"
            + safe(nonce) + "\n"
            + sha256(body);
    }

    private static byte[] safeBody(byte[] body) {
        return body == null ? new byte[0] : body;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String toHex(byte[] value) {
        char[] output = new char[value.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < value.length; index++) {
            int current = value[index] & 0xff;
            output[index * 2] = digits[current >>> 4];
            output[index * 2 + 1] = digits[current & 0x0f];
        }
        return new String(output);
    }
}
