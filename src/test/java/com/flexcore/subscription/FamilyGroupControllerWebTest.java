package com.flexcore.subscription;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.subscription.controller.FamilyGroupController;
import com.flexcore.subscription.dto.response.FamilyGroupResponse;
import com.flexcore.subscription.service.FamilyGroupService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FamilyGroupController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class FamilyGroupControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private FamilyGroupService familyGroupService;

    @Test
    void create_usesCurrentUserIdAsOwner() throws Exception {
        when(familyGroupService.create(any(), eq(7L)))
                .thenReturn(FamilyGroupResponse.builder().build());

        mockMvc.perform(post("/api/v1/family-groups").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":4}"))
                .andExpect(status().isCreated());

        verify(familyGroupService).create(any(), eq(7L));
    }

    @Test
    void addMember_delegatesToOwnerId() throws Exception {
        when(familyGroupService.addMember(eq(6L), any(), eq(7L)))
                .thenReturn(FamilyGroupResponse.builder().build());

        mockMvc.perform(post("/api/v1/family-groups/6/members").with(asUser(7L, "MEMBER", "BOOK_CLASS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":12}"))
                .andExpect(status().isOk());
    }

    @Test
    void myGroups_returnsList() throws Exception {
        when(familyGroupService.getMyGroups(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/family-groups/my").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isOk());
    }

    @Test
    void create_withoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/family-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":4}"))
                .andExpect(status().isUnauthorized());
    }
}
