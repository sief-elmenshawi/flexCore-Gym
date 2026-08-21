package com.flexcore.gymclass.service;

import com.flexcore.gymclass.dto.request.CreateGymClassRequest;
import com.flexcore.gymclass.dto.request.UpdateGymClassRequest;
import com.flexcore.gymclass.dto.response.GymClassResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface GymClassService {

    GymClassResponse create(CreateGymClassRequest request);

    GymClassResponse update(Long id, UpdateGymClassRequest request);

    GymClassResponse getById(Long id);

    void delete(Long id);

    Page<GymClassResponse> search(String name, Long trainerId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
