package com.flexcore.core.security;

import com.flexcore.core.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Throttles repeated failed login attempts per email address.
 * After {@code maxFailedAttempts} failures inside {@code failureWindow},
 * further logins for that email are rejected for {@code blockDuration}.
 * A successful login clears the counter.
 * <p>
 * Storage is pluggable: {@link InMemoryLoginAttemptStore} for local/single-node use and
 * {@link RedisLoginAttemptStore} for distributed deployments. Read-modify-write of the
 * attempt state is atomic in both back-ends, so concurrent logins cannot lose a failure.
 * <p>
 * Time is injected through the package-private overloads so expiry, window resets and
 * sweeping are unit-tested deterministically without sleeping threads.
 */
@Component
public class LoginRateLimiter {

    private final LoginAttemptStore store;
    private final int maxFailedAttempts;
    private final Duration failureWindow;
    private final Duration blockDuration;

    public LoginRateLimiter(
            LoginAttemptStore store,
            @Value("${app.security.login-rate-limit.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login-rate-limit.failure-window-minutes:15}") long failureWindowMinutes,
            @Value("${app.security.login-rate-limit.block-minutes:5}") long blockMinutes) {
        this.store = store;
        this.maxFailedAttempts = maxFailedAttempts;
        this.failureWindow = Duration.ofMinutes(failureWindowMinutes);
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public void checkNotBlocked(String email) {
        checkNotBlocked(email, System.currentTimeMillis());
    }

    public void recordFailure(String email) {
        recordFailure(email, System.currentTimeMillis());
    }

    /** Successful authentication wipes any accumulated failures. */
    public void onSuccess(String email) {
        store.remove(email);
    }

    void checkNotBlocked(String email, long nowEpochMillis) {
        AttemptState state = store.get(email);
        if (state != null && state.isBlocked(nowEpochMillis)) {
            long secondsLeft = Math.max(0, (state.blockedUntilEpochMillis() - nowEpochMillis) / 1000);
            long minutesLeft = Math.max(1, (secondsLeft + 59) / 60);
            throw new TooManyRequestsException("error.login.rate-limited", minutesLeft);
        }
    }

    void recordFailure(String email, long nowEpochMillis) {
        store.compute(email, maxFailedAttempts, failureWindow.toMillis(), blockDuration.toMillis(),
                nowEpochMillis, failureWindow.plus(blockDuration));
    }

    /** Evicts entries whose failure window elapsed and that are no longer blocked. */
    @Scheduled(fixedDelay = 3_600_000)
    public void sweepExpired() {
        sweepExpired(System.currentTimeMillis());
    }

    void sweepExpired(long nowEpochMillis) {
        store.removeIfExpired(nowEpochMillis, failureWindow.toMillis());
    }
}