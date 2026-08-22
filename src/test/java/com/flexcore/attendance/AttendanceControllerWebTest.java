package com.flexcore.attendance;

import com.flexcore.attendance.controller.AttendanceController;
import com.flexcore.attendance.dto.response.AttendanceResponse;
import com.flexcore.attendance.service.AttendanceService;
import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.support.SecuredControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class AttendanceControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private AttendanceService attendanceService;

    @Test
    void checkIn_withPermission_returns201() throws Exception {
        when(attendanceService.checkIn(any(), eq(9L)))
                .thenReturn(AttendanceResponse.builder().userId(1L).build());

        mockMvc.perform(post("/api/v1/attendance/check-in").with(asUser(9L, "RECEPTIONIST", "CHECK_IN_MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void checkIn_withoutPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/check-in").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_staffCanQueryAnyMember() throws Exception {
        when(attendanceService.getMemberHistory(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(AttendanceResponse.builder().build())));

        mockMvc.perform(get("/api/v1/attendance").with(asUser(9L, "RECEPTIONIST", "CHECK_IN_MEMBER"))
                        .param("userId", "1"))
                .andExpect(status().isOk());

        verify(attendanceService).getMemberHistory(eq(1L), any());
    }

    @Test
    void history_memberSeesOwnHistory() throws Exception {
        when(attendanceService.getMemberHistory(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/attendance").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isOk());

        verify(attendanceService).getMemberHistory(eq(7L), any());
    }
}
