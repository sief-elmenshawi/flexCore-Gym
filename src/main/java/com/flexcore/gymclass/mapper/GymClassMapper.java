package com.flexcore.gymclass.mapper;

import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.dto.response.GymClassResponse;
import com.flexcore.gymclass.entity.ClassBooking;
import com.flexcore.gymclass.entity.GymClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GymClassMapper {

    @Mapping(target = "trainerId", source = "trainer.id")
    @Mapping(target = "trainerName", source = "trainer.fullName")
    @Mapping(target = "availableSpots", expression = "java(gymClass.getCapacity() - gymClass.getBookedCount())")
    GymClassResponse toResponse(GymClass gymClass);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "classId", source = "gymClass.id")
    @Mapping(target = "className", source = "gymClass.name")
    @Mapping(target = "startsAt", source = "gymClass.startsAt")
    @Mapping(target = "trainerName", source = "gymClass.trainer.fullName")
    ClassBookingResponse toBookingResponse(ClassBooking booking);
}
