package com.flexcore.user;

import com.flexcore.core.exception.DuplicateResourceException;
import com.flexcore.role.entity.Role;
import com.flexcore.role.repository.RoleRepository;
import com.flexcore.user.dto.request.CreateUserRequest;
import com.flexcore.user.entity.User;
import com.flexcore.user.mapper.UserMapper;
import com.flexcore.user.repository.UserRepository;
import com.flexcore.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private Role memberRole;

    @BeforeEach
    void setUp() {
        memberRole = Role.builder().id(2L).name("MEMBER").build();
    }

    private CreateUserRequest request(String email) {
        CreateUserRequest request = new CreateUserRequest();
        request.setFullName("  Ahmed Hassan  ");
        request.setEmail(email);
        request.setPassword("Str0ngP@ss");
        request.setRoleId(2L);
        return request;
    }

    @Test
    void create_whenEmailExistsWithDifferentCase_throwsDuplicate() {
        when(userRepository.existsByEmail("ahmed@test.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> userService.create(request("  Ahmed@Test.COM ")));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void create_storesTrimmedLowercaseEmail() {
        when(userRepository.existsByEmail("ahmed@test.com")).thenReturn(false);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(memberRole));
        when(passwordEncoder.encode("Str0ngP@ss")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request("  Ahmed@Test.COM "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ahmed@test.com", captor.getValue().getEmail());
        assertEquals("Ahmed Hassan", captor.getValue().getFullName());
        assertEquals("$2a$hashed", captor.getValue().getPasswordHash());
    }
}
