package com.flexcore.core.security;

import com.flexcore.core.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles repeated failed login attempts per email address.
 * After {@code maxFailedAttempts} failures inside {@code failureWindow},
 * further logins for that email are rejected for {@code blockDuration}.
 * A successful login clears the counter. State is per-instance and in-memory,
 * which matches the current single-node deployment.
 */
@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxFailedAttempts;
    private final Duration failureWindow;
    private final Duration blockDuration;

    public LoginRateLimiter(
            @Value("${app.security.login-rate-limit.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login-rate-limit.failure-window-minutes:15}") long failureWindowMinutes,
            @Value("${app.security.login-rate-limit.block-minutes:5}") long blockMinutes) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.failureWindow = Duration.ofMinutes(failureWindowMinutes);
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public void checkNotBlocked(String email) {
        checkNotBlocked(email, LocalDateTime.now());
    }

    public void recordFailure(String email) {
        recordFailure(email, LocalDateTime.now());
    }

    /** Successful authentication wipes any accumulated failures. */
    public void onSuccess(String email) {
        attempts.remove(email);
    }

    void checkNotBlocked(String email, LocalDateTime now) {
        AttemptState state = attempts.get(email);
        if (state != null && state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
            long secondsLeft = Duration.between(now, state.blockedUntil).toSeconds();
            long minutesLeft = Math.max(1, (secondsLeft + 59) / 60);
            throw new TooManyRequestsException("error.login.rate-limited", minutesLeft);
        }
    }

    void recordFailure(String email, LocalDateTime now) {
        attempts.compute(email, (key, state) -> {
            if (state == null || state.windowStart.plus(failureWindow).isBefore(now)) {
                state = new AttemptState(now);
            }
            state.failures++;
            if (state.failures >= maxFailedAttempts) {
                state.blockedUntil = now.plus(blockDuration);
                state.failures = 0;
                state.windowStart = now;
            }
            return state;
        });
    }

    /** Evicts entries whose failure window elapsed and that are no longer blocked. */
    @Scheduled(fixedDelay = 3_600_000)
    public void sweepExpired() {
        sweepExpired(LocalDateTime.now());
    }

    void sweepExpired(LocalDateTime now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState s = entry.getValue();
            boolean blockElapsed = s.blockedUntil == null || !s.blockedUntil.isAfter(now);
            boolean windowElapsed = s.windowStart.plus(failureWindow).isBefore(now);
            return blockElapsed && windowElapsed;
        });
    }

    private static final class AttemptState {
        private int failures;
        private LocalDateTime windowStart;
        private LocalDateTime blockedUntil;

        private AttemptState(LocalDateTime windowStart) {
            this.windowStart = windowStart;
        }
    }
}
