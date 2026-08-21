package com.flexcore.ptsession.mapper;

import com.flexcore.ptsession.dto.response.PTSessionResponse;
import com.flexcore.ptsession.entity.PTSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PTSessionMapper {

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberName", source = "member.fullName")
    @Mapping(target = "trainerId", source = "trainer.id")
    @Mapping(target = "trainerName", source = "trainer.fullName")
    PTSessionResponse toResponse(PTSession session);
}
