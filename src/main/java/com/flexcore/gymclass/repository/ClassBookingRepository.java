package com.flexcore.gymclass.repository;

import com.flexcore.gymclass.entity.ClassBooking;
import com.flexcore.gymclass.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassBookingRepository extends JpaRepository<ClassBooking, Long> {

    boolean existsByUserIdAndGymClassIdAndStatus(Long userId, Long gymClassId, BookingStatus status);

    void deleteByGymClass_Id(Long gymClassId);

    @EntityGraph(attributePaths = {"gymClass", "gymClass.trainer"})
    Page<ClassBooking> findByUserIdOrderByBookedAtDesc(Long userId, Pageable pageable);
}
