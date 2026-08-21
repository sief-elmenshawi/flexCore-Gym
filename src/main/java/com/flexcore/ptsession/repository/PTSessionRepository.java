package com.flexcore.ptsession.repository;

import com.flexcore.ptsession.entity.PTSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PTSessionRepository extends JpaRepository<PTSession, Long> {

    @Query(value = """
            SELECT COUNT(*) > 0 FROM pt_sessions s
            WHERE s.trainer_id = :trainerId
              AND s.status = 'SCHEDULED'
              AND s.scheduled_at < :sessionEnd
              AND s.scheduled_at + (s.duration_minutes * INTERVAL '1 minute') > :sessionStart
            """, nativeQuery = true)
    boolean existsOverlappingForTrainer(@Param("trainerId") Long trainerId,
                                        @Param("sessionStart") LocalDateTime sessionStart,
                                        @Param("sessionEnd") LocalDateTime sessionEnd);

    @EntityGraph(attributePaths = {"member", "trainer"})
    Page<PTSession> findByMemberIdOrderByScheduledAtDesc(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "trainer"})
    Page<PTSession> findByTrainerIdOrderByScheduledAtDesc(Long trainerId, Pageable pageable);
}
