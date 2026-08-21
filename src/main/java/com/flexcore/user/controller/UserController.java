package com.flexcore.user.controller;

import com.flexcore.core.security.SecurityUtils;
import com.flexcore.user.dto.request.CreateUserRequest;
import com.flexcore.user.dto.request.UpdateProfileRequest;
import com.flexcore.user.dto.request.UpdateUserRequest;
import com.flexcore.user.dto.response.UserResponse;
import com.flexcore.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management and profile endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's profile")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.getById(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the current authenticated user's profile")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_STAFF')")
    @Operation(summary = "Create a user (staff or member)", description = "Requires MANAGE_STAFF permission")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "409", description = "Email already registered"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_STAFF')")
    @Operation(summary = "List users with pagination", description = "Requires MANAGE_STAFF permission")
    public ResponseEntity<Page<UserResponse>> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_STAFF')")
    @Operation(summary = "Get a user by id", description = "Requires MANAGE_STAFF permission")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_STAFF')")
    @Operation(summary = "Update a user's basic info", description = "Requires MANAGE_STAFF permission")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_STAFF')")
    @Operation(summary = "Deactivate a user account (soft delete)", description = "Requires MANAGE_STAFF permission")
    @ApiResponse(responseCode = "204", description = "User deactivated")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
