package com.flexcore.gymclass.service;

import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClassBookingService {

    ClassBookingResponse book(Long classId, Long memberId);

    void cancelBooking(Long bookingId, Long requestingUserId);

    void deletePermanently(Long bookingId, Long requestingUserId);

    Page<ClassBookingResponse> getMyBookings(Long userId, Pageable pageable);
}
