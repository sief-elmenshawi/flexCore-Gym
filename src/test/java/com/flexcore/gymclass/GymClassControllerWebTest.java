package com.flexcore.gymclass;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.gymclass.controller.GymClassController;
import com.flexcore.gymclass.dto.response.GymClassResponse;
import com.flexcore.gymclass.service.GymClassService;
import com.flexcore.support.SecuredControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GymClassController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class GymClassControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private GymClassService gymClassService;

    @Test
    void upcoming_isPublicAndSearches() throws Exception {
        when(gymClassService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(GymClassResponse.builder().build())));

        mockMvc.perform(get("/api/v1/classes/upcoming")
                        .param("name", "yoga")
                        .param("trainerId", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void create_withManageOwnSchedule_returns201() throws Exception {
        when(gymClassService.create(any())).thenReturn(GymClassResponse.builder().id(30L).build());

        mockMvc.perform(post("/api/v1/classes").with(asUser(2L, "TRAINER", "MANAGE_OWN_SCHEDULE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Morning Yoga","trainerId":2,"capacity":15,
                                 "startsAt":"2026-09-01T10:00:00","durationMinutes":60}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void getById_returnsClassDetails() throws Exception {
        when(gymClassService.getById(30L)).thenReturn(GymClassResponse.builder()
                .id(30L).name("HIIT").build());

        mockMvc.perform(get("/api/v1/classes/30").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("HIIT"));
    }

    @Test
    void delete_withManageOwnSchedule_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/classes/30").with(asUser(2L, "TRAINER", "MANAGE_OWN_SCHEDULE")))
                .andExpect(status().isNoContent());

        verify(gymClassService).delete(30L);
    }
}
