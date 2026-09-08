package com.flexcore.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the rate-limit transition that {@link RedisLoginAttemptStore} mirrors in Lua,
 * so the two back-ends provably cannot drift.
 */
class AttemptStateTest {

    private static final long T0 = epoch(2026, 8, 22, 12, 0);
    private static final long MINUTE = 60_000L;
    private static final int MAX = 5;
    private static final long WINDOW = 15 * MINUTE;
    private static final long BLOCK = 5 * MINUTE;

    @Test
    void freshEmailStartsWithOneFailure() {
        AttemptState next = AttemptState.next(null, T0, MAX, WINDOW, BLOCK);
        assertEquals(1, next.failures());
        assertEquals(T0, next.windowStartEpochMillis());
        assertEquals(0, next.blockedUntilEpochMillis());
        assertFalse(next.isBlocked(T0 + MINUTE));
    }

    @Test
    void failuresAccumulateInsideWindow() {
        AttemptState one = AttemptState.next(null, T0, MAX, WINDOW, BLOCK);
        AttemptState two = AttemptState.next(one, T0 + MINUTE, MAX, WINDOW, BLOCK);
        assertEquals(2, two.failures());
        assertEquals(T0, two.windowStartEpochMillis());
    }

    @Test
    void failureAfterWindowStartsFreshCount() {
        AttemptState one = AttemptState.next(null, T0, MAX, WINDOW, BLOCK);
        AttemptState next = AttemptState.next(one, T0 + 16 * MINUTE, MAX, WINDOW, BLOCK);
        assertEquals(1, next.failures());
        assertEquals(T0 + 16 * MINUTE, next.windowStartEpochMillis());
    }

    @Test
    void thresholdBlocksAndResetsCount() {
        AttemptState state = null;
        for (int i = 0; i < MAX; i++) {
            state = AttemptState.next(state, T0 + i * 100, MAX, WINDOW, BLOCK);
        }
        assertEquals(0, state.failures());
        assertEquals(T0 + (MAX - 1) * 100, state.windowStartEpochMillis());
        assertEquals(T0 + (MAX - 1) * 100 + BLOCK, state.blockedUntilEpochMillis());
        assertTrue(state.isBlocked(T0 + MINUTE));
    }

    @Test
    void blockIsPreservedWhileActive() {
        AttemptState state = null;
        for (int i = 0; i < MAX; i++) {
            state = AttemptState.next(state, T0 + i * 100, MAX, WINDOW, BLOCK);
        }
        long blockedUntil = state.blockedUntilEpochMillis();

        AttemptState duringBlock = AttemptState.next(state, T0 + MINUTE, MAX, WINDOW, BLOCK);
        assertEquals(blockedUntil, duringBlock.blockedUntilEpochMillis(),
                "An active block must not be extended or cleared by further failures");
        assertEquals(0, duringBlock.failures());
        assertEquals(T0 + MINUTE, duringBlock.windowStartEpochMillis());
    }

    @Test
    void countingResumesAfterBlockExpires() {
        AttemptState state = null;
        for (int i = 0; i < MAX; i++) {
            state = AttemptState.next(state, T0 + i * 100, MAX, WINDOW, BLOCK);
        }
        long afterBlock = T0 + 6 * MINUTE; // block expired at T0 + 5min
        assertFalse(state.isBlocked(afterBlock));

        AttemptState resumed = AttemptState.next(state, afterBlock, MAX, WINDOW, BLOCK);
        assertEquals(1, resumed.failures());
        assertEquals(0, resumed.blockedUntilEpochMillis());
    }

    private static long epoch(int year, int month, int day, int hour, int minute) {
        return java.time.LocalDateTime.of(year, month, day, hour, minute)
                .atZone(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }
}