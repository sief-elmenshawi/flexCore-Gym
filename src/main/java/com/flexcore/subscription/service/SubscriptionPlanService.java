package com.flexcore.subscription.service;

import com.flexcore.subscription.dto.request.CreateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.request.UpdateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;

import java.util.List;

public interface SubscriptionPlanService {

    SubscriptionPlanResponse create(CreateSubscriptionPlanRequest request);

    SubscriptionPlanResponse update(Long id, UpdateSubscriptionPlanRequest request);

    List<SubscriptionPlanResponse> getAll();
}
