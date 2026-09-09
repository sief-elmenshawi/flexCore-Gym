package com.flexcore.outbox.enums;

/**
 * Result of processing a single outbox event inside its own transaction.
 */
public enum PublishOutcome {

    /** Handler ran and the row was marked PUBLISHED. */
    PUBLISHED,

    /** Handler threw; the row stays PENDING for a retry (or became DEAD at max attempts). */
    FAILED,

    /** No handler is registered for this event type; the row stays PENDING. */
    UNHANDLED,

    /** The row was already claimed/published by another worker; nothing to do. */
    SKIPPED
}