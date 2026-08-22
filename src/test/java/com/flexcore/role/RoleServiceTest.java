package com.flexcore.role;

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
import com.flexcore.role.service.impl.RoleServiceImpl;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleMapper roleMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    private CreateRoleRequest createRequest(String name, Set<String> codes) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName(name);
        request.setPermissionCodes(codes);
        return request;
    }

    @Test
    void create_normalizesNameAndResolvesPermissions() {
        when(roleRepository.existsByName("COACH")).thenReturn(false);
        Permission p = Permission.builder().id(1L).code("classes.manage").build();
        when(permissionRepository.findByCodeIn(Set.of("classes.manage"))).thenReturn(List.of(p));
        Role saved = Role.builder().id(5L).name("COACH").build();
        when(roleRepository.save(any(Role.class))).thenReturn(saved);
        when(roleMapper.toResponse(saved)).thenReturn(RoleResponse.builder().id(5L).name("COACH").build());

        RoleResponse response = roleService.create(createRequest("  coach  ", Set.of("classes.manage")));

        assertEquals("COACH", response.getName());
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertEquals("COACH", captor.getValue().getName());
        assertEquals(1, captor.getValue().getPermissions().size());
    }

    @Test
    void create_duplicateThrows() {
        when(roleRepository.existsByName("ADMIN")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> roleService.create(createRequest("admin", Set.of())));

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void create_unknownPermissionCodeThrows() {
        when(roleRepository.existsByName("X")).thenReturn(false);
        when(permissionRepository.findByCodeIn(Set.of("nope"))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> roleService.create(createRequest("x", Set.of("nope"))));
    }

    @Test
    void update_renamesRoleAndReplacesPermissions() {
        Role existing = Role.builder().id(3L).name("OLD").permissions(new java.util.HashSet<>()).build();
        existing.getPermissions().add(Permission.builder().code("old.perm").build());
        when(roleRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("NEW")).thenReturn(Optional.empty());
        Permission fresh = Permission.builder().id(9L).code("new.perm").build();
        when(permissionRepository.findByCodeIn(Set.of("new.perm"))).thenReturn(List.of(fresh));
        when(roleRepository.save(existing)).thenReturn(existing);
        when(roleMapper.toResponse(existing)).thenReturn(RoleResponse.builder().id(3L).name("NEW").build());

        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setName(" new ");
        request.setPermissionCodes(Set.of("new.perm"));
        RoleResponse response = roleService.update(3L, request);

        assertEquals("NEW", response.getName());
        assertEquals("NEW", existing.getName());
        assertEquals(1, existing.getPermissions().size());
        assertEquals("new.perm", existing.getPermissions().iterator().next().getCode());
    }

    @Test
    void update_nameTakenByOtherRoleThrows() {
        when(roleRepository.findById(3L)).thenReturn(Optional.of(Role.builder().id(3L).name("OLD").build()));
        when(roleRepository.findByName("OTHER")).thenReturn(Optional.of(Role.builder().id(8L).name("OTHER").build()));

        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setName("other");
        request.setPermissionCodes(Set.of());

        assertThrows(DuplicateResourceException.class, () -> roleService.update(3L, request));
    }

    @Test
    void update_missingThrows() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> roleService.update(99L, new UpdateRoleRequest()));
    }

    @Test
    void delete_clearsPermissionsThenDeletes() {
        Role role = Role.builder().id(4L).name("TEMP").permissions(new java.util.HashSet<>()).build();
        role.getPermissions().add(Permission.builder().code("p").build());
        when(roleRepository.findById(4L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole_Id(4L)).thenReturn(false);

        roleService.delete(4L);

        assertEquals(0, role.getPermissions().size());
        verify(roleRepository).delete(role);
    }

    @Test
    void delete_roleAssignedToUsersThrows() {
        when(roleRepository.findById(4L)).thenReturn(Optional.of(Role.builder().id(4L).name("MEMBER").build()));
        when(userRepository.existsByRole_Id(4L)).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> roleService.delete(4L));

        verify(roleRepository, never()).delete(any(Role.class));
    }
}
