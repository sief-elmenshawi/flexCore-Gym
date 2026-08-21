package com.flexcore.attendance.service;

import com.flexcore.attendance.dto.request.CheckInRequest;
import com.flexcore.attendance.dto.response.AttendanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AttendanceService {

    AttendanceResponse checkIn(CheckInRequest request, Long staffUserId);

    Page<AttendanceResponse> getMemberHistory(Long userId, Pageable pageable);
}
