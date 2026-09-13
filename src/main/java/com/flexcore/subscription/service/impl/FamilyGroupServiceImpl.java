package com.flexcore.subscription.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.subscription.dto.request.AddFamilyMemberRequest;
import com.flexcore.subscription.dto.request.CreateFamilyGroupRequest;
import com.flexcore.subscription.dto.response.FamilyGroupResponse;
import com.flexcore.subscription.entity.FamilyGroup;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.mapper.SubscriptionMapper;
import com.flexcore.subscription.repository.FamilyGroupRepository;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.subscription.service.FamilyGroupService;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyGroupServiceImpl implements FamilyGroupService {

    private final FamilyGroupRepository familyGroupRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public FamilyGroupResponse create(CreateFamilyGroupRequest request, Long ownerUserId) {
        User owner = findUser(ownerUserId);
        SubscriptionPlan plan = findPlan(request.getPlanId());

        if (!plan.isFamilyPlan()) {
            throw new BusinessRuleViolationException("error.subscription.plan-not-family", plan.getName());
        }

        FamilyGroup group = FamilyGroup.builder()
                .ownerUser(owner)
                .plan(plan)
                .build();
        group = familyGroupRepository.save(group);

        LocalDateTime now = LocalDateTime.now();
        Subscription ownerSubscription = Subscription.builder()
                .user(owner)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(now.plusDays(plan.getDurationInDays()))
                .familyGroup(group)
                .build();
        subscriptionRepository.save(ownerSubscription);

        return subscriptionMapper.toResponse(group);
    }

    @Override
    @Transactional
    public FamilyGroupResponse addMember(Long groupId, AddFamilyMemberRequest request, Long requestingUserId) {
        FamilyGroup group = familyGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("error.family-group.notfound", groupId));

        if (!group.getOwnerUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Only the family group owner can add members");
        }

        User member = findUser(request.getUserId());
        if (member.getId().equals(group.getOwnerUser().getId())) {
            throw new BusinessRuleViolationException("error.family.owner-already-member");
        }
        if (subscriptionRepository.existsByUserIdAndStatusIn(member.getId(),
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN))) {
            throw new BusinessRuleViolationException("error.subscription.already-active");
        }

        long activeMembers = subscriptionRepository.countByFamilyGroupIdAndStatus(groupId, SubscriptionStatus.ACTIVE);
        Integer maxMembers = group.getPlan().getMaxFamilyMembers();
        if (maxMembers != null && activeMembers >= maxMembers) {
            throw new BusinessRuleViolationException(
                    "error.family.group-full", activeMembers, maxMembers);
        }

        LocalDateTime now = LocalDateTime.now();
        Subscription memberSubscription = Subscription.builder()
                .user(member)
                .plan(group.getPlan())
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(now.plusDays(group.getPlan().getDurationInDays()))
                .familyGroup(group)
                .build();
        subscriptionRepository.save(memberSubscription);

        return subscriptionMapper.toResponse(group);
    }

    @Override
    @Transactional
    public FamilyGroupResponse deletePermanently(Long groupId, Long requestingUserId) {
        FamilyGroup group = familyGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("error.family-group.notfound", groupId));

        if (!group.getOwnerUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Only the family group owner can delete the group");
        }
        if (subscriptionRepository.countByFamilyGroupIdAndStatus(groupId, SubscriptionStatus.ACTIVE) > 0
                || subscriptionRepository.countByFamilyGroupIdAndStatus(groupId, SubscriptionStatus.FROZEN) > 0) {
            throw new BusinessRuleViolationException("error.family.group-has-active-members");
        }

        subscriptionRepository.cancelGroupSubscriptions(groupId);
        familyGroupRepository.delete(group);
        return subscriptionMapper.toResponse(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FamilyGroupResponse> getMyGroups(Long ownerUserId) {
        return familyGroupRepository.findByOwnerUserId(ownerUserId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
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
