package com.flexcore.role.service;

import com.flexcore.role.dto.request.CreateRoleRequest;
import com.flexcore.role.dto.request.UpdateRoleRequest;
import com.flexcore.role.dto.response.RoleResponse;

import java.util.List;

public interface RoleService {

    RoleResponse create(CreateRoleRequest request);

    RoleResponse update(Long id, UpdateRoleRequest request);

    List<RoleResponse> getAll();

    RoleResponse getById(Long id);

    void delete(Long id);
}
