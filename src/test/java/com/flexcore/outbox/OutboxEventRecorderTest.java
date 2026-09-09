package com.flexcore.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flexcore.gymclass.event.BookingConfirmedEvent;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventRecorderTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<io.opentelemetry.api.OpenTelemetry> noOtelProvider = mock(ObjectProvider.class);

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final OutboxEventRecorder recorder = new OutboxEventRecorder(repository, mapper, noOtelProvider);

    @Test
    void persistsPendingEventWithSerializedPayload() throws Exception {
        when(noOtelProvider.getIfAvailable()).thenReturn(null);
        var payload = new BookingConfirmedEvent(11L, 22L, 33L, "HIIT", LocalDateTime.of(2026, 1, 5, 10, 30));

        recorder.recordEvent(BookingConfirmedEvent.TYPE, "class_booking", 11L, payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(BookingConfirmedEvent.TYPE, saved.getEventType());
        assertEquals("class_booking", saved.getAggregateType());
        assertEquals(11L, saved.getAggregateId());

        JsonNode node = mapper.readTree(saved.getPayload());
        assertEquals(11L, node.get("bookingId").asLong());
        assertEquals(22L, node.get("memberId").asLong());
        assertEquals("HIIT", node.get("className").asText());
        assertEquals("2026-01-05T10:30:00", node.get("bookedAt").asText());

        assertNull(saved.getTraceContext(), "no OpenTelemetry provider -> no trace context captured");
    }

    @Test
    void unwrapsSerializationErrors() {
        OutboxEventRecorder strict = new OutboxEventRecorder(repository, new ObjectMapper(), noOtelProvider);
        var payload = new BookingConfirmedEvent(1L, 2L, 3L, "HIIT", LocalDateTime.now());

        assertThrows(IllegalArgumentException.class,
                () -> strict.recordEvent(BookingConfirmedEvent.TYPE, "class_booking", 1L, payload));
    }
}