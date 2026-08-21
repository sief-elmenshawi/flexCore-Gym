package com.flexcore.ptsession.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.ptsession.dto.request.BookPTSessionRequest;
import com.flexcore.ptsession.dto.response.PTSessionResponse;
import com.flexcore.ptsession.entity.PTSession;
import com.flexcore.ptsession.enums.PTSessionStatus;
import com.flexcore.ptsession.mapper.PTSessionMapper;
import com.flexcore.ptsession.repository.PTSessionRepository;
import com.flexcore.ptsession.service.PTSessionService;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PTSessionServiceImpl implements PTSessionService {

    private final PTSessionRepository ptSessionRepository;
    private final UserRepository userRepository;
    private final PTSessionMapper ptSessionMapper;

    @Override
    @Transactional
    public PTSessionResponse book(BookPTSessionRequest request, Long memberId) {
        User member = findUser(memberId);
        User trainer = findTrainer(request.getTrainerId());

        LocalDateTime start = request.getScheduledAt();
        LocalDateTime end = start.plusMinutes(request.getDurationMinutes());

        if (ptSessionRepository.existsOverlappingForTrainer(trainer.getId(), start, end)) {
            throw new BusinessRuleViolationException(
                    "error.pt.overlap", trainer.getFullName());
        }

        PTSession session = PTSession.builder()
                .member(member)
                .trainer(trainer)
                .scheduledAt(start)
                .durationMinutes(request.getDurationMinutes())
                .status(PTSessionStatus.SCHEDULED)
                .build();

        return ptSessionMapper.toResponse(ptSessionRepository.save(session));
    }

    @Override
    @Transactional
    public void cancel(Long sessionId, Long requestingUserId) {
        PTSession session = ptSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.pt-session.notfound", sessionId));

        boolean participant = session.getMember().getId().equals(requestingUserId)
                || session.getTrainer().getId().equals(requestingUserId);
        if (!participant) {
            throw new AccessDeniedException("Only session participants can cancel it");
        }
        if (session.getStatus() != PTSessionStatus.SCHEDULED) {
            throw new BusinessRuleViolationException("error.pt.cancel-only-scheduled");
        }

        session.setStatus(PTSessionStatus.CANCELLED);
        ptSessionRepository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PTSessionResponse> getMySessions(Long userId, String roleName, Pageable pageable) {
        Page<PTSession> page = "TRAINER".equals(roleName)
                ? ptSessionRepository.findByTrainerIdOrderByScheduledAtDesc(userId, pageable)
                : ptSessionRepository.findByMemberIdOrderByScheduledAtDesc(userId, pageable);
        return page.map(ptSessionMapper::toResponse);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.notfound", id));
    }

    private User findTrainer(Long id) {
        User trainer = findUser(id);
        if (!"TRAINER".equals(trainer.getRole().getName())) {
            throw new BusinessRuleViolationException("error.user.not-trainer", id);
        }
        if (!trainer.isActive()) {
            throw new BusinessRuleViolationException("error.trainer.deactivated", id);
        }
        return trainer;
    }
}
