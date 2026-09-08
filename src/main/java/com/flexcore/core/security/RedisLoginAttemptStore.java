package com.flexcore.core.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed {@link LoginAttemptStore}.
 * <p>
 * State lives under {@code flexcore:login-ratelimit:<email>} as a JSON document. Every failure
 * is recorded by a single atomic Lua script (read → apply {@link AttemptState#next} → write with
 * a fresh TTL), so a distributed deployment can never lose a failure count, and the operation
 * costs exactly one round-trip — no WATCH/MULTI/EXEC sessions, no retries. The script is a mirror
 * of {@link AttemptState#next}; keep the two in lock-step. The TTL garbage-collects stale keys
 * automatically, so no scheduled sweep is needed in distributed deployments.
 */
@Component
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final String KEY_PREFIX = "flexcore:login-ratelimit:";

    /**
     * Atomic state transition mirroring {@link AttemptState#next}.
     * KEYS[1] = state key; ARGV: nowEpochMillis, failureWindowMillis, blockDurationMillis,
     * maxFailedAttempts, ttlSeconds. Returns the JSON of the new state.
     */
    private static final String TRANSITION_LUA = """
            local nowMillis = tonumber(ARGV[1])
            local failureWindowMillis = tonumber(ARGV[2])
            local blockDurationMillis = tonumber(ARGV[3])
            local maxFailedAttempts = tonumber(ARGV[4])
            local ttlSeconds = tonumber(ARGV[5])

            local function encode(failures, windowStart, blockedUntil)
              return cjson.encode({
                failures = failures,
                windowStartEpochMillis = windowStart,
                blockedUntilEpochMillis = blockedUntil
              })
            end

            local raw = redis.call('GET', KEYS[1])
            local failures, windowStart, blockedUntil = 0, nowMillis, 0

            if raw then
              local state = cjson.decode(raw)
              failures = state.failures
              windowStart = state.windowStartEpochMillis
              blockedUntil = state.blockedUntilEpochMillis
              -- A still-running block is preserved: it is neither extended nor cleared.
              if blockedUntil > nowMillis then
                local out = encode(0, nowMillis, blockedUntil)
                redis.call('SET', KEYS[1], out, 'EX', ttlSeconds)
                return out
              end
            end

            if windowStart + failureWindowMillis < nowMillis then
              failures = 1
              windowStart = nowMillis
            else
              failures = failures + 1
            end

            local newBlockedUntil = 0
            if failures >= maxFailedAttempts then
              newBlockedUntil = nowMillis + blockDurationMillis
              failures = 0
              windowStart = nowMillis
            end

            local out = encode(failures, windowStart, newBlockedUntil)
            redis.call('SET', KEYS[1], out, 'EX', ttlSeconds)
            return out
            """;

    private static final RedisScript<String> TRANSITION_SCRIPT = new DefaultRedisScript<>(TRANSITION_LUA, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisLoginAttemptStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AttemptState get(String email) {
        String raw = redisTemplate.opsForValue().get(key(email));
        return raw == null ? null : deserialize(raw);
    }

    @Override
    public AttemptState compute(String email, int maxFailedAttempts, long failureWindowMillis,
                                long blockDurationMillis, long nowEpochMillis, Duration ttl) {
        String json = redisTemplate.execute(TRANSITION_SCRIPT, List.of(key(email)),
                String.valueOf(nowEpochMillis), String.valueOf(failureWindowMillis),
                String.valueOf(blockDurationMillis), String.valueOf(maxFailedAttempts),
                String.valueOf(ttl.toSeconds()));
        if (json == null) {
            throw new IllegalStateException("Redis rate-limit script returned no value for " + key(email));
        }
        return deserialize(json);
    }

    @Override
    public void remove(String email) {
        redisTemplate.delete(key(email));
    }

    @Override
    public void removeIfExpired(long nowEpochMillis, long failureWindowMillis) {
        // Keys carry a Redis TTL that expires automatically; nothing to sweep.
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }

    private AttemptState deserialize(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, AttemptState.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}