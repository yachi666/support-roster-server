package com.support.server.supportrosterserver.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.auth.AccountRole;
import com.support.server.supportrosterserver.auth.AccountStatus;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountMapper;
import com.support.server.supportrosterserver.mapper.WorkspaceAccountTeamScopeMapper;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOperationLogService;

class WorkspaceAdminBootstrapServiceTest {

    private StaffMapper staffMapper;
    private WorkspaceAccountMapper workspaceAccountMapper;
    private WorkspaceAccountTeamScopeMapper workspaceAccountTeamScopeMapper;
    private WorkspaceOperationLogService workspaceOperationLogService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        workspaceAccountMapper = mock(WorkspaceAccountMapper.class);
        workspaceAccountTeamScopeMapper = mock(WorkspaceAccountTeamScopeMapper.class);
        workspaceOperationLogService = mock(WorkspaceOperationLogService.class);
    }

    @Test
    void shouldCreatePendingAdminWhenBootstrapStaffHasNoAccount() throws Exception {
        StaffEntity staff = new StaffEntity();
        staff.setId(11L);
        staff.setStaffCode("A1001");
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(workspaceAccountMapper.selectOne(any())).thenReturn(null);

        WorkspaceAdminBootstrapService service = createService("A1001");

        service.run(null);

        verify(workspaceAccountMapper).insert(any(WorkspaceAccountEntity.class));
        verify(workspaceOperationLogService).log("system", "Bootstrap workspace admin", "workspace_account", null,
            "Created bootstrap admin for staff_code=A1001");
    }

    @Test
    void shouldPromoteExistingAccountToAdmin() throws Exception {
        StaffEntity staff = new StaffEntity();
        staff.setId(12L);
        staff.setStaffCode("A1002");

        WorkspaceAccountEntity account = new WorkspaceAccountEntity();
        account.setId(22L);
        account.setStaffId(12L);
        account.setStaffCode("A1002");
        account.setRoleCode("readonly");
        account.setAccountStatus(AccountStatus.DISABLED.getCode());
        account.setPasswordHash("hashed-password");

        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(workspaceAccountMapper.selectOne(any())).thenReturn(account);

        WorkspaceAdminBootstrapService service = createService("A1002");

        service.run(null);

        assertEquals(AccountRole.ADMIN.getCode(), account.getRoleCode());
        assertEquals(AccountStatus.ACTIVE.getCode(), account.getAccountStatus());
        assertEquals("LOCAL_PASSWORD", account.getAuthSource());
        verify(workspaceAccountMapper).updateById(account);
        verify(workspaceAccountTeamScopeMapper).delete(any());
    }

    @Test
    void shouldFailFastWhenBootstrapStaffDoesNotExist() {
        when(staffMapper.selectOne(any())).thenReturn(null);

        WorkspaceAdminBootstrapService service = createService("MISSING");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.run(null));
        assertEquals(
            "Configured support.auth.bootstrap-admin-staff-code does not match any workspace staff record: MISSING",
            error.getMessage()
        );
        verify(workspaceAccountMapper, never()).insert(any(WorkspaceAccountEntity.class));
    }

    private WorkspaceAdminBootstrapService createService(String bootstrapStaffCode) {
        return new WorkspaceAdminBootstrapService(
            bootstrapStaffCode,
            staffMapper,
            workspaceAccountMapper,
            workspaceAccountTeamScopeMapper,
            workspaceOperationLogService
        );
    }
}
