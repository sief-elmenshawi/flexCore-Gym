package com.flexcore.subscription.service;

import com.flexcore.subscription.dto.request.AddFamilyMemberRequest;
import com.flexcore.subscription.dto.request.CreateFamilyGroupRequest;
import com.flexcore.subscription.dto.response.FamilyGroupResponse;

import java.util.List;

public interface FamilyGroupService {

    FamilyGroupResponse create(CreateFamilyGroupRequest request, Long ownerUserId);

    FamilyGroupResponse addMember(Long groupId, AddFamilyMemberRequest request, Long requestingUserId);

    FamilyGroupResponse deletePermanently(Long groupId, Long requestingUserId);

    List<FamilyGroupResponse> getMyGroups(Long ownerUserId);
}
