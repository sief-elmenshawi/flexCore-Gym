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
 * Periodically reconciles stored subscription status with the passage of time:
 * - flips overdue ACTIVE subscriptions to EXPIRED
 * - auto-thaws FROZEN subscriptions whose freeze period fully elapsed
 *   (endDate was already extended at freeze time, so no days are refunded)
 * Services additionally validate endDate lazily at read time, so correctness
 * never depends on this job having run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "${app.jobs.subscription-maintenance-cron:0 */30 * * * *}")
    @Transactional
    public void maintainSubscriptionStates() {
        LocalDateTime now = LocalDateTime.now();
        int thawed = subscriptionRepository.thawElapsedFreezes(now);
        int expired = subscriptionRepository.expireOverdue(now, SubscriptionStatus.EXPIRED);
        if (thawed > 0 || expired > 0) {
            log.info("Subscription maintenance: {} freeze(s) thawed, {} overdue subscription(s) expired",
                    thawed, expired);
        }
    }
}
