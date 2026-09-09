package com.flexcore.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.enums.PublishOutcome;
import com.flexcore.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the per-event outbox processor (retry/backoff/dead-lettering). The
 * {@code @Transactional(REQUIRES_NEW)} boundary is a Spring runtime concern and is not
 * exercised here; the integration test covers the real transaction wiring.
 */
class OutboxEventProcessorTest {

    private record TestEvent(Long id, String name) {
    }

    private static class RecordingHandler implements OutboxEventHandler<TestEvent> {

        final List<TestEvent> received = new ArrayList<>();

        @Override
        public String eventType() {
            return "test.event";
        }

        @Override
        public Class<TestEvent> payloadType() {
            return TestEvent.class;
        }

        @Override
        public void handle(TestEvent event) {
            received.add(event);
        }
    }

    private static class FlakyHandler implements OutboxEventHandler<TestEvent> {

        int calls;

        @Override
        public String eventType() {
            return "flaky.event";
        }

        @Override
        public Class<TestEvent> payloadType() {
            return TestEvent.class;
        }

        @Override
        public void handle(TestEvent event) {
            calls++;
            throw new IllegalStateException("boom");
        }
    }

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final RecordingHandler recordingHandler = new RecordingHandler();
    private final FlakyHandler flakyHandler = new FlakyHandler();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private OutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        var registry = new OutboxEventHandlerRegistry(List.of(recordingHandler, flakyHandler));
        processor = new OutboxEventProcessor(repository, registry, mapper, 3, 5, 3600);
    }

    @Test
    void publishesEventAndMarksItPublished() {
        OutboxEvent event = pendingEvent(10L, "test.event", 0);

        when(repository.findByIdForPublishing(10L)).thenReturn(Optional.of(event));

        assertEquals(PublishOutcome.PUBLISHED, processor.publish(10L));
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(List.of(new TestEvent(10L, "hiit")), recordingHandler.received);
    }

    @Test
    void failingHandlerKeepsEventPendingWithBackoffScheduled() {
        OutboxEvent event = pendingEvent(11L, "flaky.event", 0);

        when(repository.findByIdForPublishing(11L)).thenReturn(Optional.of(event));

        assertEquals(PublishOutcome.FAILED, processor.publish(11L));
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
        assertEquals("boom", event.getLastError());
        assertNotNull(event.getNextAttemptAt());
        assertTrue(event.getNextAttemptAt().isAfter(LocalDateTime.now().minusSeconds(1)));
        assertEquals(1, flakyHandler.calls);
    }

    @Test
    void eventReachingMaxAttemptsIsDeadLettered() {
        OutboxEvent event = pendingEvent(12L, "flaky.event", 2);

        when(repository.findByIdForPublishing(12L)).thenReturn(Optional.of(event));

        assertEquals(PublishOutcome.FAILED, processor.publish(12L));
        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertEquals(3, event.getAttempts());
        assertNull(event.getNextAttemptAt());
    }

    @Test
    void alreadyPublishedEventIsSkipped() {
        OutboxEvent event = pendingEvent(13L, "test.event", 0);
        event.markPublished();

        when(repository.findByIdForPublishing(13L)).thenReturn(Optional.of(event));

        assertEquals(PublishOutcome.SKIPPED, processor.publish(13L));
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(List.of(), recordingHandler.received);
    }

    @Test
    void lockedOrMissingEventIsSkipped() {
        when(repository.findByIdForPublishing(14L)).thenReturn(Optional.empty());

        assertEquals(PublishOutcome.SKIPPED, processor.publish(14L));
    }

    @Test
    void eventWithoutHandlerStaysPendingWithoutCountingAttempts() {
        OutboxEvent event = pendingEvent(15L, "nobody.handles", 0);

        when(repository.findByIdForPublishing(15L)).thenReturn(Optional.of(event));

        assertEquals(PublishOutcome.UNHANDLED, processor.publish(15L));
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertNull(event.getLastError());
        assertEquals(List.of(), recordingHandler.received);
        assertEquals(0, flakyHandler.calls);
    }

    private OutboxEvent pendingEvent(Long id, String eventType, int attempts) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateType("test_aggregate")
                .aggregateId(1L)
                .eventType(eventType)
                .payload("{\"id\":" + id + ",\"name\":\"hiit\"}")
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .createdAt(LocalDateTime.now())
                .build();
    }
}