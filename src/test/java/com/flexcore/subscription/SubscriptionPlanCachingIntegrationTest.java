package com.flexcore.subscription;

import com.flexcore.subscription.dto.request.CreateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.request.UpdateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.service.SubscriptionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test proving Redis caching behaves correctly end-to-end:
 *  - read path is cached (the list is present in the Redis-backed {@code plans} cache
 *    under the configured key {@code flexcore:plans::all} after the first read)
 *  - invalidation works (updating a plan evicts the stale cached list)
 * Requires a reachable test database AND a reachable Redis on localhost:6379
 * (start with:  docker run -d --name flexcore-redis -p 6379:6379 redis:7-alpine).
 * <p>
 * All cache interactions go through the application's own {@link CacheManager} (the same
 * {@code RedisCache}/{@code RedisCacheWriter} the service uses), so the clear, the reads
 * and the service's writes share one coherent connection pathway instead of racing raw
 * {@link StringRedisTemplate} commands against the cache writer across pooled connections.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionPlanCachingIntegrationTest {

    private static final String CONFIGURED_CACHE_KEY = "flexcore:plans::all";

    @Autowired
    private SubscriptionPlanService planService;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearCacheAndData() throws InterruptedException {
        planRepository.deleteAll();
        plans().clear();
        // The cache writer dispatches commands over as many connections as the pool has;
        // wait for the eviction to be visible before the test re-populates, so a slow
        // eviction can never wipe the value put by the test body.
        awaitCachedState(false);
    }

    private Cache plans() {
        return cacheManager.getCache("plans");
    }

    private boolean isCached() {
        Cache.ValueWrapper cached = plans().get("all");
        return cached != null && cached.get() != null;
    }

    /**
     * Poll the cached state instead of asserting immediately: the Redis-backed cache
     * writes through the Lettuce pool, so the observable store lags the calling thread
     * by however long the current round-trip takes. Await it with a deadline so a slow
     * moment on a loaded runner cannot turn into a race.
     */
    private void awaitCachedState(boolean expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (isCached() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Redis cache state did not reach expected=" + expected
                + " within 20s (last observed=" + isCached() + ")");
    }

    @Test
    void getAll_isCached_storedInRedisUnderConfiguredKey() throws InterruptedException {
        planService.create(buildCreateRequest("Basic", "100.00"));

        List<SubscriptionPlanResponse> result = planService.getAll();
        assertEquals(1, result.size());

        awaitCachedState(true);

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(CONFIGURED_CACHE_KEY)),
                "The plans list must be stored in Redis under the configured cache key");

        List<SubscriptionPlanResponse> second = planService.getAll();
        assertEquals("Basic", second.get(0).getName(), "Second read returns the cached values");
        assertEquals(0, new BigDecimal("100.00").compareTo(second.get(0).getPrice()));
    }

    @Test
    void update_evictsCache_nextReadFetchesFreshDataFromDb() throws InterruptedException {
        SubscriptionPlanResponse created = planService.create(buildCreateRequest("Basic", "100.00"));

        planService.getAll(); // populate the cache
        awaitCachedState(true);

        UpdateSubscriptionPlanRequest update = new UpdateSubscriptionPlanRequest();
        update.setName("Basic Plus");
        update.setPrice(new BigDecimal("149.00"));
        update.setDurationInDays(30);
        update.setMaxFamilyMembers(0);
        planService.update(created.getId(), update);

        // The eviction may land over a different pooled connection than the put; wait for it.
        awaitCachedState(false);

        List<SubscriptionPlanResponse> refreshed = planService.getAll();
        assertEquals(1, refreshed.size());
        assertEquals("Basic Plus", refreshed.get(0).getName(),
                "Refreshed list must come from the DB, not a stale cached copy");
        assertEquals(0, new BigDecimal("149.00").compareTo(refreshed.get(0).getPrice()));
    }

    private CreateSubscriptionPlanRequest buildCreateRequest(String name, String price) {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setDurationInDays(30);
        request.setMaxFamilyMembers(0);
        return request;
    }
}