package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccessPolicyUpdateRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspacePageAccessPolicyDto;
import com.support.server.supportrosterserver.entity.workspace.WorkspaceAccessPolicyEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.WorkspaceAccessPolicyMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

class WorkspaceAccessPolicyServiceTest {

    private WorkspaceAccessPolicyMapper workspaceAccessPolicyMapper;
    private AuthContextService authContextService;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private WorkspaceAccessPolicyService workspaceAccessPolicyService;

    @BeforeEach
    void setUp() {
        workspaceAccessPolicyMapper = mock(WorkspaceAccessPolicyMapper.class);
        authContextService = mock(AuthContextService.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        workspaceAccessPolicyService = new WorkspaceAccessPolicyService(
            workspaceAccessPolicyMapper,
            authContextService,
            workspaceOperationLogService
        );
        when(workspaceAccessPolicyMapper.selectList(any())).thenReturn(List.of());
        when(authContextService.requireLogin()).thenReturn(new AuthenticatedAccount(1L, 1L, "A001", "Admin", "ADMIN", "ACTIVE", "LOCAL", java.util.Set.of(), List.of()));
    }

    @Test
    void shouldAcceptRoundTripPolicyPayloadWithNonConfigurablePages() {
        WorkspaceAccessPolicyUpdateRequest request = new WorkspaceAccessPolicyUpdateRequest(List.of(
            new WorkspacePageAccessPolicyDto("overview", false, true),
            new WorkspacePageAccessPolicyDto("roster", false, true),
            new WorkspacePageAccessPolicyDto("staff", false, true),
            new WorkspacePageAccessPolicyDto("shifts", false, true),
            new WorkspacePageAccessPolicyDto("validation", false, true),
            new WorkspacePageAccessPolicyDto("import-export", false, true),
            new WorkspacePageAccessPolicyDto("teams", false, true),
            new WorkspacePageAccessPolicyDto("linux-passwords", true, true),
            new WorkspacePageAccessPolicyDto("accounts", true, false)
        ));

        var response = workspaceAccessPolicyService.updateAccessPolicy(request);

        assertEquals(9, response.getPages().size());
        verify(workspaceAccessPolicyMapper, never()).insert(org.mockito.ArgumentMatchers.<WorkspaceAccessPolicyEntity>argThat(entity -> "accounts".equals(entity.getPageCode())));
    }

    @Test
    void shouldExposeLinuxPasswordsPolicyInDefaultResponse() {
        var response = workspaceAccessPolicyService.getAccessPolicy();

        assertEquals(9, response.getPages().size());
        var linuxPasswordsPolicy = response.getPages().stream()
            .filter(policy -> "linux-passwords".equals(policy.getPageCode()))
            .findFirst()
            .orElseThrow();
        assertEquals(true, linuxPasswordsPolicy.getAuthRequired());
        assertEquals(true, linuxPasswordsPolicy.getConfigurable());
    }

    @Test
    void shouldRejectUnsupportedPageCode() {
        WorkspaceAccessPolicyUpdateRequest request = new WorkspaceAccessPolicyUpdateRequest(List.of(
            new WorkspacePageAccessPolicyDto("overview", false, true),
            new WorkspacePageAccessPolicyDto("roster", false, true),
            new WorkspacePageAccessPolicyDto("staff", false, true),
            new WorkspacePageAccessPolicyDto("shifts", false, true),
            new WorkspacePageAccessPolicyDto("validation", false, true),
            new WorkspacePageAccessPolicyDto("import-export", false, true),
            new WorkspacePageAccessPolicyDto("teams", false, true),
            new WorkspacePageAccessPolicyDto("unknown", false, true)
        ));

        BadRequestException error = assertThrows(BadRequestException.class, () -> workspaceAccessPolicyService.updateAccessPolicy(request));

        assertEquals("Unsupported workspace page code: unknown", error.getMessage());
    }
}
