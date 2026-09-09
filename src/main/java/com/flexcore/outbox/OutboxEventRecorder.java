package com.flexcore.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes an outbox row. MANDATORY propagation forces the row to be created inside
 * the caller's database transaction: the event commits or rolls back atomically
 * with the business change, which is the whole point of the outbox pattern.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(String eventType, String aggregateType, Long aggregateId, Object payload) {
        try {
            repository.save(OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize outbox event payload: " + eventType, ex);
        }
    }
}