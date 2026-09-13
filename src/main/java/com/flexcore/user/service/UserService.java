package com.flexcore.user.service;

import com.flexcore.user.dto.request.CreateUserRequest;
import com.flexcore.user.dto.request.UpdateProfileRequest;
import com.flexcore.user.dto.request.UpdateUserRequest;
import com.flexcore.user.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse getById(Long id);

    Page<UserResponse> getAll(Pageable pageable);

    Page<UserResponse> getTrainers(Pageable pageable);

    Page<UserResponse> getMembers(Pageable pageable);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    UserResponse update(Long id, UpdateUserRequest request);

    void deactivate(Long id);
}
