package com.flexcore.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dispatches pending outbox events to their handlers and marks them PUBLISHED on
 * success. Failures keep the row PENDING so the next poll retries it (at-least-once
 * delivery). The optimistic lock is released on commit while unlocked rows are
 * skipped, so several worker instances can poll concurrently without
 * double-delivery of a single event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final OutboxEventHandlerRegistry eventHandlerRegistry;
    private final ObjectMapper objectMapper;

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> pending = repository.findPendingEvents(PageRequest.of(0, BATCH_SIZE), OutboxStatus.PENDING);
        int published = 0;
        for (OutboxEvent event : pending) {
            published += publishEvent(event);
        }
        if (published > 0 || !pending.isEmpty()) {
            log.info("Outbox poll: {} of {} pending event(s) published", published, pending.size());
        }
        return published;
    }

    private int publishEvent(OutboxEvent event) {
        OutboxEventHandler<?> handler = eventHandlerRegistry.find(event.getEventType()).orElse(null);
        if (handler == null) {
            log.warn("No handler registered for outbox event type '{}'; event {} stays pending",
                    event.getEventType(), event.getId());
            return 0;
        }
        try {
            Object payload = objectMapper.readValue(event.getPayload(), handler.payloadType());
            dispatch(handler, payload);
            event.markPublished();
            return 1;
        } catch (Exception ex) {
            log.error("Failed to dispatch outbox event {} ({}); leaving it pending for retry",
                    event.getId(), event.getEventType(), ex);
            event.recordFailure(ex);
            return 0;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dispatch(OutboxEventHandler handler, Object payload) {
        handler.handle(payload);
    }
}