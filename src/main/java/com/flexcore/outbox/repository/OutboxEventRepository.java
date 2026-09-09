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

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims a bounded batch of pending events for this worker. PESSIMISTIC_WRITE +
     * lock timeout -2 compiles to {@code FOR UPDATE SKIP LOCKED} on PostgreSQL, so
     * concurrent workers never hand the same row to two dispatchers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("select e from OutboxEvent e where e.status = :status order by e.id asc")
    List<OutboxEvent> findPendingEvents(Pageable pageable, @Param("status") OutboxStatus status);

    OutboxEvent findFirstByEventTypeOrderByIdDesc(String eventType);

    void deleteByEventType(String eventType);
}