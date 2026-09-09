package com.flexcore.gymclass.event;

import com.flexcore.outbox.OutboxEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback in-app handler for {@code booking.confirmed}, active only when the RabbitMQ
 * route is disabled ({@code app.rabbit.enabled: false}, which is the test profile). When
 * RabbitMQ is on, {@code OutboxRabbitPublisher} replaces this handler and the event is
 * delivered to a real broker. Either way the handler is idempotent so a redelivered
 * event is harmless.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "false")
public class BookingConfirmedEventHandler implements OutboxEventHandler<BookingConfirmedEvent> {

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
        log.info("Booking {} confirmed for member {} in class '{}' — notification dispatched",
                event.bookingId(), event.memberId(), event.className());
    }
}