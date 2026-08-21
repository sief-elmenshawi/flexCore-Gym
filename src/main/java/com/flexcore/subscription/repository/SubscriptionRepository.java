package com.flexcore.subscription.repository;

import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {

    @Override
    @EntityGraph(attributePaths = {"user", "plan"})
    Page<Subscription> findAll(Specification<Subscription> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"plan"})
    List<Subscription> findByUserIdOrderByEndDateAsc(Long userId);

    Optional<Subscription> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<SubscriptionStatus> statuses);

    long countByFamilyGroupIdAndStatus(Long familyGroupId, SubscriptionStatus status);

    /**
     * Subscriptions that currently grant gym access: ACTIVE or FROZEN,
     * and whose end date is still in the future.
     */
    @EntityGraph(attributePaths = {"plan"})
    @Query("""
            select s from Subscription s
            where s.user.id = :userId
              and s.endDate > :now
              and s.status in (
                  com.flexcore.subscription.enums.SubscriptionStatus.ACTIVE,
                  com.flexcore.subscription.enums.SubscriptionStatus.FROZEN)
            order by s.endDate asc
            """)
    List<Subscription> findUsableByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Bulk-transitions overdue ACTIVE subscriptions to EXPIRED.
     * lastModifiedDate is set explicitly because JPQL bulk updates bypass entity auditing.
     */
    @Modifying
    @Query("""
            update Subscription s
            set s.status = :newStatus, s.lastModifiedDate = :now
            where s.status = com.flexcore.subscription.enums.SubscriptionStatus.ACTIVE
              and s.endDate < :now
            """)
    int expireOverdue(@Param("now") LocalDateTime now, @Param("newStatus") SubscriptionStatus newStatus);
}
