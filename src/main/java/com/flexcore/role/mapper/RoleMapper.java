package com.flexcore.role.mapper;

import com.flexcore.role.dto.response.PermissionResponse;
import com.flexcore.role.dto.response.RoleResponse;
import com.flexcore.role.entity.Permission;
import com.flexcore.role.entity.Role;
import org.mapstruct.Mapper;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    RoleResponse toResponse(Role role);

    PermissionResponse toPermissionResponse(Permission permission);

    Set<PermissionResponse> toPermissionResponses(Set<Permission> permissions);
}
