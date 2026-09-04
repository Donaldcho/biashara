package com.biasharaai.desktop.v2;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairingAttemptLimiterTest {
    @Test
    void locksAfterFailureLimitAndRecoversAfterDelay() {
        MutableClock clock = new MutableClock(1_800_000_000_000L);
        PairingAttemptLimiter limiter = new PairingAttemptLimiter(clock, 3, 30_000L);

        assertFalse(limiter.recordFailure());
        assertFalse(limiter.recordFailure());
        assertTrue(limiter.recordFailure());
        assertEquals(30, limiter.retryAfterSeconds());

        clock.advanceMillis(30_001L);
        assertEquals(0, limiter.retryAfterSeconds());
        assertFalse(limiter.recordFailure());
    }

    @Test
    void successfulPairingResetsFailures() {
        MutableClock clock = new MutableClock(1_800_000_000_000L);
        PairingAttemptLimiter limiter = new PairingAttemptLimiter(clock, 2, 30_000L);

        assertFalse(limiter.recordFailure());
        limiter.reset();

        assertFalse(limiter.recordFailure());
        assertEquals(0, limiter.retryAfterSeconds());
    }

    private static final class MutableClock extends Clock {
        private long currentMillis;

        private MutableClock(long currentMillis) {
            this.currentMillis = currentMillis;
        }

        void advanceMillis(long millis) {
            currentMillis += millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(currentMillis);
        }

        @Override
        public long millis() {
            return currentMillis;
        }
    }
}
