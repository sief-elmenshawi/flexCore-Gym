package com.flexcore.user.mapper;

import com.flexcore.user.dto.response.UserResponse;
import com.flexcore.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roleName", source = "role.name")
    UserResponse toResponse(User user);
}
