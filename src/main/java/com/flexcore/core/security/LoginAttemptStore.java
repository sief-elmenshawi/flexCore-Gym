package com.flexcore.core.security;

import java.time.Duration;

/**
 * Pluggable state store for {@link LoginRateLimiter}.
 * <p>
 * The in-memory implementation keeps the deterministic, time-injected behaviour that the
 * core logic depends on; the Redis implementation runs the same transition atomically
 * inside a Lua script so the state is shared safely across application instances.
 */
public interface LoginAttemptStore {

    AttemptState get(String email);

    /**
     * Atomically applies {@link AttemptState#next} — with the given policy parameters —
     * to the stored state and persists the result. Implementations must guarantee that
     * concurrent modifications can never lose a failure count.
     *
     * @param email              the throttled identity
     * @param maxFailedAttempts  failures allowed inside the failure window
     * @param failureWindowMillis sliding window length
     * @param blockDurationMillis how long access is rejected after the threshold
     * @param nowEpochMillis     point in time the failure happens
     * @param ttl                how long the key lives without further activity
     * @return the resulting stored state
     */
    AttemptState compute(String email, int maxFailedAttempts, long failureWindowMillis,
                         long blockDurationMillis, long nowEpochMillis, Duration ttl);

    void remove(String email);

    /**
     * Drops state that is neither blocking anymore nor inside an active failure window.
     * In-memory storage uses this for the scheduled sweep; Redis storage relies on TTL and
     * treats this as a no-op.
     */
    void removeIfExpired(long nowEpochMillis, long failureWindowMillis);
}