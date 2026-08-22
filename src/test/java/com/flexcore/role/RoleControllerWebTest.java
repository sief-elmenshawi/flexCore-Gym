package com.flexcore.role;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.role.controller.RoleController;
import com.flexcore.role.dto.response.RoleResponse;
import com.flexcore.role.entity.Permission;
import com.flexcore.role.repository.PermissionRepository;
import com.flexcore.role.service.RoleService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class RoleControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private PermissionRepository permissionRepository;

    @Test
    void create_withManageStaff_returns201() throws Exception {
        when(roleService.create(any())).thenReturn(RoleResponse.builder().id(5L).name("COACH").build());

        mockMvc.perform(post("/api/v1/roles").with(asUser(9L, "ADMIN", "MANAGE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"coach\",\"permissionCodes\":[\"classes.manage\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getAll_withoutManageStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/roles").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_withManageStaff_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/5").with(asUser(9L, "ADMIN", "MANAGE_STAFF")))
                .andExpect(status().isNoContent());

        verify(roleService).delete(5L);
    }

    @Test
    void permissions_listsAllAvailableCodes() throws Exception {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().id(1L).code("BOOK_CLASS").description("book").build()));

        mockMvc.perform(get("/api/v1/roles/permissions").with(asUser(9L, "ADMIN", "MANAGE_STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void getById_unknownRoleStillDelegatesToService() throws Exception {
        when(roleService.getById(eq(99L))).thenReturn(RoleResponse.builder().build());

        mockMvc.perform(get("/api/v1/roles/99").with(asUser(9L, "ADMIN", "MANAGE_STAFF")))
                .andExpect(status().isOk());
    }
}
