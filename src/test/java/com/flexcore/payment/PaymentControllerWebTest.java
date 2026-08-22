package com.flexcore.payment;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.payment.controller.PaymentController;
import com.flexcore.payment.dto.response.PaymentResponse;
import com.flexcore.payment.service.PaymentService;
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

@WebMvcTest(PaymentController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class PaymentControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void initiate_asMemberWithoutRenewPermission_passesPrivilegedFalse() throws Exception {
        when(paymentService.initiate(any(), eq(7L), eq(false)))
                .thenReturn(PaymentResponse.builder().build());

        mockMvc.perform(post("/api/v1/payments/initiate").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscriptionId\":5,\"method\":\"MOCK_FAWRY\"}"))
                .andExpect(status().isCreated());

        verify(paymentService).initiate(any(), eq(7L), eq(false));
    }

    @Test
    void initiate_asStaffWithRenewPermission_passesPrivilegedTrue() throws Exception {
        when(paymentService.initiate(any(), eq(9L), eq(true)))
                .thenReturn(PaymentResponse.builder().build());

        mockMvc.perform(post("/api/v1/payments/initiate").with(asUser(9L, "ADMIN", "RENEW_SUBSCRIPTION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscriptionId\":5,\"method\":\"MOCK_INSTAPAY\"}"))
                .andExpect(status().isCreated());

        verify(paymentService).initiate(any(), eq(9L), eq(true));
    }

    @Test
    void my_returnsPagedPayments() throws Exception {
        when(paymentService.getMyPayments(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(PaymentResponse.builder().build())));

        mockMvc.perform(get("/api/v1/payments/my").with(asUser(7L, "MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    void my_withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/my"))
                .andExpect(status().isUnauthorized());
    }
}
