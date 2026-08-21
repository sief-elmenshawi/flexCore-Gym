package com.flexcore.subscription;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.subscription.dto.request.AddFamilyMemberRequest;
import com.flexcore.subscription.entity.FamilyGroup;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.mapper.SubscriptionMapper;
import com.flexcore.subscription.repository.FamilyGroupRepository;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.subscription.service.impl.FamilyGroupServiceImpl;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyGroupServiceTest {

    @Mock private FamilyGroupRepository familyGroupRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionMapper subscriptionMapper;

    @InjectMocks
    private FamilyGroupServiceImpl familyGroupService;

    private User owner;
    private User member;
    private SubscriptionPlan familyPlan;
    private FamilyGroup group;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).fullName("Owner").email("owner@test.com").active(true).build();
        member = User.builder().id(2L).fullName("Member").email("member@test.com").active(true).build();
        familyPlan = SubscriptionPlan.builder()
                .id(20L)
                .name("Family Monthly")
                .price(new java.math.BigDecimal("2000.00"))
                .durationInDays(30)
                .maxFamilyMembers(4)
                .build();
        group = FamilyGroup.builder()
                .id(30L)
                .ownerUser(owner)
                .plan(familyPlan)
                .build();
    }

    private AddFamilyMemberRequest request(Long userId) {
        AddFamilyMemberRequest request = new AddFamilyMemberRequest();
        request.setUserId(userId);
        return request;
    }

    @Test
    void addMember_whenMemberAlreadyHasActiveSubscription_throws() {
        when(familyGroupRepository.findById(30L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(subscriptionRepository.existsByUserIdAndStatusIn(2L,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN))).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class,
                () -> familyGroupService.addMember(30L, request(2L), 1L));

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void addMember_whenMemberHasNoSubscription_createsMembership() {
        when(familyGroupRepository.findById(30L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(subscriptionRepository.existsByUserIdAndStatusIn(2L,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN))).thenReturn(false);
        when(subscriptionRepository.countByFamilyGroupIdAndStatus(30L, SubscriptionStatus.ACTIVE)).thenReturn(1L);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        familyGroupService.addMember(30L, request(2L), 1L);

        verify(subscriptionRepository).save(any(Subscription.class));
    }
}
