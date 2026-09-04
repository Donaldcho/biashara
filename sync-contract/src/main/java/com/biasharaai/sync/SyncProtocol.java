package com.biasharaai.sync;

import java.util.Collections;
import java.util.Set;

public final class SyncProtocol {
    public static final String CURRENT_VERSION = "1.0";
    public static final Set<String> SUPPORTED_VERSIONS = Collections.singleton(CURRENT_VERSION);
    public static final String AUTHENTICATION = "HMAC-SHA256";

    public static final String HEADER_SESSION = "X-Biashara-Session";
    public static final String HEADER_PROTOCOL = "X-Biashara-Protocol";
    public static final String HEADER_REQUEST_ID = "X-Biashara-Request-Id";
    public static final String HEADER_TIMESTAMP = "X-Biashara-Timestamp";
    public static final String HEADER_NONCE = "X-Biashara-Nonce";
    public static final String HEADER_SIGNATURE = "X-Biashara-Signature";

    public static final long MAX_CLOCK_SKEW_MILLIS = 5L * 60L * 1000L;
    public static final int MAX_BODY_BYTES = 12 * 1024 * 1024;

    private SyncProtocol() {
    }
}
