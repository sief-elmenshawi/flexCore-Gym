package com.flexcore.ptsession;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.ptsession.dto.request.BookPTSessionRequest;
import com.flexcore.ptsession.dto.response.PTSessionResponse;
import com.flexcore.ptsession.entity.PTSession;
import com.flexcore.ptsession.enums.PTSessionStatus;
import com.flexcore.ptsession.mapper.PTSessionMapper;
import com.flexcore.ptsession.repository.PTSessionRepository;
import com.flexcore.ptsession.service.impl.PTSessionServiceImpl;
import com.flexcore.role.entity.Role;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PTSessionServiceTest {

    @Mock private PTSessionRepository ptSessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PTSessionMapper ptSessionMapper;

    @InjectMocks
    private PTSessionServiceImpl ptSessionService;

    private User member;
    private User trainer;

    @BeforeEach
    void setUp() {
        member = User.builder().id(1L).fullName("Member One").active(true)
                .role(Role.builder().name("MEMBER").build()).build();
        trainer = User.builder().id(2L).fullName("Coach T").active(true)
                .role(Role.builder().name("TRAINER").build()).build();
    }

    private BookPTSessionRequest request(LocalDateTime at) {
        BookPTSessionRequest request = new BookPTSessionRequest();
        request.setTrainerId(2L);
        request.setScheduledAt(at);
        request.setDurationMinutes(60);
        return request;
    }

    @Test
    void book_savesScheduledSession() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(userRepository.findById(2L)).thenReturn(Optional.of(trainer));
        when(ptSessionRepository.existsOverlappingForTrainer(eq(2L), any(), any())).thenReturn(false);
        when(ptSessionRepository.save(any(PTSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ptSessionMapper.toResponse(any(PTSession.class)))
                .thenReturn(PTSessionResponse.builder().status(PTSessionStatus.SCHEDULED).build());

        PTSessionResponse response = ptSessionService.book(request(LocalDateTime.now().plusDays(1)), 1L);

        assertEquals(PTSessionStatus.SCHEDULED, response.getStatus());
        ArgumentCaptor<PTSession> captor = ArgumentCaptor.forClass(PTSession.class);
        verify(ptSessionRepository).save(captor.capture());
        assertEquals(2L, captor.getValue().getTrainer().getId());
        assertEquals(60, captor.getValue().getDurationMinutes());
    }

    @Test
    void book_overlappingTrainerSlotThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        when(userRepository.findById(2L)).thenReturn(Optional.of(trainer));
        when(ptSessionRepository.existsOverlappingForTrainer(eq(2L), any(), any())).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class,
                () -> ptSessionService.book(request(LocalDateTime.now().plusDays(1)), 1L));

        verify(ptSessionRepository, never()).save(any(PTSession.class));
    }

    @Test
    void book_targetUserIsNotTrainerThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        User notTrainer = User.builder().id(3L).active(true)
                .role(Role.builder().name("MEMBER").build()).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(notTrainer));

        BookPTSessionRequest request = request(LocalDateTime.now().plusDays(1));
        request.setTrainerId(3L);

        assertThrows(BusinessRuleViolationException.class,
                () -> ptSessionService.book(request, 1L));
    }

    @Test
    void book_deactivatedTrainerThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(member));
        trainer.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(trainer));

        assertThrows(BusinessRuleViolationException.class,
                () -> ptSessionService.book(request(LocalDateTime.now().plusDays(1)), 1L));
    }

    @Test
    void cancel_byMemberMarksCancelled() {
        PTSession session = PTSession.builder()
                .id(10L).member(member).trainer(trainer)
                .scheduledAt(LocalDateTime.now().plusHours(2))
                .durationMinutes(60)
                .status(PTSessionStatus.SCHEDULED)
                .build();
        when(ptSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        ptSessionService.cancel(10L, 1L);

        assertEquals(PTSessionStatus.CANCELLED, session.getStatus());
        verify(ptSessionRepository).save(session);
    }

    @Test
    void cancel_byTrainerAllowed() {
        PTSession session = PTSession.builder()
                .id(10L).member(member).trainer(trainer)
                .status(PTSessionStatus.SCHEDULED).build();
        when(ptSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        ptSessionService.cancel(10L, 2L);

        assertEquals(PTSessionStatus.CANCELLED, session.getStatus());
    }

    @Test
    void cancel_byOutsiderDenied() {
        PTSession session = PTSession.builder()
                .id(10L).member(member).trainer(trainer)
                .status(PTSessionStatus.SCHEDULED).build();
        when(ptSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThrows(AccessDeniedException.class, () -> ptSessionService.cancel(10L, 42L));

        assertEquals(PTSessionStatus.SCHEDULED, session.getStatus());
    }

    @Test
    void cancel_alreadyCancelledThrows() {
        PTSession session = PTSession.builder()
                .id(10L).member(member).trainer(trainer)
                .status(PTSessionStatus.CANCELLED).build();
        when(ptSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThrows(BusinessRuleViolationException.class, () -> ptSessionService.cancel(10L, 1L));
    }

    @Test
    void getMySessions_routesByRole() {
        Pageable pageable = PageRequest.of(0, 10);
        PTSession session = PTSession.builder().id(10L).member(member).trainer(trainer).build();
        when(ptSessionRepository.findByTrainerIdOrderByScheduledAtDesc(2L, pageable))
                .thenReturn(new PageImpl<>(List.of(session)));
        when(ptSessionMapper.toResponse(session)).thenReturn(PTSessionResponse.builder().id(10L).build());
        when(ptSessionRepository.findByMemberIdOrderByScheduledAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<PTSessionResponse> trainerPage = ptSessionService.getMySessions(2L, "TRAINER", pageable);
        Page<PTSessionResponse> memberPage = ptSessionService.getMySessions(1L, "MEMBER", pageable);

        assertEquals(1, trainerPage.getTotalElements());
        assertEquals(0, memberPage.getTotalElements());
    }
}
