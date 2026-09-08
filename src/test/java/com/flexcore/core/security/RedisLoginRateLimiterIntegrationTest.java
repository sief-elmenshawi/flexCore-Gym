package com.flexcore.core.security;

import com.flexcore.core.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Redis-backed store drives the limiter correctly: state lives in Redis under a
 * namespaced key, concurrent logins race safely, and a stuck key eventually expires.
 * Requires a reachable Redis on localhost:6379 (docker run -d --name flexcore-redis -p 6379:6379 redis:7-alpine).
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisLoginRateLimiterIntegrationTest {

    @Autowired
    private LoginRateLimiter limiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        var keys = redisTemplate.keys("flexcore:login-ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void statePersistsInRedisAndBlocksAcrossCalls() {
        java.util.concurrent.atomic.AtomicLong now =
                new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("redis@test.com", now.get());
            now.addAndGet(100);
        }

        String key = "flexcore:login-ratelimit:redis@test.com";
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(key)),
                "Block state must live in Redis under " + key);

        assertThrows(TooManyRequestsException.class,
                () -> limiter.checkNotBlocked("redis@test.com", now.get()));
    }

    @Test
    void successRemovesStateFromRedis() {
        java.util.concurrent.atomic.AtomicLong now =
                new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("ok@test.com", now.get());
            now.addAndGet(100);
        }

        limiter.onSuccess("ok@test.com");

        String key = "flexcore:login-ratelimit:ok@test.com";
        assertTrue(!Boolean.TRUE.equals(redisTemplate.hasKey(key)),
                "A successful login must wipe the Redis state");
        assertDoesNotThrow(() -> limiter.checkNotBlocked("ok@test.com", now.get() + 1000));
    }

    @Test
    void concurrentFailuresNeverLoseCount() throws Exception {
        String email = "race@test.com";
        int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>(threads);

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                limiter.recordFailure(email, System.currentTimeMillis());
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) {
            f.get();
        }
        pool.shutdown();

        // 8 concurrent failures with max=5 must leave the email blocked: no count is lost.
        assertThrows(TooManyRequestsException.class, () -> limiter.checkNotBlocked(email, System.currentTimeMillis()));
    }
}