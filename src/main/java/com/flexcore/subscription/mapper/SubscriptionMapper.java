package com.flexcore.subscription.mapper;

import com.flexcore.subscription.dto.response.FamilyGroupResponse;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;
import com.flexcore.subscription.dto.response.SubscriptionResponse;
import com.flexcore.subscription.entity.FamilyGroup;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "price", source = "plan.price")
    @Mapping(target = "familyGroupId", source = "familyGroup.id")
    SubscriptionResponse toResponse(Subscription subscription);

    @Mapping(target = "ownerUserId", source = "ownerUser.id")
    @Mapping(target = "ownerName", source = "ownerUser.fullName")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "maxMembers", source = "plan.maxFamilyMembers")
    FamilyGroupResponse toResponse(FamilyGroup familyGroup);
}
