package com.flexcore.gymclass;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.entity.ClassBooking;
import com.flexcore.gymclass.entity.GymClass;
import com.flexcore.gymclass.enums.BookingStatus;
import com.flexcore.gymclass.event.BookingConfirmedEvent;
import com.flexcore.gymclass.mapper.GymClassMapper;
import com.flexcore.gymclass.repository.ClassBookingRepository;
import com.flexcore.gymclass.repository.GymClassRepository;
import com.flexcore.gymclass.service.impl.ClassBookingServiceImpl;
import com.flexcore.outbox.OutboxEventRecorder;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level checks of the booking business rules. The actual concurrent
 * race is exercised separately in BookingConcurrencyTest, which hits a
 * real database with parallel threads — a mocked repository can't
 * reproduce a genuine optimistic-lock conflict.
 */
@ExtendWith(MockitoExtension.class)
class ClassBookingServiceTest {

    @Mock private ClassBookingRepository classBookingRepository;
    @Mock private GymClassRepository gymClassRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private GymClassMapper gymClassMapper;
    @Mock private OutboxEventRecorder outboxEventRecorder;

    @InjectMocks
    private ClassBookingServiceImpl classBookingService;

    private User member;
    private GymClass gymClass;

    @BeforeEach
    void setUp() {
        member = User.builder().id(1L).fullName("Sara Ali").email("sara@example.com").active(true).build();

        gymClass = GymClass.builder()
                .id(2L)
                .name("Yoga")
                .capacity(10)
                .bookedCount(0)
                .startsAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Test
    void book_withOpenCapacity_incrementsBookedCountAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(gymClassRepository.findById(2L)).thenReturn(Optional.of(gymClass));
        when(subscriptionRepository.findUsableByUserId(anyLong(), any())).thenReturn(List.of(new Subscription()));
        when(classBookingRepository.existsByUserIdAndGymClassIdAndStatus(1L, 2L, BookingStatus.CONFIRMED))
                .thenReturn(false);
        when(gymClassRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(classBookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gymClassMapper.toBookingResponse(any(ClassBooking.class)))
                .thenReturn(ClassBookingResponse.builder().id(7L).status(BookingStatus.CONFIRMED).build());

        classBookingService.book(2L, 1L);

        assertEquals(1, gymClass.getBookedCount());
        verify(classBookingRepository).save(any(ClassBooking.class));
        verify(outboxEventRecorder).recordEvent(
                eq(BookingConfirmedEvent.TYPE), eq("class_booking"), any(), any());
    }

    @Test
    void book_withoutActiveSubscription_throwsWithoutSaving() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(gymClassRepository.findById(2L)).thenReturn(Optional.of(gymClass));
        when(subscriptionRepository.findUsableByUserId(anyLong(), any())).thenReturn(List.of());

        assertThrows(BusinessRuleViolationException.class, () -> classBookingService.book(2L, 1L));
        verify(gymClassRepository, never()).save(any());
        verify(classBookingRepository, never()).save(any());
    }

    @Test
    void book_whenClassIsFull_throwsWithoutSaving() {
        gymClass.setCapacity(5);
        gymClass.setBookedCount(5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(gymClassRepository.findById(2L)).thenReturn(Optional.of(gymClass));

        assertThrows(BusinessRuleViolationException.class, () -> classBookingService.book(2L, 1L));
        verify(classBookingRepository, never()).save(any());
    }

    @Test
    void book_whenDuplicateActiveBooking_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(gymClassRepository.findById(2L)).thenReturn(Optional.of(gymClass));
        when(classBookingRepository.existsByUserIdAndGymClassIdAndStatus(1L, 2L, BookingStatus.CONFIRMED))
                .thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> classBookingService.book(2L, 1L));
        verify(classBookingRepository, never()).save(any());
    }

    @Test
    void book_whenClassAlreadyStarted_throws() {
        gymClass.setStartsAt(LocalDateTime.now().minusHours(1));

        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(gymClassRepository.findById(2L)).thenReturn(Optional.of(gymClass));

        assertThrows(BusinessRuleViolationException.class, () -> classBookingService.book(2L, 1L));
    }

    @Test
    void book_onOptimisticLockConflict_translatesToBusinessRule() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(gymClassRepository.findById(2L)).thenReturn(Optional.of(gymClass));
        when(subscriptionRepository.findUsableByUserId(anyLong(), any())).thenReturn(List.of(new Subscription()));
        when(classBookingRepository.existsByUserIdAndGymClassIdAndStatus(anyLong(), anyLong(), any()))
                .thenReturn(false);
        // Simulates: another concurrent request booked the last spot between
        // our findById() read and our saveAndFlush() write.
        when(gymClassRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(GymClass.class, 2L));

        assertThrows(BusinessRuleViolationException.class, () -> classBookingService.book(2L, 1L));
        verify(classBookingRepository, never()).save(any());
    }

    @Test
    void cancel_byConfirmedOwner_cancelsAndDecrementsCapacity() {
        gymClass.setBookedCount(3);
        ClassBooking booking = ClassBooking.builder()
                .id(5L)
                .user(member)
                .gymClass(gymClass)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(classBookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        classBookingService.cancelBooking(5L, 1L);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(2, gymClass.getBookedCount());
    }

    @Test
    void cancel_byWrongUser_throwsForbidden() {
        ClassBooking booking = ClassBooking.builder()
                .id(5L)
                .user(member)
                .gymClass(gymClass)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(classBookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> classBookingService.cancelBooking(5L, 999L));
    }
}
