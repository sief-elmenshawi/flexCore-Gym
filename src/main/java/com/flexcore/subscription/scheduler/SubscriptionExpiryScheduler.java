package com.flexcore.subscription.scheduler;

import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Periodically flips overdue ACTIVE subscriptions to EXPIRED so the stored
 * status stays accurate. Services additionally validate endDate lazily at
 * read time, so correctness never depends on this job having run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void expireOverdueSubscriptions() {
        int expired = subscriptionRepository.expireOverdue(LocalDateTime.now(), SubscriptionStatus.EXPIRED);
        if (expired > 0) {
            log.info("Subscription expiry job marked {} overdue subscription(s) as EXPIRED", expired);
        }
    }
}
