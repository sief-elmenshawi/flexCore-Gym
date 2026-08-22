package com.flexcore.attendance;

import com.flexcore.attendance.dto.request.CheckInRequest;
import com.flexcore.attendance.dto.response.AttendanceResponse;
import com.flexcore.attendance.entity.Attendance;
import com.flexcore.attendance.mapper.AttendanceMapper;
import com.flexcore.attendance.repository.AttendanceRepository;
import com.flexcore.attendance.service.impl.AttendanceServiceImpl;
import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private AttendanceMapper attendanceMapper;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    private User member;
    private User staff;

    @BeforeEach
    void setUp() {
        member = User.builder().id(1L).fullName("Member One").active(true).build();
        staff = User.builder().id(9L).fullName("Receptionist").active(true).build();
    }

    private CheckInRequest request(Long userId) {
        CheckInRequest request = new CheckInRequest();
        request.setUserId(userId);
        return request;
    }

    @Test
    void checkIn_usesLatestUsableSubscriptionAndRecordsStaff() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        Subscription older = Subscription.builder().id(11L).build();
        Subscription latest = Subscription.builder().id(22L).build();
        when(subscriptionRepository.findUsableByUserId(eq(1L), any()))
                .thenReturn(List.of(older, latest));
        when(userRepository.findById(9L)).thenReturn(Optional.of(staff));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
        AttendanceResponse mapped = AttendanceResponse.builder().userId(1L).build();
        when(attendanceMapper.toResponse(any(Attendance.class))).thenReturn(mapped);

        AttendanceResponse response = attendanceService.checkIn(request(1L), 9L);

        assertEquals(1L, response.getUserId());
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(captor.capture());
        assertSame(member, captor.getValue().getUser());
        assertSame(latest, captor.getValue().getSubscription());
        assertSame(staff, captor.getValue().getCheckedInBy());
    }

    @Test
    void checkIn_deactivatedMemberThrows() {
        member.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThrows(BusinessRuleViolationException.class,
                () -> attendanceService.checkIn(request(1L), 9L));

        verify(attendanceRepository, never()).save(any(Attendance.class));
    }

    @Test
    void checkIn_withoutUsableSubscriptionThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findUsableByUserId(eq(1L), any())).thenReturn(List.of());

        assertThrows(BusinessRuleViolationException.class,
                () -> attendanceService.checkIn(request(1L), 9L));
    }

    @Test
    void checkIn_unknownMemberThrows() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> attendanceService.checkIn(request(404L), 9L));
    }

    @Test
    void getMemberHistory_mapsPage() {
        Pageable pageable = PageRequest.of(0, 5);
        Attendance attendance = Attendance.builder().id(3L).user(member).build();
        when(attendanceRepository.findByUserIdOrderByCheckInAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(attendance)));
        when(attendanceMapper.toResponse(attendance))
                .thenReturn(AttendanceResponse.builder().id(3L).build());

        var page = attendanceService.getMemberHistory(1L, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(3L, page.getContent().get(0).getId());
    }
}
