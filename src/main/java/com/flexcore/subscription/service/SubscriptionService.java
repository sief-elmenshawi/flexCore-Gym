package com.flexcore.subscription.service;

import com.flexcore.subscription.dto.request.CreateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.request.FreezeSubscriptionRequest;
import com.flexcore.subscription.dto.request.PurchaseSubscriptionRequest;
import com.flexcore.subscription.dto.request.UpdateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;
import com.flexcore.subscription.dto.response.SubscriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SubscriptionService {

    SubscriptionResponse purchase(PurchaseSubscriptionRequest request, Long currentUserId, boolean privileged);

    SubscriptionResponse freeze(Long id, FreezeSubscriptionRequest request, Long currentUserId, boolean privileged);

    SubscriptionResponse unfreeze(Long id, Long currentUserId, boolean privileged);

    SubscriptionResponse cancel(Long id, Long currentUserId, boolean privileged);

    List<SubscriptionResponse> getMySubscriptions(Long userId);

    Page<SubscriptionResponse> expiringSoon(int withinDays, Pageable pageable);
}
