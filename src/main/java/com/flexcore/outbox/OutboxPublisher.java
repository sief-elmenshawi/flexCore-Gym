package com.flexcore.outbox;

import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.enums.PublishOutcome;
import com.flexcore.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Poll worker: scans a bounded batch of due PENDING events and hands each one to
 * {@link OutboxEventProcessor}, which runs in its own transaction. The scan itself
 * takes no locks; claiming happens per event inside the processor's transaction, so
 * concurrent workers never double-deliver and one slow event never holds up the batch.
 */
@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final OutboxEventProcessor processor;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository repository,
                           OutboxEventProcessor processor,
                           @Value("${app.jobs.outbox-batch-size:100}") int batchSize) {
        this.repository = repository;
        this.processor = processor;
        this.batchSize = batchSize;
    }

    public int publishPendingEvents() {
        List<OutboxEvent> pending = repository.findPendingEvents(
                PageRequest.of(0, batchSize), OutboxStatus.PENDING, LocalDateTime.now());
        int published = 0;
        for (OutboxEvent event : pending) {
            if (processor.publish(event.getId()) == PublishOutcome.PUBLISHED) {
                published++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("Outbox poll: {} of {} pending event(s) published", published, pending.size());
        }
        return published;
    }
}