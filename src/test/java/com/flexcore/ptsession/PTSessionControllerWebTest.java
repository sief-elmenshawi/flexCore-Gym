package com.flexcore.ptsession;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.ptsession.controller.PTSessionController;
import com.flexcore.ptsession.dto.response.PTSessionResponse;
import com.flexcore.ptsession.service.PTSessionService;
import com.flexcore.support.SecuredControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PTSessionController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class PTSessionControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private PTSessionService ptSessionService;

    @Test
    void book_returns201AndUsesCurrentUserId() throws Exception {
        when(ptSessionService.book(any(), eq(7L)))
                .thenReturn(PTSessionResponse.builder().id(50L).build());
        String futureAt = LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        mockMvc.perform(post("/api/v1/pt-sessions").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerId\":2,\"scheduledAt\":\"" + futureAt + "\",\"durationMinutes\":60}"))
                .andExpect(status().isCreated());

        verify(ptSessionService).book(any(), eq(7L));
    }

    @Test
    void mine_asTrainerRoutesWithTrainerRole() throws Exception {
        when(ptSessionService.getMySessions(eq(2L), eq("TRAINER"), any()))
                .thenReturn(new PageImpl<>(List.of(PTSessionResponse.builder().build())));

        mockMvc.perform(get("/api/v1/pt-sessions/mine").with(asUser(2L, "TRAINER", "MANAGE_OWN_SCHEDULE")))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_returns204AndDelegatesToParticipantId() throws Exception {
        mockMvc.perform(delete("/api/v1/pt-sessions/50").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isNoContent());

        verify(ptSessionService).cancel(50L, 7L);
    }

    @Test
    void book_withoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/pt-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerId\":2,\"scheduledAt\":\"2026-09-01T18:00:00\"}"))
                .andExpect(status().isUnauthorized());
    }
}
