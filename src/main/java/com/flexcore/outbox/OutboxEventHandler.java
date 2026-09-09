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
}