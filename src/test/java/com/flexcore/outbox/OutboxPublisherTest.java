package com.flexcore.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

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

    private static class FailingHandler implements OutboxEventHandler<TestEvent> {

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
    private final FailingHandler failingHandler = new FailingHandler();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        var registry = new OutboxEventHandlerRegistry(
                List.of(recordingHandler, failingHandler));
        publisher = new OutboxPublisher(repository, registry, mapper);
    }

    @Test
    void dispatchesPendingEventsAndMarksThemPublished() {
        OutboxEvent event = pendingEvent("test.event", "{\"id\":7,\"name\":\"hiit\"}");

        when(repository.findPendingEvents(any(Pageable.class), eq(OutboxStatus.PENDING))).thenReturn(List.of(event));

        assertEquals(1, publisher.publishPendingEvents());
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(List.of(new TestEvent(7L, "hiit")), recordingHandler.received);
    }

    @Test
    void failingHandlerLeavesEventPendingAndRecordsError() {
        OutboxEvent event = pendingEvent("flaky.event", "{\"id\":9,\"name\":\"spin\"}");

        when(repository.findPendingEvents(any(Pageable.class), eq(OutboxStatus.PENDING))).thenReturn(List.of(event));

        assertEquals(0, publisher.publishPendingEvents());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
        assertEquals("boom", event.getLastError());
        assertEquals(1, failingHandler.calls);
    }

    @Test
    void eventWithoutHandlerStaysPendingWithoutCrashingTheWorker() {
        OutboxEvent event = pendingEvent("nobody.handles", "{}");

        when(repository.findPendingEvents(any(Pageable.class), eq(OutboxStatus.PENDING))).thenReturn(List.of(event));

        assertEquals(0, publisher.publishPendingEvents());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertEquals(List.of(), recordingHandler.received);
        assertEquals(0, failingHandler.calls);
    }

    private OutboxEvent pendingEvent(String eventType, String payload) {
        return OutboxEvent.builder()
                .aggregateType("test_aggregate")
                .aggregateId(1L)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}