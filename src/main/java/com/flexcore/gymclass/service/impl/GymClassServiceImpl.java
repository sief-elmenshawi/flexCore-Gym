package com.flexcore.gymclass.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.gymclass.dto.request.CreateGymClassRequest;
import com.flexcore.gymclass.dto.request.UpdateGymClassRequest;
import com.flexcore.gymclass.dto.response.GymClassResponse;
import com.flexcore.gymclass.entity.GymClass;
import com.flexcore.gymclass.mapper.GymClassMapper;
import com.flexcore.gymclass.repository.GymClassRepository;
import com.flexcore.gymclass.service.GymClassService;
import com.flexcore.gymclass.specification.GymClassSpecifications;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GymClassServiceImpl implements GymClassService {

    private final GymClassRepository gymClassRepository;
    private final UserRepository userRepository;
    private final GymClassMapper gymClassMapper;

    @Override
    @Transactional
    public GymClassResponse create(CreateGymClassRequest request) {
        User trainer = findTrainer(request.getTrainerId());

        GymClass gymClass = GymClass.builder()
                .name(request.getName().trim())
                .trainer(trainer)
                .capacity(request.getCapacity())
                .bookedCount(0)
                .startsAt(request.getStartsAt())
                .durationMinutes(request.getDurationMinutes())
                .build();

        return gymClassMapper.toResponse(gymClassRepository.save(gymClass));
    }

    @Override
    @Transactional
    public GymClassResponse update(Long id, UpdateGymClassRequest request) {
        GymClass gymClass = findGymClass(id);

        if (request.getCapacity() < gymClass.getBookedCount()) {
            throw new BusinessRuleViolationException(
                    "error.gym-class.capacity-below-booked", gymClass.getBookedCount());
        }

        gymClass.setName(request.getName().trim());
        gymClass.setTrainer(findTrainer(request.getTrainerId()));
        gymClass.setCapacity(request.getCapacity());
        gymClass.setStartsAt(request.getStartsAt());
        gymClass.setDurationMinutes(request.getDurationMinutes());

        return gymClassMapper.toResponse(gymClassRepository.save(gymClass));
    }

    @Override
    @Transactional(readOnly = true)
    public GymClassResponse getById(Long id) {
        return gymClassMapper.toResponse(findGymClass(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        GymClass gymClass = findGymClass(id);
        if (gymClass.getBookedCount() > 0) {
            throw new BusinessRuleViolationException("error.gym-class.delete-has-bookings");
        }
        gymClassRepository.delete(gymClass);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GymClassResponse> search(String name, Long trainerId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<GymClass> spec = Specification.where(GymClassSpecifications.nameContains(name))
                .and(GymClassSpecifications.hasTrainer(trainerId))
                .and(GymClassSpecifications.startsAtFrom(from))
                .and(GymClassSpecifications.startsAtTo(to));

        return gymClassRepository.findAll(spec, pageable).map(gymClassMapper::toResponse);
    }

    private GymClass findGymClass(Long id) {
        return gymClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.gym-class.notfound", id));
    }

    private User findTrainer(Long trainerId) {
        User trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("error.trainer.notfound", trainerId));
        if (!"TRAINER".equals(trainer.getRole().getName())) {
            throw new BusinessRuleViolationException("error.user.not-trainer", trainerId);
        }
        if (!trainer.isActive()) {
            throw new BusinessRuleViolationException("error.trainer.deactivated", trainerId);
        }
        return trainer;
    }
}
