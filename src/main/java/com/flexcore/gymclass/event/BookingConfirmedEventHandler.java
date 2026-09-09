package com.flexcore.gymclass.event;

import com.flexcore.outbox.OutboxEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Example downstream consumer of the {@code booking.confirmed} event. In the real
 * system this would send the confirmation email/push to the member; the handler
 * is idempotent so a redelivered event is harmless.
 */
@Slf4j
@Component
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