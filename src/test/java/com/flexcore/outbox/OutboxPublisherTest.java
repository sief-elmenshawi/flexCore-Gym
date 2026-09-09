package com.flexcore.outbox;

import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.enums.PublishOutcome;
import com.flexcore.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, processor, 100);
    }

    @Test
    void publishesEachDueEventAndCountsOnlySuccessfulOnes() {
        OutboxEvent first = pendingEvent(1L);
        OutboxEvent second = pendingEvent(2L);

        when(repository.findPendingEvents(any(Pageable.class), eq(OutboxStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        when(processor.publish(first.getId())).thenReturn(PublishOutcome.PUBLISHED);
        when(processor.publish(second.getId())).thenReturn(PublishOutcome.PUBLISHED);

        assertEquals(2, publisher.publishPendingEvents());
        verify(processor).publish(first.getId());
        verify(processor).publish(second.getId());
    }

    @Test
    void failedAndUnhandledEventsAreNotCountedAsPublished() {
        OutboxEvent failed = pendingEvent(1L);
        OutboxEvent unhandled = pendingEvent(2L);

        when(repository.findPendingEvents(any(Pageable.class), eq(OutboxStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(failed, unhandled));
        when(processor.publish(failed.getId())).thenReturn(PublishOutcome.FAILED);
        when(processor.publish(unhandled.getId())).thenReturn(PublishOutcome.UNHANDLED);

        assertEquals(0, publisher.publishPendingEvents());
    }

    @Test
    void batchSizeIsAppliedToTheCandidateFetch() {
        OutboxPublisher smallBatch = new OutboxPublisher(repository, processor, 5);
        when(repository.findPendingEvents(any(Pageable.class), eq(OutboxStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertEquals(0, smallBatch.publishPendingEvents());
        verify(repository).findPendingEvents(
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 5),
                eq(OutboxStatus.PENDING), any(LocalDateTime.class));
        verify(processor, never()).publish(any());
    }

    private OutboxEvent pendingEvent(Long id) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateType("test_aggregate")
                .aggregateId(1L)
                .eventType("test.event")
                .payload("{\"id\":" + id + ",\"name\":\"hiit\"}")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}