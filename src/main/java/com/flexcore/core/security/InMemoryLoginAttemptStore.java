package com.flexcore.core.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link LoginAttemptStore} backed by a {@link ConcurrentHashMap}.
 * Keeps the original local-only semantics and is what the deterministic unit tests
 * exercise without needing a running Redis.
 * <p>
 * Registered only when {@code app.security.login-rate-limit.store=in-memory} — a single-node
 * fallback for local development or environments without Redis. State does not survive a
 * restart and is not shared across instances; use {@link RedisLoginAttemptStore} (the default)
 * for any multi-instance deployment.
 */
@Component
@ConditionalOnProperty(name = "app.security.login-rate-limit.store", havingValue = "in-memory")
public final class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    @Override
    public AttemptState get(String email) {
        return attempts.get(email);
    }

    @Override
    public AttemptState compute(String email, int maxFailedAttempts, long failureWindowMillis,
                                long blockDurationMillis, long nowEpochMillis, Duration ttl) {
        return attempts.compute(email, (key, current) ->
                AttemptState.next(current, nowEpochMillis, maxFailedAttempts, failureWindowMillis, blockDurationMillis));
    }

    @Override
    public void remove(String email) {
        attempts.remove(email);
    }

    @Override
    public void removeIfExpired(long nowEpochMillis, long failureWindowMillis) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState s = entry.getValue();
            boolean blockElapsed = s.blockedUntilEpochMillis() == 0 || s.blockedUntilEpochMillis() <= nowEpochMillis;
            boolean windowElapsed = s.windowStartEpochMillis() + failureWindowMillis < nowEpochMillis;
            return blockElapsed && windowElapsed;
        });
    }
}