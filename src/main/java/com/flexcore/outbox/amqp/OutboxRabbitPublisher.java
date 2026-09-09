package com.flexcore.outbox.amqp;

import com.flexcore.gymclass.event.BookingConfirmedEvent;
import com.flexcore.outbox.OutboxEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRabbitPublisher implements OutboxEventHandler<BookingConfirmedEvent> {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    private static final long CONFIRM_TIMEOUT_MILLIS = 15_000;

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
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, event, correlation);
        waitForBrokerConfirm(event, correlation);
        log.info("Booking {} confirmed for member {} published to RabbitMQ as '{}' (broker confirmed)",
                event.bookingId(), event.memberId(), BookingConfirmedEvent.TYPE);
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