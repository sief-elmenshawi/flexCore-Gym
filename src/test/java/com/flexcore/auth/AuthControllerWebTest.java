package com.flexcore.auth;

import com.flexcore.auth.controller.AuthController;
import com.flexcore.auth.dto.response.AuthResponse;
import com.flexcore.auth.service.AuthService;
import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.support.SecuredControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class AuthControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private AuthService authService;

    @Test
    void register_returns201WithToken() throws Exception {
        when(authService.register(any())).thenReturn(AuthResponse.builder()
                .accessToken("jwt").tokenType("Bearer").userId(7L)
                .email("new@test.com").roleName("MEMBER").build());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New User","email":"new@test.com",
                                 "password":"Str0ngP@ss","phoneNumber":"0100000000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.roleName").value("MEMBER"));
    }

    @Test
    void login_returns200() throws Exception {
        when(authService.login(any())).thenReturn(AuthResponse.builder()
                .accessToken("jwt").tokenType("Bearer").userId(7L).roleName("MEMBER").build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ahmed@test.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"));
    }

    @Test
    void register_malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }
}
