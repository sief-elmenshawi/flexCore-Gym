package com.flexcore.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.repository.OutboxEventRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes an outbox row. MANDATORY propagation forces the row to be created inside
 * the caller's database transaction: the event commits or rolls back atomically
 * with the business change, which is the whole point of the outbox pattern.
 *
 * <p>The W3C traceparent of the recording thread is stored on the row when a sampled
 * span is active. The outbox publisher runs on a background scheduler thread, so this
 * snapshot is the only way to re-join the original request's trace after the async
 * boundary. When tracing is disabled (tests, no OpenTelemetry bean) this is a no-op.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

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
                    .traceContext(captureTraceContext())
                    .build());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize outbox event payload: " + eventType, ex);
        }
    }

    /**
     * Renders the current sampled span as a W3C {@code traceparent}. Returns
     * {@code null} when there is no OpenTelemetry, no active span, or the span is
     * unsampled, so unrelated/non-sampled flows never leak junk into the database.
     */
    private String captureTraceContext() {
        try {
            OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
            if (openTelemetry == null) {
                return null;
            }
            Span span = Span.fromContext(Context.current());
            if (span == null) {
                return null;
            }
            SpanContext spanContext = span.getSpanContext();
            if (!spanContext.isValid() || !spanContext.isSampled()) {
                return null;
            }
            Map<String, String> carrier = new HashMap<>();
            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            propagator.inject(Context.current(), carrier, (map, key, value) -> map.put(key, value));
            return carrier.get("traceparent");
        } catch (RuntimeException ex) {
            return null;
        }
    }
}