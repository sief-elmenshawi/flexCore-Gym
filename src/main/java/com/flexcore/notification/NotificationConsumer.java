package com.flexcore.notification;

import com.flexcore.gymclass.event.BookingConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Stand-in for the real notification service: a downstream consumer that reacts to the
 * {@code booking.confirmed} message queued by the outbox. Everything downstream of the
 * broker would be a separate microservice; here it lives in the same process so the
 * wiring stays testable.
 * <p>
 * The {@code received} queue is observability scaffolding used by the integration test.
 * It is bounded so a runaway publisher can never grow it without limit; the production
 * concern is the (fake) dispatch log + acknowledgment when the listener returns normally.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    private final BlockingQueue<BookingConfirmedEvent> received = new LinkedBlockingQueue<>(100);

    @RabbitListener(queues = "${app.rabbit.notification-queue:flexcore.notification.queue}")
    public void onNotification(BookingConfirmedEvent event) {
        log.info("Notification dispatched to member {} for booking {} in class '{}'",
                event.memberId(), event.bookingId(), event.className());
        received.offer(event);
    }

    public BookingConfirmedEvent await(long timeoutMillis) throws InterruptedException {
        return received.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void reset() {
        received.clear();
    }
}