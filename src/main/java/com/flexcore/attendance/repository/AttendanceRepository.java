package com.flexcore.attendance.repository;

import com.flexcore.attendance.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @EntityGraph(attributePaths = {"user", "subscription"})
    Page<Attendance> findByUserIdOrderByCheckInAtDesc(Long userId, Pageable pageable);
}
