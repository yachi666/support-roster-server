package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.auth.AuthenticatedAccount;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.service.auth.AuthContextService;
import com.support.server.supportrosterserver.service.auth.AuthTokenVersionService;

class WorkspaceAccountServiceTest {

    private WorkspaceAccountMapper workspaceAccountMapper;
    private WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private AuthContextService authContextService;
    private WorkspaceOperationLogService workspaceOperationLogService;
    private AuthTokenVersionService authTokenVersionService;
    private WorkspaceAccountService workspaceAccountService;

    @BeforeEach
    void setUp() {
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        workspaceAccountTeamScopeMapper = mock(WorkspaceAccountTeamScopeMapper.class);
        authContextService = mock(AuthContextService.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
        authTokenVersionService = mock(AuthTokenVersionService.class);
        workspaceAccountService = new WorkspaceAccountService(
            workspaceAccountMapper,
            workspaceAccountTeamScopeMapper,
            mock(StaffMapper.class),
            mock(WorkspaceLookupService.class),
            authContextService,
            authTokenVersionService,
            workspaceOperationLogService
        );
    }

    @Test
    void shouldDeleteAccountAndScopesWithoutDeletingStaffProfile() {
        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(8L);
        account.setStaffId(88L);
        account.setStaffCode("A088");
        when(workspaceAccountMapper.selectById(8L)).thenReturn(account);
        when(authContextService.requireLogin()).thenReturn(new AuthenticatedAccount(
            1L,
            11L,
            "ADMIN1",
            "Admin User",
            "admin",
            "ACTIVE",
            "LOCAL_PASSWORD",
            Set.of(),
            List.of()
        ));

        workspaceAccountService.deleteAccount(8L);

        verify(authTokenVersionService).bumpTokenVersion(account);
        verify(workspaceAccountTeamScopeMapper).delete(any());
        verify(workspaceAccountMapper).deleteById(8L);
        verify(workspaceOperationLogService).log("Admin User", "Delete workspace account", "workspace_account", 8L, "Staff ID=A088");
    }

    @Test
    void shouldRejectDeletingCurrentSignedInAccount() {
        when(authContextService.requireLogin()).thenReturn(new AuthenticatedAccount(
            8L,
            11L,
            "ADMIN1",
            "Admin User",
            "admin",
            "ACTIVE",
            "LOCAL_PASSWORD",
            Set.of(),
            List.of()
        ));

        assertThrows(BadRequestException.class, () -> workspaceAccountService.deleteAccount(8L));

        verify(workspaceAccountMapper, never()).deleteById(anyLong());
        verify(workspaceAccountTeamScopeMapper, never()).delete(any());
    }
}
