package com.flexcore.subscription.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.subscription.dto.request.FreezeSubscriptionRequest;
import com.flexcore.subscription.dto.request.PurchaseSubscriptionRequest;
import com.flexcore.subscription.dto.response.SubscriptionResponse;
import com.flexcore.subscription.entity.FamilyGroup;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.mapper.SubscriptionMapper;
import com.flexcore.subscription.repository.FamilyGroupRepository;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.subscription.service.SubscriptionService;
import com.flexcore.subscription.specification.SubscriptionSpecifications;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final FamilyGroupRepository familyGroupRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public SubscriptionResponse purchase(PurchaseSubscriptionRequest request, Long currentUserId, boolean privileged) {
        Long targetUserId = privileged && request.getUserId() != null ? request.getUserId() : currentUserId;
        User user = findUser(targetUserId);
        SubscriptionPlan plan = findPlan(request.getPlanId());

        FamilyGroup familyGroup = null;
        if (request.getFamilyGroupId() != null) {
            if (!plan.isFamilyPlan()) {
                throw new BusinessRuleViolationException("error.subscription.plan-not-family", plan.getName());
            }
            familyGroup = familyGroupRepository.findById(request.getFamilyGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.family-group.notfound", request.getFamilyGroupId()));
        }

        if (subscriptionRepository.existsByUserIdAndStatusIn(user.getId(),
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN))) {
            throw new BusinessRuleViolationException("error.subscription.already-active");
        }

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(now.plusDays(plan.getDurationInDays()))
                .familyGroup(familyGroup)
                .build();

        return subscriptionMapper.toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public SubscriptionResponse freeze(Long id, FreezeSubscriptionRequest request,
                                       Long currentUserId, boolean privileged) {
        Subscription subscription = getOwnedSubscription(id, currentUserId, privileged);

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                || !subscription.getEndDate().isAfter(LocalDateTime.now())) {
            // The lazy endDate guard blocks reviving an overdue-but-not-yet-expired
            // subscription: freezing would extend its end date into the future again.
            throw new BusinessRuleViolationException("error.subscription.freeze-only-active");
        }

        LocalDateTime now = LocalDateTime.now();
        subscription.setStatus(SubscriptionStatus.FROZEN);
        subscription.setFrozenAt(now);
        subscription.setFrozenUntil(now.plusDays(request.getDays()));
        subscription.setEndDate(subscription.getEndDate().plusDays(request.getDays()));

        return subscriptionMapper.toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public SubscriptionResponse unfreeze(Long id, Long currentUserId, boolean privileged) {
        Subscription subscription = getOwnedSubscription(id, currentUserId, privileged);

        if (subscription.getStatus() != SubscriptionStatus.FROZEN) {
            throw new BusinessRuleViolationException("error.subscription.unfreeze-only-frozen");
        }

        LocalDateTime now = LocalDateTime.now();
        long remainingFrozenDays = 0;
        if (subscription.getFrozenUntil() != null && subscription.getFrozenUntil().isAfter(now)) {
            remainingFrozenDays = java.time.Duration.between(now, subscription.getFrozenUntil()).toDays();
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEndDate(subscription.getEndDate().minusDays(remainingFrozenDays));
        subscription.setFrozenAt(null);
        subscription.setFrozenUntil(null);

        return subscriptionMapper.toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel(Long id, Long currentUserId, boolean privileged) {
        Subscription subscription = getOwnedSubscription(id, currentUserId, privileged);

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new BusinessRuleViolationException("error.subscription.already-cancelled");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        return subscriptionMapper.toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getMySubscriptions(Long userId) {
        return subscriptionRepository.findByUserIdOrderByEndDateAsc(userId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> expiringSoon(int withinDays, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Specification<Subscription> spec = Specification.where(SubscriptionSpecifications.hasStatus(SubscriptionStatus.ACTIVE))
                .and(SubscriptionSpecifications.endsBefore(now.plusDays(withinDays)));

        return subscriptionRepository.findAll(spec, pageable).map(subscriptionMapper::toResponse);
    }

    private Subscription getOwnedSubscription(Long id, Long currentUserId, boolean privileged) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.subscription.notfound", id));

        if (!privileged && !subscription.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only manage your own subscriptions");
        }
        return subscription;
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notfound", id));
    }

    private SubscriptionPlan findPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.subscription-plan.notfound", id));
    }
}
