package com.flexcore.core.security;

/**
 * Per-email throttling state. Timestamps are stored as epoch milliseconds so the same
 * representation survives deterministically in both in-memory and Redis storage.
 */
public record AttemptState(int failures, long windowStartEpochMillis, long blockedUntilEpochMillis) {

    /**
     * Policy behind every attempt recorded by {@link LoginRateLimiter} — the single
     * source of truth used by the in-memory store. The Redis store runs the exact same
     * transition atomically inside a Lua script (see {@code RedisLoginAttemptStore}),
     * so the two back-ends can never drift.
     *
     * @param current            state before this failure, or {@code null}
     * @param nowEpochMillis     point in time the failure happens
     * @param maxFailedAttempts  failures allowed inside the failure window
     * @param failureWindowMillis sliding window length
     * @param blockDurationMillis how long access is rejected after the threshold
     */
    public static AttemptState next(AttemptState current, long nowEpochMillis,
                                    int maxFailedAttempts, long failureWindowMillis, long blockDurationMillis) {
        // A still-running block is preserved: further failures neither extend nor clear it.
        if (current != null && current.isBlocked(nowEpochMillis)) {
            return new AttemptState(0, nowEpochMillis, current.blockedUntilEpochMillis());
        }
        int failures;
        long windowStart;
        if (current == null || current.windowElapsed(nowEpochMillis, failureWindowMillis)) {
            failures = 1;
            windowStart = nowEpochMillis;
        } else {
            failures = current.failures() + 1;
            windowStart = current.windowStartEpochMillis();
        }
        long blockedUntil = 0;
        if (failures >= maxFailedAttempts) {
            blockedUntil = nowEpochMillis + blockDurationMillis;
            failures = 0;
            windowStart = nowEpochMillis;
        }
        return new AttemptState(failures, windowStart, blockedUntil);
    }

    /** 0 when not currently blocked. */
    public long blockedUntilEpochMillis() {
        return blockedUntilEpochMillis;
    }

    public boolean isBlocked(long nowEpochMillis) {
        return blockedUntilEpochMillis > nowEpochMillis;
    }

    public boolean windowElapsed(long nowEpochMillis, long failureWindowMillis) {
        return windowStartEpochMillis + failureWindowMillis < nowEpochMillis;
    }
}