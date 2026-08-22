package com.flexcore.subscription;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.subscription.controller.SubscriptionController;
import com.flexcore.subscription.dto.response.SubscriptionResponse;
import com.flexcore.subscription.service.SubscriptionService;
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

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class SubscriptionControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private SubscriptionService subscriptionService;

    @Test
    void purchase_asMember_returns201() throws Exception {
        when(subscriptionService.purchase(any(), eq(7L), eq(false)))
                .thenReturn(SubscriptionResponse.builder().id(10L).build());

        mockMvc.perform(post("/api/v1/subscriptions/purchase").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2}"))
                .andExpect(status().isCreated());

        verify(subscriptionService).purchase(any(), eq(7L), eq(false));
    }

    @Test
    void freeze_returns200() throws Exception {
        when(subscriptionService.freeze(eq(10L), any(), eq(7L), eq(false)))
                .thenReturn(SubscriptionResponse.builder().id(10L).build());

        mockMvc.perform(post("/api/v1/subscriptions/10/freeze").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7}"))
                .andExpect(status().isOk());
    }

    @Test
    void unfreeze_returns200() throws Exception {
        when(subscriptionService.unfreeze(eq(10L), eq(7L), eq(false)))
                .thenReturn(SubscriptionResponse.builder().build());

        mockMvc.perform(post("/api/v1/subscriptions/10/unfreeze").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_returns200() throws Exception {
        when(subscriptionService.cancel(eq(10L), eq(7L), eq(false)))
                .thenReturn(SubscriptionResponse.builder().build());

        mockMvc.perform(delete("/api/v1/subscriptions/10").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isOk());
    }

    @Test
    void mySubscriptions_returnsList() throws Exception {
        when(subscriptionService.getMySubscriptions(7L))
                .thenReturn(List.of(SubscriptionResponse.builder().id(10L).build()));

        mockMvc.perform(get("/api/v1/subscriptions/my").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isOk());
    }

    @Test
    void expiringSoon_withViewReports_returnsPage() throws Exception {
        when(subscriptionService.expiringSoon(eq(14), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/subscriptions/expiring-soon")
                        .with(asUser(9L, "ADMIN", "VIEW_REPORTS"))
                        .param("withinDays", "14"))
                .andExpect(status().isOk());
    }
}
