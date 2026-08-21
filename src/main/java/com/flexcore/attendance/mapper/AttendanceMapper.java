package com.flexcore.attendance.mapper;

import com.flexcore.attendance.dto.response.AttendanceResponse;
import com.flexcore.attendance.entity.Attendance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttendanceMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "checkedInByName", source = "checkedInBy.fullName")
    AttendanceResponse toResponse(Attendance attendance);
}
