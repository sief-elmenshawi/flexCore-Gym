package com.flexcore.gymclass.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.entity.ClassBooking;
import com.flexcore.gymclass.entity.GymClass;
import com.flexcore.gymclass.enums.BookingStatus;
import com.flexcore.gymclass.mapper.GymClassMapper;
import com.flexcore.gymclass.repository.ClassBookingRepository;
import com.flexcore.gymclass.repository.GymClassRepository;
import com.flexcore.gymclass.service.ClassBookingService;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ClassBookingServiceImpl implements ClassBookingService {

    private final ClassBookingRepository classBookingRepository;
    private final GymClassRepository gymClassRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GymClassMapper gymClassMapper;

    @Transactional
    public ClassBookingResponse book(Long classId, Long memberId) {
        User member = findUser(memberId);
        GymClass gymClass = gymClassRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("error.gym-class.notfound", classId));

        if (!gymClass.hasCapacity()) {
            throw new BusinessRuleViolationException("error.booking.class-full", gymClass.getName());
        }
        if (gymClass.getStartsAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("error.booking.started");
        }
        if (classBookingRepository.existsByUserIdAndGymClassIdAndStatus(member.getId(), classId, BookingStatus.CONFIRMED)) {
            throw new BusinessRuleViolationException("error.booking.duplicate");
        }
        if (subscriptionRepository.findUsableByUserId(memberId, LocalDateTime.now()).isEmpty()) {
            throw new BusinessRuleViolationException("error.booking.subscription-required");
        }

        ClassBooking booking = ClassBooking.builder()
                .user(member)
                .gymClass(gymClass)
                .status(BookingStatus.CONFIRMED)
                .bookedAt(LocalDateTime.now())
                .build();

        try {
            gymClass.setBookedCount(gymClass.getBookedCount() + 1);
            // Flush immediately so a concurrent-modification conflict surfaces here,
            // inside the transaction, instead of at commit time.
            gymClassRepository.saveAndFlush(gymClass);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Another concurrent request booked the last seat between our read and write
            throw new BusinessRuleViolationException("error.booking.race-lost");
        }

        return gymClassMapper.toBookingResponse(classBookingRepository.save(booking));
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long requestingUserId) {
        ClassBooking booking = classBookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("error.class-booking.notfound", bookingId));

        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("error.booking.cancel-only-confirmed");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        classBookingRepository.save(booking);

        GymClass gymClass = booking.getGymClass();
        if (gymClass.getBookedCount() > 0) {
            gymClass.setBookedCount(gymClass.getBookedCount() - 1);
            gymClassRepository.save(gymClass);
        }
    }

    @Transactional(readOnly = true)
    public Page<ClassBookingResponse> getMyBookings(Long userId, Pageable pageable) {
        return classBookingRepository.findByUserIdOrderByBookedAtDesc(userId, pageable)
                .map(gymClassMapper::toBookingResponse);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notfound", id));
    }
}
