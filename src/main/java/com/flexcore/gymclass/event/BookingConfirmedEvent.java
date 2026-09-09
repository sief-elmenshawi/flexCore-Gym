package com.flexcore.gymclass.event;

import java.time.LocalDateTime;

public record BookingConfirmedEvent(
        Long bookingId,
        Long memberId,
        Long classId,
        String className,
        LocalDateTime bookedAt
) {

    public static final String TYPE = "booking.confirmed";
}