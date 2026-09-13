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
    List<Subscription> findByUserIdAndDeletedAtIsNullOrderByEndDateAsc(Long userId);

    Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);

    Optional<Subscription> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    boolean existsByUserIdAndDeletedAtIsNullAndStatusIn(Long userId, Collection<SubscriptionStatus> statuses);

    long countByFamilyGroupIdAndDeletedAtIsNullAndStatus(Long familyGroupId, SubscriptionStatus status);

    /**
     * Subscriptions that currently grant gym access: ACTIVE or FROZEN,
     * whose end date is still in the future, and not soft-deleted.
     */
    @EntityGraph(attributePaths = {"plan"})
    @Query("""
            select s from Subscription s
            where s.user.id = :userId
              and s.endDate > :now
              and s.deletedAt is null
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
              and s.deletedAt is null
              and s.endDate < :now
            """)
    int expireOverdue(@Param("now") LocalDateTime now, @Param("newStatus") SubscriptionStatus newStatus);

    /**
     * Auto-thaws FROZEN subscriptions whose frozen period has fully elapsed.
     * endDate was already extended by the full freeze duration at freeze time,
     * so no days are refunded here - only the status flips back to ACTIVE.
     */
    @Modifying
    @Query("""
            update Subscription s
            set s.status = com.flexcore.subscription.enums.SubscriptionStatus.ACTIVE,
                s.frozenAt = null,
                s.frozenUntil = null,
                s.lastModifiedDate = :now
            where s.status = com.flexcore.subscription.enums.SubscriptionStatus.FROZEN
              and s.deletedAt is null
              and s.frozenUntil <= :now
            """)
    int thawElapsedFreezes(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE Subscription s
            SET s.status = com.flexcore.subscription.enums.SubscriptionStatus.CANCELLED,
                s.familyGroup = null
            WHERE s.familyGroup.id = :groupId
              AND s.deletedAt is null
            """)
    void cancelGroupSubscriptions(@Param("groupId") Long groupId);
}
