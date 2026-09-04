package com.biasharaai.desktop.v2;

import java.time.Clock;

final class PairingAttemptLimiter {
    private final Clock clock;
    private final int maximumFailures;
    private final long lockoutMillis;

    private int failures;
    private long lockedUntil;

    PairingAttemptLimiter() {
        this(Clock.systemUTC(), 5, 30_000L);
    }

    PairingAttemptLimiter(Clock clock, int maximumFailures, long lockoutMillis) {
        this.clock = clock;
        this.maximumFailures = maximumFailures;
        this.lockoutMillis = lockoutMillis;
    }

    synchronized long retryAfterSeconds() {
        long remaining = lockedUntil - clock.millis();
        if (remaining <= 0) {
            if (lockedUntil > 0) {
                failures = 0;
                lockedUntil = 0;
            }
            return 0;
        }
        return Math.max(1, (remaining + 999L) / 1000L);
    }

    synchronized boolean recordFailure() {
        failures++;
        if (failures < maximumFailures) {
            return false;
        }
        lockedUntil = clock.millis() + lockoutMillis;
        return true;
    }

    synchronized void reset() {
        failures = 0;
        lockedUntil = 0;
    }
}
