package com.flexcore.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the outbox worker on a fixed poll interval. Kept apart from
 * {@link OutboxPublisher} so tests (or embedded deployments) can run the publisher
 * manually with {@code app.jobs.outbox-poll-enabled: false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.outbox-poll-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollScheduler {

    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${app.jobs.outbox-poll-delay-ms:5000}")
    public void poll() {
        outboxPublisher.publishPendingEvents();
    }
}