package com.flexcore.attendance.service.impl;

import com.flexcore.attendance.dto.request.CheckInRequest;
import com.flexcore.attendance.dto.response.AttendanceResponse;
import com.flexcore.attendance.entity.Attendance;
import com.flexcore.attendance.mapper.AttendanceMapper;
import com.flexcore.attendance.repository.AttendanceRepository;
import com.flexcore.attendance.service.AttendanceService;
import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AttendanceMapper attendanceMapper;

    @Override
    @Transactional
    public AttendanceResponse checkIn(CheckInRequest request, Long staffUserId) {
        User member = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notfound", request.getUserId()));
        if (!member.isActive()) {
            throw new BusinessRuleViolationException("error.attendance.member-deactivated");
        }

        Subscription subscription = subscriptionRepository
                .findUsableByUserId(member.getId(), LocalDateTime.now())
                .stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "error.attendance.no-active-subscription"));

        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.staff.notfound", staffUserId));

        Attendance attendance = Attendance.builder()
                .user(member)
                .checkInAt(LocalDateTime.now())
                .checkedInBy(staff)
                .subscription(subscription)
                .build();

        return attendanceMapper.toResponse(attendanceRepository.save(attendance));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getMemberHistory(Long userId, Pageable pageable) {
        return attendanceRepository.findByUserIdOrderByCheckInAtDesc(userId, pageable)
                .map(attendanceMapper::toResponse);
    }
}
