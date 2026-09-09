package com.flexcore.outbox.amqp;

import com.flexcore.gymclass.event.BookingConfirmedEvent;
import com.flexcore.outbox.OutboxEventHandler;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Final step of the outbox pipeline for {@code booking.confirmed}: instead of handling
 * the event inside this application, it hands it to RabbitMQ. The exchange queues the
 * message for whatever consumers are listening. If the broker is unreachable the send
 * throws and the outbox keeps the row PENDING to retry with its backoff schedule — the
 * two systems together give guaranteed delivery without a network call inside the
 * business transaction.
 *
 * <p>This handler runs on the outbox scheduler thread, not on the request thread that
 * recorded the event, so the original trace would otherwise be lost. The row carries the
 * W3C traceparent captured at record time; this publisher makes it the current context
 * for the send (so the template's send observation joins the original trace) and also
 * stamps it directly onto the message headers as a fallback. Either path lets the
 * consuming service continue the same distributed trace.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRabbitPublisher implements OutboxEventHandler<BookingConfirmedEvent> {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    private static final long CONFIRM_TIMEOUT_MILLIS = 15_000;
    private static final String TRACE_CONTEXT_HEADER = "traceparent";

    public OutboxRabbitPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${app.rabbit.exchange:flexcore.events}") String exchange,
                                 @Value("${app.rabbit.routing-key:booking.confirmed}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public String eventType() {
        return BookingConfirmedEvent.TYPE;
    }

    @Override
    public Class<BookingConfirmedEvent> payloadType() {
        return BookingConfirmedEvent.class;
    }

    @Override
    public void handle(BookingConfirmedEvent event) {
        handle(event, null);
    }

    @Override
    public void handle(BookingConfirmedEvent event, String traceContext) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        if (traceContext == null || traceContext.isBlank()) {
            rabbitTemplate.convertAndSend(exchange, routingKey, event, correlation);
        } else {
            publishWithinTraceContext(event, correlation, traceContext);
        }
        waitForBrokerConfirm(event, correlation);
        log.info("Booking {} confirmed for member {} published to RabbitMQ as '{}' (broker confirmed)",
                event.bookingId(), event.memberId(), BookingConfirmedEvent.TYPE);
    }

    /**
     * Publishing on the scheduler thread has its own span (if any); the traceparent we
     * stored when the booking was recorded is what links this message back to the user's
     * original trace. We make that context current so the send observation (and the
     * resulting W3C injection) joins the original trace, and set the header explicitly
     * as a fallback if the observation path is not active.
     */
    private void publishWithinTraceContext(BookingConfirmedEvent event,
                                           CorrelationData correlation,
                                           String traceContext) {
        Context parent = W3CTraceContextPropagator.getInstance().extract(
                Context.current(), Collections.singletonMap(TRACE_CONTEXT_HEADER, traceContext), getter());
        MessagePostProcessor carryTrace = message -> {
            message.getMessageProperties().getHeaders().putIfAbsent(TRACE_CONTEXT_HEADER, traceContext);
            return message;
        };
        try (Scope ignored = parent.makeCurrent()) {
            rabbitTemplate.convertAndSend(exchange, routingKey, event, carryTrace, correlation);
        }
    }

    private static TextMapGetter<Map<String, String>> getter() {
        return new TextMapGetter<Map<String, String>>() {
            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return carrier.keySet();
            }

            @Override
            public String get(Map<String, String> carrier, String key) {
                return carrier.get(key);
            }
        };
    }

    /**
     * With {@code publisher-confirm-type: correlated} the template completes the
     * {@link CorrelationData} future when the broker acks (or nacks) the message. Blocking
     * on it turns "bytes written to the socket" into "message durably accepted", so a
     * broker that dies mid-publish is seen by the outbox as a failure to retry instead of a
     * silent success.
     */
    private void waitForBrokerConfirm(BookingConfirmedEvent event, CorrelationData correlation) {
        try {
            correlation.getFuture().get(CONFIRM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for broker confirmation of booking " + event.bookingId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new AmqpException("Broker did not confirm booking " + event.bookingId()
                    + " within " + CONFIRM_TIMEOUT_MILLIS + "ms", ex);
        }
    }
}