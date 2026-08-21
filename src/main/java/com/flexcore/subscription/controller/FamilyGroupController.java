package com.flexcore.subscription.controller;

import com.flexcore.core.security.SecurityUtils;
import com.flexcore.subscription.dto.request.AddFamilyMemberRequest;
import com.flexcore.subscription.dto.request.CreateFamilyGroupRequest;
import com.flexcore.subscription.dto.response.FamilyGroupResponse;
import com.flexcore.subscription.service.FamilyGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/family-groups")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Family Groups", description = "Create family groups and add members to family plans")
public class FamilyGroupController {

    private final FamilyGroupService familyGroupService;

    @PostMapping
    @Operation(summary = "Create a family group for the current user and activate the owner's subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Family group created"),
            @ApiResponse(responseCode = "400", description = "Plan is not a family plan"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<FamilyGroupResponse> create(@Valid @RequestBody CreateFamilyGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyGroupService.create(request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member to a family group (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member added with an active subscription"),
            @ApiResponse(responseCode = "400", description = "Group is full or user already in it"),
            @ApiResponse(responseCode = "403", description = "Only the owner can add members")
    })
    public ResponseEntity<FamilyGroupResponse> addMember(@PathVariable Long id,
                                                         @Valid @RequestBody AddFamilyMemberRequest request) {
        return ResponseEntity.ok(familyGroupService.addMember(id, request, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/my")
    @Operation(summary = "List family groups owned by the current user")
    public ResponseEntity<List<FamilyGroupResponse>> myGroups() {
        return ResponseEntity.ok(familyGroupService.getMyGroups(SecurityUtils.getCurrentUserId()));
    }
}
