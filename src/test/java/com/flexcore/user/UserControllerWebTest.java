package com.flexcore.user;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.support.SecuredControllerSliceTest;
import com.flexcore.user.controller.UserController;
import com.flexcore.user.dto.response.UserResponse;
import com.flexcore.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class UserControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private UserService userService;

    @Test
    void me_returnsCurrentUserProfile() throws Exception {
        when(userService.getById(7L)).thenReturn(UserResponse.builder()
                .id(7L).fullName("Ahmed").email("ahmed@test.com").build());

        mockMvc.perform(get("/api/v1/users/me").with(asUser(7L, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("ahmed@test.com"));
    }

    @Test
    void updateMe_passesCurrentUserId() throws Exception {
        when(userService.updateProfile(eq(7L), any()))
                .thenReturn(UserResponse.builder().id(7L).build());

        mockMvc.perform(put("/api/v1/users/me").with(asUser(7L, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ahmed Updated\",\"phoneNumber\":\"0111111111\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void create_withManageStaff_returns201() throws Exception {
        when(userService.create(any())).thenReturn(UserResponse.builder().id(20L).build());

        mockMvc.perform(post("/api/v1/users").with(asUser(9L, "ADMIN", "MANAGE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Reception","email":"reception@flex.com",
                                 "password":"Str0ngP@ss","roleId":3}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void getAll_withoutManageStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivate_withManageStaff_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/12").with(asUser(9L, "ADMIN", "MANAGE_STAFF")))
                .andExpect(status().isNoContent());

        verify(userService).deactivate(12L);
    }
}
