package com.flexcore.outbox.enums;

public enum OutboxStatus {

    /** Recorded inside the business transaction, not yet delivered. */
    PENDING,

    /** Delivered successfully to a handler. */
    PUBLISHED
}