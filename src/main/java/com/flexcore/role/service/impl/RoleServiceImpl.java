package com.flexcore.role.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.DuplicateResourceException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.role.dto.request.CreateRoleRequest;
import com.flexcore.role.dto.request.UpdateRoleRequest;
import com.flexcore.role.dto.response.RoleResponse;
import com.flexcore.role.entity.Permission;
import com.flexcore.role.entity.Role;
import com.flexcore.role.mapper.RoleMapper;
import com.flexcore.role.repository.PermissionRepository;
import com.flexcore.role.repository.RoleRepository;
import com.flexcore.role.service.RoleService;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        String name = request.getName().trim().toUpperCase();
        if (roleRepository.existsByName(name)) {
            throw new DuplicateResourceException("Role already exists: " + name);
        }

        Role role = Role.builder()
                .name(name)
                .permissions(resolvePermissions(request.getPermissionCodes()))
                .build();

        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Override
    @Transactional
    public RoleResponse update(Long id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.role.notfound", id));

        String name = request.getName().trim().toUpperCase();
        roleRepository.findByName(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
            throw new DuplicateResourceException("error.role.exists", name);
                });

        role.setName(name);
        role.getPermissions().clear();
        role.getPermissions().addAll(resolvePermissions(request.getPermissionCodes()));

        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAll() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.role.notfound", id));
        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.role.notfound", id));

        if (userRepository.existsByRole_Id(id)) {
            throw new BusinessRuleViolationException("error.role.assigned-users");
        }

        role.getPermissions().clear();
        roleRepository.save(role);
        roleRepository.delete(role);
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> permissions = new HashSet<>(permissionRepository.findByCodeIn(codes));
        if (permissions.size() != codes.size()) {
            throw new ResourceNotFoundException("error.permission.invalid");
        }
        return permissions;
    }
}
