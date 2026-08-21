package com.flexcore.role.controller;

import com.flexcore.role.dto.request.CreateRoleRequest;
import com.flexcore.role.dto.request.UpdateRoleRequest;
import com.flexcore.role.dto.response.PermissionResponse;
import com.flexcore.role.dto.response.RoleResponse;
import com.flexcore.role.entity.Permission;
import com.flexcore.role.repository.PermissionRepository;
import com.flexcore.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('MANAGE_STAFF')")
@Tag(name = "Roles & Permissions", description = "Role and permission management (requires MANAGE_STAFF)")
public class RoleController {

    private final RoleService roleService;
    private final PermissionRepository permissionRepository;

    @PostMapping
    @Operation(summary = "Create a role with permissions")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "409", description = "Role name already exists"),
            @ApiResponse(responseCode = "404", description = "Unknown permission code")
    })
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a role's name and permissions")
    public ResponseEntity<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(id, request));
    }

    @GetMapping
    @Operation(summary = "List all roles with their permissions")
    public ResponseEntity<List<RoleResponse>> getAll() {
        return ResponseEntity.ok(roleService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a role by id")
    @ApiResponse(responseCode = "404", description = "Role not found")
    public ResponseEntity<RoleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a role")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role deleted"),
            @ApiResponse(responseCode = "400", description = "Role is assigned to users")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    @Operation(summary = "List all available permission codes")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionRepository.findAll().stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .code(permission.getCode())
                        .description(permission.getDescription())
                        .build())
                .toList();
        return ResponseEntity.ok(permissions);
    }
}
