package com.flexcore.outbox;

/**
 * A typed consumer for one outbox event type. Implementations must be idempotent:
 * delivery is at-least-once and a crash between handling and marking the row
 * published causes the event to be redelivered on the next poll.
 */
public interface OutboxEventHandler<T> {

    String eventType();

    Class<T> payloadType();

    void handle(T event);

    /**
     * Handles an event with the W3C traceparent that was captured on the recording
     * thread. Handlers that cross an async boundary (RabbitMQ, another queue) should
     * use it to keep the distributed trace intact; handlers that stay in-process can
     * ignore it, which is what the default implementation does.
     */
    default void handle(T event, String traceContext) {
        handle(event);
    }
}