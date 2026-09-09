package com.flexcore.outbox.repository;

import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Bounded candidate scan of events that are PENDING and due for (re)attempt
     * ({@code next_attempt_at} is null or in the past). No pessimistic lock here: each
     * candidate is claimed individually inside its own transaction by
     * {@code findByIdForPublishing}, keeping locks short and per-event.
     */
    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            order by e.id asc""")
    List<OutboxEvent> findPendingEvents(Pageable pageable,
                                        @Param("status") OutboxStatus status,
                                        @Param("now") LocalDateTime now);

    /**
     * Claims a single event for processing. PESSIMISTIC_WRITE + lock timeout -2
     * compiles to {@code FOR UPDATE SKIP LOCKED} on PostgreSQL: if another worker is
     * already handling the row, this returns {@link Optional#empty()} instead of
     * blocking. The caller re-checks the status before dispatching, so a row claimed
     * and published concurrently is never double-delivered.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findByIdForPublishing(@Param("id") Long id);

    OutboxEvent findFirstByEventTypeOrderByIdDesc(String eventType);

    void deleteByEventType(String eventType);
}