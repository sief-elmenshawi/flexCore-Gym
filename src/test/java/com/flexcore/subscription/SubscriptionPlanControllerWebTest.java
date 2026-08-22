package com.flexcore.subscription;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.subscription.controller.SubscriptionPlanController;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;
import com.flexcore.subscription.service.SubscriptionPlanService;
import com.flexcore.support.SecuredControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionPlanController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class SubscriptionPlanControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private SubscriptionPlanService planService;

    @Test
    void getAll_isPublic() throws Exception {
        when(planService.getAll()).thenReturn(List.of(SubscriptionPlanResponse.builder().build()));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk());
    }

    @Test
    void create_withManageSubscriptions_returns201() throws Exception {
        when(planService.create(any())).thenReturn(SubscriptionPlanResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/plans").with(asUser(9L, "ADMIN", "MANAGE_SUBSCRIPTIONS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Monthly","price":500.00,"durationInDays":30,"maxFamilyMembers":4}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void create_withoutPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/plans").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"price\":10,\"durationInDays\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_delegatesWithId() throws Exception {
        when(planService.update(eq(2L), any())).thenReturn(SubscriptionPlanResponse.builder().id(2L).build());

        mockMvc.perform(put("/api/v1/plans/2").with(asUser(9L, "ADMIN", "MANAGE_SUBSCRIPTIONS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yearly\",\"price\":5000,\"durationInDays\":365}"))
                .andExpect(status().isOk());

        verify(planService).update(eq(2L), any());
    }
}
