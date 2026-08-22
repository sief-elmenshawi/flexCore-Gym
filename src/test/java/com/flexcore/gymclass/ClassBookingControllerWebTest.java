package com.flexcore.gymclass;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.gymclass.controller.ClassBookingController;
import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.service.ClassBookingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassBookingController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class ClassBookingControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private ClassBookingService classBookingService;

    @Test
    void book_withBookClassPermission_returns201() throws Exception {
        when(classBookingService.book(eq(3L), eq(7L)))
                .thenReturn(ClassBookingResponse.builder().build());

        mockMvc.perform(post("/api/v1/bookings").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3}"))
                .andExpect(status().isCreated());

        verify(classBookingService).book(eq(3L), eq(7L));
    }

    @Test
    void book_withoutBookClassPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/bookings").with(asUser(7L, "TRAINER", "MANAGE_OWN_SCHEDULE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void myBookings_returnsPage() throws Exception {
        when(classBookingService.getMyBookings(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(ClassBookingResponse.builder().build())));

        mockMvc.perform(get("/api/v1/bookings/my").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .param("page", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_returns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/bookings/44").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isNoContent());

        verify(classBookingService).cancelBooking(44L, 7L);
    }
}
