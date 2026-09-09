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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test proving Redis caching behaves correctly end-to-end:
 *  - read path is cached (the list is present in Redis after the first read)
 *  - invalidation works (updating a plan evicts the stale cached list)
 * Requires a reachable test database AND a reachable Redis on localhost:6379
 * (start with:  docker run -d --name flexcore-redis -p 6379:6379 redis:7-alpine).
 */
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionPlanCachingIntegrationTest {

    @Autowired
    private SubscriptionPlanService planService;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearCacheAndData() throws InterruptedException {
        planRepository.deleteAll();
        redisTemplate.delete("flexcore:plans::all");
        // The DELETE above is dispatched over a pooled connection; LLn the same pool the
        // test body's subsequent SET (put) can land before the DELETE is visible. Wait for
        // the key to be confirmed gone so no stale delete can wipe a later put.
        awaitCachedState(false);
    }

    private boolean isCached() {
        return Boolean.TRUE.equals(redisTemplate.hasKey("flexcore:plans::all"));
    }

    /**
     * Lettuce dispatches cache commands over a small connection pool, so two consecutive
     * operations (e.g. clear() then put()) can race across different connections. Poll the
     * Redis state with a short deadline instead of asserting immediately.
     */
    private void awaitCachedState(boolean expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (isCached() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Redis cache state did not reach expected=" + expected
                + " within 15s (last observed=" + isCached() + ")");
    }

    @Test
    void getAll_isCached_storedInRedisUnderConfiguredKey() throws InterruptedException {
        planService.create(buildCreateRequest("Basic", "100.00"));

        List<SubscriptionPlanResponse> result = planService.getAll();
        assertEquals(1, result.size());

        awaitCachedState(true);

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

        // Lettuce can dispatch the eviction over a different pooled connection; wait for it.
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
