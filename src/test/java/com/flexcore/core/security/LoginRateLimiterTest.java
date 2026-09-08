package com.flexcore.core.security;

import com.flexcore.core.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    private static final long T0 = epoch(2026, 8, 22, 12, 0);

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter(new InMemoryLoginAttemptStore(), 5, 15, 5);
    }

    @Test
    void allowsUpToMaxMinusOneFailures() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a@test.com", T0);
        }
        assertDoesNotThrow(() -> limiter.checkNotBlocked("a@test.com", T0 + minutes(1)));
    }

    @Test
    void blocksAfterMaxFailures() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("a@test.com", T0);
        }
        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkNotBlocked("a@test.com", T0 + minutes(1)));
    }

    @Test
    void blockExpiresAfterBlockDuration() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("a@test.com", T0);
        }
        assertDoesNotThrow(() -> limiter.checkNotBlocked("a@test.com", T0 + minutes(6)));
    }

    @Test
    void successfulLoginResetsCounter() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a@test.com", T0);
        }
        limiter.onSuccess("a@test.com");
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a@test.com", T0 + minutes(2));
        }
        assertDoesNotThrow(() -> limiter.checkNotBlocked("a@test.com", T0 + minutes(3)));
    }

    @Test
    void failuresOutsideWindowDoNotAccumulate() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a@test.com", T0);
        }
        // window is 15 minutes: a failure at +16m starts a fresh count
        limiter.recordFailure("a@test.com", T0 + minutes(16));
        assertDoesNotThrow(() -> limiter.checkNotBlocked("a@test.com", T0 + minutes(17)));
    }

    @Test
    void emailsAreIsolated() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("a@test.com", T0);
        }
        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkNotBlocked("a@test.com", T0 + minutes(1)));
        assertDoesNotThrow(() -> limiter.checkNotBlocked("b@test.com", T0 + minutes(1)));
    }

    @Test
    void sweepRemovesOnlyStaleEntries() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("stale@test.com", T0);
        }
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("fresh@test.com", T0 + minutes(10));
        }
        // stale: blocked until T0+5 and window elapsed by T0+16 → removed
        limiter.sweepExpired(T0 + minutes(16));
        assertDoesNotThrow(() -> limiter.checkNotBlocked("stale@test.com", T0 + minutes(16)));

        // fresh entry survived the sweep and keeps counting within its window:
        // a 4th failure does not block yet, the 5th does
        limiter.recordFailure("fresh@test.com", T0 + minutes(16));
        assertDoesNotThrow(() -> limiter.checkNotBlocked("fresh@test.com", T0 + minutes(16) + 30_000L));
        limiter.recordFailure("fresh@test.com", T0 + minutes(17));
        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkNotBlocked("fresh@test.com", T0 + minutes(17) + 30_000L));
    }

    private static long epoch(int year, int month, int day, int hour, int minute) {
        return java.time.LocalDateTime.of(year, month, day, hour, minute)
                .atZone(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    private static long minutes(long m) {
        return m * 60_000L;
    }
}