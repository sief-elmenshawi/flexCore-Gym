package com.flexcore.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.enums.PublishOutcome;
import com.flexcore.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles a single outbox event in its own {@code REQUIRES_NEW} transaction. That is
 * the unit of failure isolation: a slow or failing handler never aborts the rest of the
 * poll batch, and the row-level pessimistic lock is held only for the duration of one
 * event's processing. Failures are written back with exponential backoff
 * ({@code next_attempt_at} = now + base * 2^attempts, capped), and once an event has
 * exceeded {@code app.jobs.outbox-max-attempts} it is dead-lettered to DEAD instead of
 * being retried forever.
 */
@Slf4j
@Component
public class OutboxEventProcessor {

    private final OutboxEventRepository repository;
    private final OutboxEventHandlerRegistry eventHandlerRegistry;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final long backoffBaseSeconds;
    private final long backoffCapSeconds;

    public OutboxEventProcessor(OutboxEventRepository repository,
                                OutboxEventHandlerRegistry eventHandlerRegistry,
                                ObjectMapper objectMapper,
                                @Value("${app.jobs.outbox-max-attempts:10}") int maxAttempts,
                                @Value("${app.jobs.outbox-backoff-base-seconds:5}") long backoffBaseSeconds,
                                @Value("${app.jobs.outbox-backoff-cap-seconds:3600}") long backoffCapSeconds) {
        this.repository = repository;
        this.eventHandlerRegistry = eventHandlerRegistry;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.backoffBaseSeconds = backoffBaseSeconds;
        this.backoffCapSeconds = backoffCapSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PublishOutcome publish(Long eventId) {
        OutboxEvent event = repository.findByIdForPublishing(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return PublishOutcome.SKIPPED;
        }
        OutboxEventHandler<?> handler = eventHandlerRegistry.find(event.getEventType()).orElse(null);
        if (handler == null) {
            log.warn("No handler registered for outbox event type '{}'; event {} stays pending",
                    event.getEventType(), event.getId());
            return PublishOutcome.UNHANDLED;
        }
        try {
            Object payload = objectMapper.readValue(event.getPayload(), handler.payloadType());
            dispatch(handler, payload);
            event.markPublished();
            return PublishOutcome.PUBLISHED;
        } catch (Exception ex) {
            event.recordFailure(ex, nextAttemptAt(event.getAttempts()));
            if (event.getAttempts() >= maxAttempts) {
                log.error("Outbox event {} ({}) exceeded {} attempts; dead-lettering it",
                        event.getId(), event.getEventType(), maxAttempts, ex);
                event.markDead();
            } else {
                log.warn("Failed to dispatch outbox event {} ({}); keeping it pending for retry at {}",
                        event.getId(), event.getEventType(), event.getNextAttemptAt(), ex);
            }
            return PublishOutcome.FAILED;
        }
    }

    private LocalDateTime nextAttemptAt(int previousAttempts) {
        long magnitude = 1L << Math.min(previousAttempts, 30);
        long delaySeconds = Math.min(backoffCapSeconds, backoffBaseSeconds * magnitude);
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dispatch(OutboxEventHandler handler, Object payload) {
        handler.handle(payload);
    }
}