package com.flexcore.subscription.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.subscription.dto.request.CreateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.request.UpdateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.mapper.SubscriptionMapper;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    @CacheEvict(value = "plans", allEntries = true)
    public SubscriptionPlanResponse create(CreateSubscriptionPlanRequest request) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(request.getName().trim())
                .price(request.getPrice())
                .durationInDays(request.getDurationInDays())
                .maxFamilyMembers(request.getMaxFamilyMembers())
                .build();

        return subscriptionMapper.toPlanResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    @CacheEvict(value = "plans", allEntries = true)
    public SubscriptionPlanResponse update(Long id, UpdateSubscriptionPlanRequest request) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.subscription-plan.notfound", id));

        plan.setName(request.getName().trim());
        plan.setPrice(request.getPrice());
        plan.setDurationInDays(request.getDurationInDays());
        plan.setMaxFamilyMembers(request.getMaxFamilyMembers());

        return subscriptionMapper.toPlanResponse(planRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "plans", key = "'all'", sync = true)
    public List<SubscriptionPlanResponse> getAll() {
        return planRepository.findAll().stream()
                .map(subscriptionMapper::toPlanResponse)
                .toList();
    }
}
